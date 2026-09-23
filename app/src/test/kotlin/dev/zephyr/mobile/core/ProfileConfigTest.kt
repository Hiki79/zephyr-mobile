package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.Settings
import dev.zephyr.mobile.selectGroups
import org.junit.Assert.*
import org.junit.Test

class ProfileConfigTest {
    private val config = """
        proxies:
          - {name: "香港 01", type: ss, server: example.com, port: 443, cipher: aes-128-gcm, password: test}
          - name: "日本 02"
            type: trojan
            server: example.org
            port: 443
            password: test
        proxy-groups:
          - name: 节点选择
            type: select
            proxies: ["香港 01", "日本 02"]
        rules: ["MATCH,节点选择"]
    """.trimIndent()

    @Test fun localPreviewCountsNodesWithoutCountingGroups() {
        val preview = ProfileConfig.parse(config)
        assertEquals(2, preview.nodeCount)
        assertEquals(listOf("香港 01", "日本 02"), preview.proxies["节点选择"]!!.all)
        assertEquals("香港 01", preview.proxies["节点选择"]!!.now)
    }

    @Test fun nodeOnlySubscriptionRemainsVisible() {
        val preview = ProfileConfig.parse("proxies: [{name: node, type: ss}]")
        assertEquals("GLOBAL", selectGroups(preview.proxies).single().name)
        assertTrue(selectGroups(preview.proxies).single().all!!.contains("node"))
    }

    @Test fun globalModeExposesTheActualGlobalSelector() {
        val preview = ProfileConfig.parse(config)
        assertEquals("GLOBAL", selectGroups(preview.proxies, "global").single().name)
        assertEquals("节点选择", selectGroups(preview.proxies, "rule").single().name)
    }

    @Test fun emptyProviderGroupsRemainVisibleBeforeConnection() {
        val preview = ProfileConfig.parse("""
            proxy-providers:
              remote: {type: http, url: "https://example.com/nodes"}
            proxy-groups:
              - {name: remote-group, type: select, use: [remote]}
        """.trimIndent())
        assertEquals(0, preview.nodeCount)
        assertEquals(1, preview.providerCount)
        assertEquals("remote-group", selectGroups(preview.proxies).single().name)
    }

    @Test fun quotedKeysAndFlowYamlAreSupported() {
        assertEquals(1, ProfileConfig.parse("{'proxies': [{name: test, type: ss}]}").nodeCount)
    }

    @Test fun invalidContentIsRejectedBeforeReplacingTheSubscription() {
        listOf("<html>proxies:</html>", "# proxies:\nhello: world", "proxies: []",
            "proxies: [{name: repeated, type: ss}, {name: repeated, type: ss}]").forEach { text ->
            assertThrows(Exception::class.java) { ProfileConfig.parse(text) }
        }
    }

    @Test fun runtimeMergePreservesNodesAndOwnsListeners() {
        val yaml = ConfigBuilder.build(config + "\nmixed-port: 9999\ninterface-name: eth0\nlisteners: []", Settings(secret = "test"))
        val merged = ConfigBuilder.parseToMap(yaml)
        assertEquals(2, ProfileConfig.parse(yaml).nodeCount)
        assertEquals(7890, merged["mixed-port"])
        assertEquals("127.0.0.1:9090", merged["external-controller"])
        assertFalse(merged.containsKey("interface-name"))
        assertFalse(merged.containsKey("listeners"))
        assertEquals(true, (merged["dns"] as Map<*, *>)["enable"])
    }

    @Test fun missingProfileCannotSilentlyStartDirectOnlyVpn() {
        assertThrows(IllegalArgumentException::class.java) { ConfigBuilder.build(null, Settings()) }
        assertThrows(IllegalArgumentException::class.java) { ConfigBuilder.build("", Settings()) }
    }

    @Test fun providerMetadataMustUseHttps() {
        val insecure = """
            proxy-providers:
              remote: {type: http, url: "http://example.com/nodes"}
            proxy-groups: [{name: remote, type: select, use: [remote]}]
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { ProfileConfig.parse(insecure) }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigBuilder.build(insecure, Settings(secret = "test"))
        }
    }

    @Test fun embeddedDnsListenerIsDisabled() {
        val yaml = ConfigBuilder.build(config + "\ndns:\n  enable: true\n  listen: 0.0.0.0:53", Settings(secret = "test"))
        assertEquals("", (ConfigBuilder.parseToMap(yaml)["dns"] as Map<*, *>)["listen"])
    }
}
