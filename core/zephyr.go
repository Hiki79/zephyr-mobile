// Package zephyrcore is the entire native surface of Zephyr for Android.
//
// It exists only to hand mihomo three things Android will not let it take for
// itself: the home directory, the TUN file descriptor that VpnService owns, and
// a way to keep the core's own sockets out of the tunnel it is creating.
// Everything else — proxies, rules, traffic, logs — the app reads back over
// mihomo's REST API, exactly as the Windows build does. There is deliberately
// no other exported function here: the smaller this file, the less there is to
// trust that is not upstream mihomo.
//
// Built with `gomobile bind` into app/libs/zephyrcore.aar.
package zephyrcore

import (
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strings"
	"sync"
	"syscall"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	lc "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
)

// Protector is implemented in Kotlin by the VpnService. Every socket the core
// opens is handed here first; VpnService.protect keeps it on the real network
// instead of routing it back into our own TUN, which would deadlock the moment
// the core tried to reach a proxy server.
type Protector interface {
	Protect(fd int32) bool
}

var (
	mu      sync.Mutex
	started bool
)

// Start parses configYAML, attaches mihomo's TUN to the descriptor VpnService
// already opened, and brings the core up. gateway is the TUN address in CIDR
// form ("172.19.0.1/30", optionally plus a comma-separated IPv6 prefix) and
// dnsHijack is the address inside the tunnel that DNS is redirected to.
//
// Ownership of tunFd passes to this package the moment Start is called with a
// positive value, whatever the outcome: on an early failure it is closed here,
// once the TUN listener has taken it sing-tun closes it on Stop. The Kotlin
// side must therefore detach the descriptor and never close it itself; two
// owners closing the same number is how another thread's freshly opened file
// gets shut underneath it.
//
// The REST controller and everything else come from configYAML, so this
// signature does not grow as the app gains features.
func Start(home string, configYAML string, tunFd int32, gateway string, dnsHijack string, protector Protector) error {
	mu.Lock()
	defer mu.Unlock()

	if tunFd <= 0 {
		return fmt.Errorf("invalid tun file descriptor %d", tunFd)
	}
	if started {
		closeFd(tunFd)
		return errors.New("core is already running")
	}
	if home == "" {
		closeFd(tunFd)
		return errors.New("missing home directory")
	}
	if protector == nil {
		closeFd(tunFd)
		return errors.New("missing socket protector")
	}

	// Must precede config.Parse: geoip/geosite paths resolve through this.
	constant.SetHomeDir(home)

	cfg, err := config.Parse([]byte(configYAML))
	if err != nil {
		closeFd(tunFd)
		return fmt.Errorf("parse config: %w", err)
	}
	if err := applyTun(cfg, int(tunFd), gateway, dnsHijack); err != nil {
		closeFd(tunFd)
		return err
	}

	installSocketHook(protector)

	hub.ApplyConfig(cfg)

	// ReCreateTun reports failure by logging and clearing Enable rather than
	// returning an error, so a core that never got its TUN would otherwise look
	// perfectly healthy while carrying no traffic at all. sing-tun has had the
	// descriptor since ApplyConfig and closes it during Shutdown; closing it
	// again here would risk hitting a number the runtime has since reused, so
	// the rare leak on this path is the safer failure.
	if !listener.GetTunConf().Enable {
		executor.Shutdown()
		dialer.DefaultSocketHook = nil
		return errors.New("tun listener failed to start; check the core log")
	}

	started = true
	return nil
}

// Stop unwinds the listeners (which closes the TUN descriptor) and the fake-ip
// pool. Safe to call when not running.
func Stop() {
	mu.Lock()
	defer mu.Unlock()

	if !started {
		return
	}
	executor.Shutdown()
	dialer.DefaultSocketHook = nil
	started = false
}

// Running reports whether Start has succeeded and Stop has not yet run.
func Running() bool {
	mu.Lock()
	defer mu.Unlock()
	return started
}

// closeFd releases a descriptor the TUN listener never got to own.
func closeFd(fd int32) {
	_ = syscall.Close(int(fd))
}

// applyTun overwrites whatever the subscription said about tun. Routing and the
// portal address belong to VpnService.Builder on the Kotlin side; the core only
// needs the addresses it should answer on and where to catch DNS.
func applyTun(cfg *config.Config, fd int, gateway string, dnsHijack string) error {
	prefix4, prefix6, err := parsePrefixes(gateway)
	if err != nil {
		return fmt.Errorf("tun gateway %q: %w", gateway, err)
	}
	if len(prefix4) == 0 && len(prefix6) == 0 {
		return fmt.Errorf("tun gateway %q yielded no addresses", gateway)
	}

	cfg.General.Tun = lc.Tun{
		Enable:    true,
		Device:    sing_tun.InterfaceName,
		Stack:     constant.TunGvisor,
		DNSHijack: hijackTargets(dnsHijack),
		// VpnService.Builder installs the routes; letting the core also try
		// would need privileges this process does not have.
		AutoRoute: false,
		// A non-nil socket hook already pins the core's egress; asking it to
		// also detect an interface makes it bind to one and bypass protect.
		AutoDetectInterface: false,
		Inet4Address:        prefix4,
		Inet6Address:        prefix6,
		MTU:                 9000,
		FileDescriptor:      fd,
	}
	return nil
}

// installSocketHook routes every core-opened socket through VpnService.protect.
// Setting this also makes mihomo skip interface binding, routing marks and TCP
// fast open, none of which an unprivileged Android process can use.
func installSocketHook(protector Protector) {
	dialer.DefaultSocketHook = func(_ string, _ string, conn syscall.RawConn) error {
		var protected bool
		if err := conn.Control(func(fd uintptr) {
			protected = protector.Protect(int32(fd))
		}); err != nil {
			return fmt.Errorf("inspect core socket: %w", err)
		}
		if !protected {
			return errors.New("VpnService.protect rejected a core socket")
		}
		return nil
	}
}

// parsePrefixes splits "172.19.0.1/30" or "172.19.0.1/30,fdfe:dcba:9876::1/126"
// into per-family prefix lists.
func parsePrefixes(value string) (prefix4 []netip.Prefix, prefix6 []netip.Prefix, err error) {
	for _, entry := range strings.Split(value, ",") {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		prefix, parseErr := netip.ParsePrefix(entry)
		if parseErr != nil {
			return nil, nil, parseErr
		}
		if prefix.Addr().Is4() {
			prefix4 = append(prefix4, prefix)
		} else {
			prefix6 = append(prefix6, prefix)
		}
	}
	return prefix4, prefix6, nil
}

// hijackTargets turns "172.19.0.2" into the "host:53" form the tun listener wants.
func hijackTargets(value string) []string {
	var targets []string
	for _, entry := range strings.Split(value, ",") {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		targets = append(targets, net.JoinHostPort(entry, "53"))
	}
	return targets
}
