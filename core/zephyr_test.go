package zephyrcore

import (
	"testing"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/listener"
	lc "github.com/metacubex/mihomo/listener/config"
)

func TestTunUsesAndroidDescriptorWithoutPrivilegedRouteSetup(t *testing.T) {
	cfg := &config.Config{General: &config.General{}}
	if err := applyTun(cfg, 42, "172.19.0.1/30,fdfe:dcba:9876::1/126", "172.19.0.2,fdfe:dcba:9876::2"); err != nil {
		t.Fatal(err)
	}
	tun := cfg.General.Tun
	if tun.FileDescriptor != 42 || tun.AutoRoute || tun.AutoDetectInterface || !tun.Enable {
		t.Fatalf("invalid Android TUN options: %+v", tun)
	}
	if len(tun.Inet4Address) != 1 || len(tun.Inet6Address) != 1 || tun.DNSHijack[1] != "[fdfe:dcba:9876::2]:53" {
		t.Fatal("IPv4/IPv6 gateway and DNS mismatch")
	}
}

func TestStopResetsTunConfigSoReusedDescriptorCanRestart(t *testing.T) {
	// Upstream Cleanup closes the listener but retains this enabled config.
	listener.LastTunConf = lc.Tun{Enable: true, FileDescriptor: 42}
	shutdownCore()
	if listener.GetTunConf().Enable || listener.GetTunConf().FileDescriptor != 0 {
		t.Fatal("stale enabled TUN config would skip creating the next tunnel")
	}
}
