package dev.zephyr.mobile

import dev.zephyr.mobile.data.DelayHistory
import dev.zephyr.mobile.data.ProxyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupsTest {
    private fun node(name: String, delay: Int? = null) =
        ProxyItem(name = name, type = "Vless", history = listOfNotNull(delay?.let { DelayHistory(delay = it) }))

    private fun group(name: String, now: String?, vararg all: String, type: String = "Selector") =
        ProxyItem(name = name, type = type, now = now, all = all.toList())

    // The shape of a typical 36-group subscription, cut down.
    private val proxies = listOf(
        ProxyItem(name = "DIRECT", type = "Direct"),
        ProxyItem(name = "REJECT", type = "Reject"),
        node("[vless]香港|移动|x1", 80),
        node("[ss]香港|砖线1|x25", 20),
        node("[vless]日本|软银|x0.5", 120),
        node("剩余流量：491.1 GB", 5),
        group("🚀 节点选择", "⚡ 自动选择", "⚡ 自动选择", "DIRECT", "[vless]香港|移动|x1", "剩余流量：491.1 GB"),
        group("⚡ 自动选择", "[ss]香港|砖线1|x25", "[vless]香港|移动|x1", "[ss]香港|砖线1|x25", type = "URLTest"),
        group("📹 油管视频", "🚀 节点选择", "🚀 节点选择", "DIRECT"),
        group("🎬 奈飞", "🚀 节点选择", "🚀 节点选择", "DIRECT"),
        group("🐦 推特", "🚀 节点选择", "🚀 节点选择"),
        group("🔒 国内服务", "DIRECT", "DIRECT", "🚀 节点选择"),
        group("🛑 广告拦截", "REJECT", "REJECT", "DIRECT"),
        group("🎮 Steam", "[vless]日本|软银|x0.5", "🚀 节点选择", "[vless]日本|软银|x0.5"),
    ).associateBy { it.name }

    private val groups = proxies.values.filter { it.isGroup }

    @Test fun followersAreSummarisedAndExceptionsKept() {
        val layout = layoutGroups(groups, proxies, keep = null)
        assertEquals(listOf("🚀 节点选择", "⚡ 自动选择", "🎮 Steam"), layout.primary.map { it.name })
        assertEquals(mapOf("🚀 节点选择" to 3), layout.followers)
        assertEquals(1, layout.direct)
        assertEquals(1, layout.blocked)
        assertEquals("3 组跟随「🚀 节点选择」 · 1 组直连 · 1 组拦截", layout.summary())
    }

    @Test fun theSelectedGroupStaysInTheQuickRow() {
        val layout = layoutGroups(groups, proxies, keep = "🎬 奈飞")
        assertEquals(true, layout.primary.any { it.name == "🎬 奈飞" })
        assertEquals(mapOf("🚀 节点选择" to 2), layout.followers)
    }

    @Test fun smallConfigsAreNotFolded() {
        val few = groups.filter { it.name in setOf("🚀 节点选择", "⚡ 自动选择", "📹 油管视频") }
        assertEquals(few, layoutGroups(few, proxies, keep = null).primary)
    }

    @Test fun exitFollowsNestedGroupsToTheNode() {
        assertEquals("[ss]香港|砖线1|x25", finalExit(proxies, "🚀 节点选择")?.name)
        assertNull(finalExit(proxies, null))
    }

    @Test fun fastestSkipsCostlyNodesNoticesAndGroups() {
        // The ×25 line is quicker and the notice "answers" in 5 ms; neither may win.
        assertEquals("[vless]香港|移动|x1", fastestNode(proxies, proxies.getValue("🚀 节点选择"))?.name)
        assertEquals("[ss]香港|砖线1|x25", fastestNode(proxies, proxies.getValue("⚡ 自动选择"), maxMultiplier = 30.0)?.name)
        assertNull(fastestNode(proxies, proxies.getValue("🛑 广告拦截")))
    }
}
