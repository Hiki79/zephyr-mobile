package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.CoreStage
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.isRealNode
import dev.zephyr.mobile.resolveChain
import dev.zephyr.mobile.selectGroups
import dev.zephyr.mobile.ui.DelayDot
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.GroupSelector
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.MultiplierBadge
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Region
import dev.zephyr.mobile.ui.Segmented
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZSwitch
import dev.zephyr.mobile.ui.ZTextField
import dev.zephyr.mobile.ui.isInfoNode
import dev.zephyr.mobile.ui.matchesQuery
import dev.zephyr.mobile.ui.multiplierOf
import dev.zephyr.mobile.ui.regionOf
import kotlinx.coroutines.launch

/** One line of the node list: a region heading, or a node under it. */
private sealed interface NodeListRow {
    val key: String
}

private data class RegionHeading(val region: Region, val nodes: List<String>) : NodeListRow {
    override val key: String get() = "head-${region.code}"
}

private data class NodeLine(val name: String, val notice: Boolean) : NodeListRow {
    override val key: String get() = "node-$name"
}

private val GROUP_REGION = Region("GRP", "策略组与直连")
private val NOTICE_REGION = Region("INFO", "订阅信息")

/** Measured first, then untested, then timed out. */
private fun latencyRank(latency: Int?): Int = when {
    latency == null -> Int.MAX_VALUE - 1
    latency <= 0 -> Int.MAX_VALUE
    else -> latency
}

/**
 * Nodes bucketed by the region their name implies, same as the desktop list.
 * Nested groups and built-ins lead; the provider's notices go last, apart.
 */
private fun buildNodeRows(
    active: ProxyItem?,
    proxies: Map<String, ProxyItem>,
    query: String,
    prefs: ZephyrState.NodeListPrefs,
): List<NodeListRow> {
    val buckets = LinkedHashMap<Region, MutableList<String>>()
    val notices = mutableListOf<String>()
    for (node in active?.all.orEmpty()) {
        if (!matchesQuery(node, query)) continue
        val item = proxies[node]
        val latency = item?.latency
        if (prefs.hideTimeouts && latency != null && latency <= 0) continue
        val region = when {
            item?.isGroup == true -> GROUP_REGION
            isInfoNode(node) -> { notices += node; continue }
            item != null && !isRealNode(item) -> GROUP_REGION
            else -> regionOf(node)
        }
        buckets.getOrPut(region) { mutableListOf() } += node
    }
    val rows = ArrayList<NodeListRow>()
    for ((region, nodes) in buckets) {
        val ordered = if (prefs.byLatency) nodes.sortedBy { latencyRank(proxies[it]?.latency) } else nodes
        rows += RegionHeading(region, ordered)
        ordered.mapTo(rows) { NodeLine(it, notice = false) }
    }
    if (notices.isNotEmpty()) {
        rows += RegionHeading(NOTICE_REGION, notices)
        notices.mapTo(rows) { NodeLine(it, notice = true) }
    }
    return rows
}

