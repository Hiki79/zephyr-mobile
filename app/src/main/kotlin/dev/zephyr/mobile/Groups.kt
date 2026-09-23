package dev.zephyr.mobile

import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.ui.isInfoNode
import dev.zephyr.mobile.ui.multiplierOf

private val BUILTIN_TYPES = setOf("direct", "reject", "rejectdrop", "pass", "compatible")
private val BLOCK_TYPES = setOf("reject", "rejectdrop")

/** A server that actually carries traffic: not a group, a built-in, or a notice. */
fun isRealNode(item: ProxyItem): Boolean =
    !item.isGroup && item.type.lowercase() !in BUILTIN_TYPES && !isInfoNode(item.name)

/** Follows `now` through nested groups to the node that finally carries the traffic. */
fun finalExit(proxies: Map<String, ProxyItem>, name: String?, depth: Int = 0): ProxyItem? {
    val item = proxies[name ?: return null] ?: return null
    if (!item.isGroup || depth > 6) return item
    return finalExit(proxies, item.now, depth + 1)
}

/**
 * The lowest-latency measured node of [group] at or under [maxMultiplier].
 * Notices and built-ins never qualify; a name without a multiplier counts as 1×.
 */
fun fastestNode(proxies: Map<String, ProxyItem>, group: ProxyItem, maxMultiplier: Double = 1.0): ProxyItem? =
    group.all.orEmpty()
        .mapNotNull { proxies[it] }
        .filter { isRealNode(it) && (multiplierOf(it.name) ?: 1.0) <= maxMultiplier }
        .filter { (it.latency ?: 0) > 0 }
        .minByOrNull { it.latency ?: Int.MAX_VALUE }

/**
 * Most groups in a large subscription only route one service to another
 * group ("YouTube → 节点选择"). The picker shows the groups that decide where
 * traffic exits and summarises the rest instead of listing 36 look-alike cards.
 */
data class GroupLayout(
    val primary: List<ProxyItem>,
    /** Target group name to how many groups currently follow it. */
    val followers: Map<String, Int> = emptyMap(),
    val direct: Int = 0,
    val blocked: Int = 0,
) {
    val folded: Int get() = followers.values.sum() + direct + blocked

    fun summary(): String = buildList {
        followers.entries.sortedByDescending { it.value }.forEach { (target, count) -> add("$count 组跟随「$target」") }
        if (direct > 0) add("$direct 组直连")
        if (blocked > 0) add("$blocked 组拦截")
    }.joinToString(" · ")
}

private const val MIN_FOLDED = 3

fun layoutGroups(groups: List<ProxyItem>, proxies: Map<String, ProxyItem>, keep: String?): GroupLayout {
    val names = groups.mapTo(HashSet()) { it.name }
    val targeted = groups.mapNotNullTo(HashSet()) { it.now?.takeIf { now -> now in names } }
    val primary = mutableListOf<ProxyItem>()
    val followers = LinkedHashMap<String, Int>()
    var direct = 0
    var blocked = 0
    for (group in groups) {
        val now = group.now
        if (now == null || group.name == keep || group.name in targeted) {
            primary += group
            continue
        }
        val target = proxies[now]
        val type = target?.type?.lowercase()
        when {
            now in names || target?.isGroup == true -> followers[now] = (followers[now] ?: 0) + 1
            now == "DIRECT" || type == "direct" -> direct++
            now == "REJECT" || now == "REJECT-DROP" || type in BLOCK_TYPES -> blocked++
            // Pinned to a concrete node: exactly the exception worth showing.
            else -> primary += group
        }
    }
    val layout = GroupLayout(primary, followers, direct, blocked)
    return if (layout.folded < MIN_FOLDED || primary.isEmpty()) GroupLayout(groups) else layout
}
