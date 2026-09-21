package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.groupLatency
import dev.zephyr.mobile.resolveChain
import dev.zephyr.mobile.selectGroups
import dev.zephyr.mobile.ui.DelayDot
import dev.zephyr.mobile.ui.DelayPill
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZSwitch
import dev.zephyr.mobile.ui.ZTextField
import dev.zephyr.mobile.ui.nodeMeta
import dev.zephyr.mobile.ui.regionOf

@Composable
fun ProxiesScreen(onNavigateProfiles: () -> Unit) {
    val proxies by ZephyrState.proxies.collectAsState()
    val status by ZephyrState.status.collectAsState()

    val groups = remember(proxies) { selectGroups(proxies) }
    var activeName by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var hideDead by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if (groups.isEmpty()) {
            activeName = null
        } else if (activeName == null || groups.none { it.name == activeName }) {
            activeName = groups.first().name
        }
    }

    val active = activeName?.let { proxies[it] }
    val switchable = active?.type.equals("Selector", ignoreCase = true)

    // Nodes bucketed by the region their name implies, same as the desktop list.
    val buckets = remember(active, proxies, query, hideDead) {
        val all = active?.all.orEmpty()
        val needle = query.trim().lowercase()
        val map = LinkedHashMap<String, MutableList<String>>()
        val names = LinkedHashMap<String, String>()
        for (node in all) {
            if (needle.isNotEmpty() && !node.lowercase().contains(needle)) continue
            val latency = proxies[node]?.latency
            if (hideDead && latency != null && latency <= 0) continue
            val region = regionOf(node)
            names[region.code] = region.name
            map.getOrPut(region.code) { mutableListOf() }.add(node)
        }
        map.entries.map { (code, nodes) -> Triple(code, names[code].orEmpty(), nodes.toList()) }
    }
    val visibleCount = buckets.sumOf { it.third.size }

    if (groups.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Z.gutter)) {
            PageHeader("02 / PROXIES", "节点", "策略组与节点都来自当前订阅")
            ZCard {
                EmptyState(
                    icon = ZIcon.Globe,
                    title = if (status.running) "内核还没有返回节点" else "还没有可用的节点",
                    description = if (status.running) {
                        "稍等片刻，或下拉刷新。"
                    } else {
                        "添加一个订阅并连接之后，它的策略组和节点会出现在这里。"
                    },
                    action = {
                        ZButton("去添加订阅", onClick = onNavigateProfiles, primary = true, icon = ZIcon.Plus)
                    },
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                kicker = "02 / PROXIES",
                title = "节点",
                subtitle = "${groups.size} 个策略组 · 当前 ${active?.name ?: "--"} 指向 ${resolveChain(proxies, active?.now)}",
            )
        }

        // The desktop puts the group list in a left rail; a phone gets a
        // scrolling row of chips instead, which keeps the same information
        // one tap away without stealing half the width.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                groups.forEach { group ->
                    GroupChip(
                        group = group,
                        selected = group.name == activeName,
                        latency = groupLatency(proxies, group),
                        onClick = { activeName = group.name },
                    )
                }
            }
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
                            if (switchable) "当前 ${active?.now ?: "--"}"
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
                }
                HairLine()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "查找节点，例如 香港、IEPL、0.5x",
                        leading = ZIcon.Search,
                        rounded = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("隐藏超时", fontSize = 12.sp, color = Z.muted)
                    Spacer(Modifier.width(7.dp))
                    ZSwitch(checked = hideDead, onChange = { hideDead = it })
                }
            }
        }

        if (visibleCount == 0) {
            item {
                ZCard {
                    Text(
                        "没有匹配的节点，换个关键词试试。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        buckets.forEach { (code, regionName, nodes) ->
            item(key = "head-$code") {
                val measured = nodes.mapNotNull { proxies[it]?.latency }.filter { it > 0 }
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
                            code,
                            style = MonoSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Z.muted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(regionName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        buildString {
                            append("${nodes.size} 个节点")
                            if (measured.isNotEmpty()) append(" · 平均 ${measured.average().toInt()} ms")
                        },
                        fontSize = 11.5.sp,
                        color = Z.muted,
                    )
                }
            }

            items(nodes, key = { "$code-$it" }) { node ->
                NodeRow(
                    name = node,
                    type = proxies[node]?.type ?: "--",
                    latency = proxies[node]?.latency,
                    selected = active?.now == node,
                    enabled = switchable,
                    onClick = { activeName?.let { ZephyrState.selectNode(it, node) } },
                )
            }
        }

        item {
            Text(
                "显示 $visibleCount / ${active?.all?.size ?: 0} 个节点 · 150 ms 以内为绿，300 ms 以内为橙",
                fontSize = 11.5.sp,
                color = Z.muted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GroupChip(group: ProxyItem, selected: Boolean, latency: Int?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(if (selected) Z.bluePale else Z.card, RoundedCornerShape(Z.radiusSm))
            .border(
                1.dp,
                if (selected) Z.blueLine else Z.line,
                RoundedCornerShape(Z.radiusSm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Z.blueDark else Z.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            DelayPill(latency)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            group.now ?: group.type,
            fontSize = 11.sp,
            color = Z.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
private fun NodeRow(
    name: String,
    type: String,
    latency: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Z.bluePale else Z.card, RoundedCornerShape(Z.radiusSm))
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
                color = if (selected) Z.blueDark else Z.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nodeMeta(name, type),
                fontSize = 11.5.sp,
                color = Z.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        DelayDot(latency)
    }
    Spacer(Modifier.height(6.dp))
}