@Composable
fun ProxiesScreen(onNavigateProfiles: () -> Unit, onConnect: () -> Unit) {
    val proxies by ZephyrState.proxies.collectAsStateWithLifecycle()
    val status by ZephyrState.status.collectAsStateWithLifecycle()
    val settings by ZephyrState.settings.collectAsStateWithLifecycle()
    val profiles by ZephyrState.profiles.collectAsStateWithLifecycle()
    val focused by ZephyrState.focusedGroup.collectAsStateWithLifecycle()
    val prefs by ZephyrState.nodePrefs.collectAsStateWithLifecycle()
    val displayedSettings = status.runtimeSettings ?: settings
    val currentProfile = profiles.find { it.uid == displayedSettings.currentProfile }

    val groups = remember(proxies, displayedSettings.mode) { selectGroups(proxies, displayedSettings.mode) }
    // Kept in the shared state, so the choice survives tab switches and the
    // overview and the tile panel can open a particular group.
    val activeName = focused?.takeIf { name -> groups.any { it.name == name } } ?: groups.firstOrNull()?.name
    var query by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val active = activeName?.let { proxies[it] }
    val switchable = active?.type.equals("Selector", ignoreCase = true)
    val rows = remember(active, proxies, query, prefs) { buildNodeRows(active, proxies, query, prefs) }
    val visibleCount = rows.count { it is NodeLine && !it.notice }

    if (groups.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Z.gutter)) {
            PageHeader("02 / PROXIES", "节点", "策略组与节点都来自当前订阅")
            ZCard {
                EmptyState(
                    icon = ZIcon.Globe,
                    title = if (status.running) "内核还没有返回节点" else if (currentProfile != null) "订阅已导入" else "还没有订阅",
                    description = if (status.running) {
                        "稍等片刻，或点刷新重试。"
                    } else if (currentProfile != null) {
                        status.lastError ?: "${currentProfile.name} 已保存，连接后加载节点提供器。"
                    } else {
                        "添加一个订阅并连接之后，它的策略组和节点会出现在这里。"
                    },
                    action = {
                        when {
                            status.running -> ZButton("刷新", onClick = ZephyrState::refreshProxies, primary = true)
                            currentProfile != null -> ZButton("连接代理", onClick = onConnect, primary = true,
                                enabled = status.stage != CoreStage.STARTING)
                            else -> ZButton("去添加订阅", onClick = onNavigateProfiles, primary = true, icon = ZIcon.Plus)
                        }
                    },
                )
            }
        }
        return
    }

    // The items ahead of the node rows below, counted so "定位" can scroll to one.
    val leadingItems = if (status.running) 4 else 5
    val currentIndex = rows.indexOfFirst { it is NodeLine && it.name == active?.now }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { dev.zephyr.mobile.ui.SessionNotice() }
        item {
            PageHeader(
                kicker = "02 / PROXIES",
                title = "节点",
                subtitle = "${groups.size} 个策略组 · 当前 ${active?.name ?: "--"} 指向 ${resolveChain(proxies, active?.now)}",
            )
        }

        if (!status.running) {
            item {
                ZCard {
                    Column(Modifier.padding(14.dp)) {
                        Text(status.lastError ?: "已从订阅读取节点，连接后可切换和测速。", color = Z.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        ZButton("连接代理", onClick = onConnect, primary = true,
                            enabled = status.stage != CoreStage.STARTING)
                    }
                }
            }
        }

        item {
            GroupSelector(groups, proxies, activeName) { ZephyrState.focusGroup(it); query = "" }
        }

        item {
            ZCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                active?.name.orEmpty(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Z.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("(${active?.all?.size ?: 0})", fontSize = 14.sp, color = Z.faint)
                        }
                        Text(
                            if (!status.running) "订阅预览 · 连接后加载实际选择"
                            else if (switchable) "当前 ${active?.now ?: "--"}"
                            else "${active?.type} 组由内核自动选择，不能手动切换",
                            fontSize = 12.sp,
                            color = Z.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ZButton(
                        if (testing) "测速中" else "测速",
                        onClick = {
                            val name = activeName ?: return@ZButton
                            testing = true
                            ZephyrState.testGroup(name) { testing = false }
                        },
                        icon = ZIcon.Zap,
                        small = true,
                        enabled = !testing && status.running,
                    )
                    if (switchable) {
                        Spacer(Modifier.width(6.dp))
                        ZButton(
                            "选最快",
                            onClick = { activeName?.let(ZephyrState::selectFastest) },
                            small = true,
                            enabled = status.running,
                        )
                    }
                }
                HairLine()
                ZTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "查找节点，例如 香港 家宽、0.5x",
                    leading = ZIcon.Search,
                    rounded = true,
                    clearable = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Segmented(
                        value = prefs.byLatency,
                        options = listOf(false to "原顺序", true to "按延迟"),
                        onChange = { byLatency -> ZephyrState.updateNodePrefs { it.copy(byLatency = byLatency) } },
                    )
                    Spacer(Modifier.weight(1f))
                    if (currentIndex >= 0) {
                        ZButton(
                            "定位",
                            onClick = {
                                scope.launch { listState.animateScrollToItem(leadingItems + (currentIndex - 1).coerceAtLeast(0)) }
                            },
                            small = true,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("隐藏超时", fontSize = 12.sp, color = Z.muted)
                    ZSwitch(
                        checked = prefs.hideTimeouts,
                        onChange = { hide -> ZephyrState.updateNodePrefs { it.copy(hideTimeouts = hide) } },
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                ZCard {
                    Text(
                        if (!status.running && active?.all.isNullOrEmpty()) "此组的节点提供器会在连接后加载。"
                        else "没有匹配的节点，换个关键词试试。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(rows, key = { it.key }) { row ->
            when (row) {
                is RegionHeading -> RegionHeader(row, proxies)
                is NodeLine -> {
                    val item = proxies[row.name]
                    NodeRow(
                        name = row.name,
                        meta = when {
                            row.notice -> "服务商写在节点列表里的提示，不能连接"
                            item?.isGroup == true -> "${item.type} → ${item.now ?: "--"}"
                            else -> item?.type ?: "--"
                        },
                        latency = if (row.notice) null else item?.latency,
                        multiplier = if (row.notice || item?.isGroup == true) null else multiplierOf(row.name),
                        selected = active?.now == row.name,
                        enabled = switchable && status.running && !row.notice,
                        notice = row.notice,
                        onClick = { activeName?.let { ZephyrState.selectNode(it, row.name) } },
                    )
                }
            }
        }

        item {
            Text(
                "显示 $visibleCount / ${active?.all?.size ?: 0} 项 · 150 ms 以内为绿，300 ms 以内为橙 · 倍率 ×3 以上标红",
                fontSize = 11.5.sp,
                color = Z.muted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RegionHeader(heading: RegionHeading, proxies: Map<String, ProxyItem>) {
    val measured = heading.nodes.mapNotNull { proxies[it]?.latency }.filter { it > 0 }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(Color(0xFFF1F1EC), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                heading.region.code,
                style = MonoSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Z.muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(heading.region.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
        Spacer(Modifier.weight(1f))
        Text(
            when (heading.region) {
                NOTICE_REGION -> "${heading.nodes.size} 条"
                GROUP_REGION -> "${heading.nodes.size} 项"
                else -> buildString {
                    append("${heading.nodes.size} 个节点")
                    if (measured.isNotEmpty()) append(" · 平均 ${measured.average().toInt()} ms")
                }
            },
            fontSize = 11.5.sp,
            color = Z.muted,
        )
    }
}

@Composable
private fun NodeRow(
    name: String,
    meta: String,
    latency: Int?,
    multiplier: Double?,
    selected: Boolean,
    enabled: Boolean,
    notice: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Z.bluePale else if (notice) Z.paper else Z.card, RoundedCornerShape(Z.radiusSm))
            .border(
                1.dp,
                if (selected) Z.blue else Z.line,
                RoundedCornerShape(Z.radiusSm),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Z.blueDark else if (notice) Z.muted else Z.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                fontSize = 11.5.sp,
                color = if (notice) Z.faint else Z.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (multiplier != null) {
            Spacer(Modifier.width(8.dp))
            MultiplierBadge(multiplier)
        }
        if (!notice) {
            Spacer(Modifier.width(10.dp))
            DelayDot(latency)
        }
    }
    Spacer(Modifier.height(6.dp))
}
