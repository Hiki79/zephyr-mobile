package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.Settings
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Merges our runtime keys over whatever the subscription shipped, the same job
 * the Windows build does with serde_yaml. The result is handed to the core as a
 * string; nothing is written to a shared location on the way.
 */
object ConfigBuilder {

    /** Used until the user adds a subscription, so the UI always has a core to talk to. */
    private const val BLANK_PROFILE = """
proxies: []
proxy-groups:
  - name: 节点选择
    type: select
    proxies: [DIRECT]
rules:
  - MATCH,DIRECT
"""

    /**
     * Listener keys we always own. A subscription that sets its own `port` or
     * `mixed-port` would otherwise collide with ours and the core would fail to
     * bind. `tun` is dropped because the Go bridge rebuilds it around the
     * VpnService descriptor after parsing.
     */
    private val OWNED_KEYS = listOf(
        "port", "socks-port", "redir-port", "tproxy-port", "mixed-port",
        "tun", "external-controller", "external-controller-tls",
        "external-controller-unix", "external-controller-pipe", "external-ui",
        "secret", "allow-lan", "bind-address", "log-level", "mode", "ipv6",
        "unified-delay", "tcp-concurrent", "find-process-mode",
        "global-client-fingerprint", "profile",
    )

    fun build(profileYaml: String?, settings: Settings): String {
        val source = profileYaml?.takeIf { it.isNotBlank() } ?: BLANK_PROFILE
        val root = parseToMap(source)

        // A method reference would not type-check here: remove returns the old
        // value, and forEach wants Unit.
        OWNED_KEYS.forEach { key -> root.remove(key) }

        // The mixed port is not what carries traffic here — the TUN is — but it
        // keeps the core reachable for anything the user points at it manually,
        // and it mirrors the desktop build.
        root["mixed-port"] = settings.mixedPort
        root["external-controller"] = "127.0.0.1:${settings.ctrlPort}"
        root["secret"] = settings.secret
        root["mode"] = settings.mode
        root["log-level"] = settings.logLevel
        root["allow-lan"] = settings.allowLan
        root["ipv6"] = settings.ipv6
        root["unified-delay"] = settings.unifiedDelay
        root["tcp-concurrent"] = true
        root["global-client-fingerprint"] = "chrome"
        // Resolving the owning app of a connection needs a privileged lookup
        // this process does not have on Android 10+, so asking for it only
        // wastes work on every connection.
        root["find-process-mode"] = "off"

        root["profile"] = linkedMapOf<String, Any?>(
            "store-selected" to true,
            "store-fake-ip" to true,
        )

        // Only supply DNS when the subscription does not bring a working one of
        // its own. Without an enabled resolver the TUN has nothing to hijack to.
        if (!hasEnabledDns(root)) {
            root["dns"] = defaultDns(settings.ipv6)
        }

        return dump(root)
    }

    private fun parseToMap(text: String): LinkedHashMap<String, Any?> {
        // A subscription is remote input from the provider. SafeConstructor
        // keeps YAML tags from instantiating arbitrary classes; the raised code
        // point limit is only there because real configs run past the default.
        val loaderOptions = LoaderOptions().apply {
            codePointLimit = 32 * 1024 * 1024
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 256
        }
        val parsed = Yaml(SafeConstructor(loaderOptions)).load<Any?>(text)
        val result = LinkedHashMap<String, Any?>()
        if (parsed is Map<*, *>) {
            parsed.forEach { (key, value) -> if (key is String) result[key] = value }
        }
        return result
    }

    private fun hasEnabledDns(root: Map<String, Any?>): Boolean {
        val dns = root["dns"] as? Map<*, *> ?: return false
        return dns["enable"] == true
    }

    private fun defaultDns(ipv6: Boolean): LinkedHashMap<String, Any?> = linkedMapOf(
        "enable" to true,
        "listen" to "127.0.0.1:1053",
        "ipv6" to ipv6,
        "enhanced-mode" to "fake-ip",
        "fake-ip-range" to "198.18.0.1/16",
        "fake-ip-filter" to listOf(
            "*.lan",
            "*.local",
            "localhost.ptlogin2.qq.com",
            "+.msftconnecttest.com",
            "+.msftncsi.com",
            "stun.*.*",
            "time.*.com",
        ),
        "default-nameserver" to listOf("223.5.5.5", "119.29.29.29"),
        "nameserver" to listOf(
            "https://dns.alidns.com/dns-query",
            "https://doh.pub/dns-query",
        ),
    )

    private fun dump(root: Map<String, Any?>): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            width = 8192
            isAllowUnicode = true
            splitLines = false
        }
        return Yaml(options).dump(root)
    }
}
