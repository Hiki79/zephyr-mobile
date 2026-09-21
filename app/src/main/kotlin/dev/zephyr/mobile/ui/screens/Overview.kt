package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.CoreStage
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.data.TrafficSample
import dev.zephyr.mobile.groupLatency
import dev.zephyr.mobile.resolveChain
import dev.zephyr.mobile.selectGroups
import dev.zephyr.mobile.ui.CardFoot
import dev.zephyr.mobile.ui.CardHeader
import dev.zephyr.mobile.ui.DelayPill
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.KickerStyle
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.SectionLabel
import dev.zephyr.mobile.ui.Segmented
import dev.zephyr.mobile.ui.Tag
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZSwitch
import dev.zephyr.mobile.ui.formatBytes
import dev.zephyr.mobile.ui.formatUptime
import dev.zephyr.mobile.ui.splitRate
import kotlinx.coroutines.delay

private const val DEFAULT_SUMMARY = 4

@Composable
fun OverviewScreen(
    onNavigateProxies: () -> Unit,
    onNavigateProfiles: () -> Unit,
    onNavigateConnections: () -> Unit,
    onToggleVpn: (Boolean) -> Unit,
) {
    val status by ZephyrState.status.collectAsState()
    val settings by ZephyrState.settings.collectAsState()
    val proxies by ZephyrState.proxies.collectAsState()
    val traffic by ZephyrState.traffic.collectAsState()
    val connections by ZephyrState.connections.collectAsState()
    val memory by ZephyrState.memory.collectAsState()
    val profiles by ZephyrState.profiles.collectAsState()

    var picking by remember { mutableStateOf(false) }

    val groups = remember(proxies, settings.mode) { selectGroups(proxies, settings.mode) }
    val pinned = settings.pinnedGroups
    val summary = remember(groups, pinned) {
        if (pinned.isEmpty()) groups.take(DEFAULT_SUMMARY) else groups.filter { it.name in pinned }
    }
    val currentProfile = profiles.find { it.uid == settings.currentProfile }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            PageHeader(
                kicker = "01 / OVERVIEW",
                title = "连接总览",
                subtitle = buildString {
                    append(currentProfile?.let { "当前订阅 ${it.name}" } ?: "尚未添加订阅")
                    append(" · ")
                    append(
                        when (status.stage) {
                            CoreStage.RUNNING -> "内核运行中"
                            CoreStage.STARTING -> "正在启动"
                            CoreStage.FAILED -> status.lastError ?: "启动失败"
                            CoreStage.STOPPED -> "未连接"
                        },
                    )
                },
            )
        }

        item { HeroCard(traffic, status.stage, settings.mixedPort) }

        item {
            ZCard {
                ControlRow(
                    icon = ZIcon.Power,
                    title = "代理连接",
                    subtitle = when (status.stage) {
                        CoreStage.RUNNING -> "全部流量正在经过 Zephyr"
                        CoreStage.STARTING -> "正在建立隧道"
                        CoreStage.FAILED -> status.lastError ?: "启动失败"
                        CoreStage.STOPPED -> "未接管设备流量"
                    },
                    active = status.running,
                ) {
                    ZSwitch(
                        checked = status.stage == CoreStage.RUNNING || status.stage == CoreStage.STARTING,
                        onChange = onToggleVpn,
                        enabled = currentProfile != null || status.running,
                    )
                }
                HairLine()
                ControlRow(
                    icon = ZIcon.Route,
                    title = "出站模式",
                    subtitle = when (settings.mode) {
                        "global" -> "全部流量走当前节点"
                        "direct" -> "全部流量不走代理"
                        else -> "按规则分流，国内直连"
                    },
                    active = true,
                ) {
                    Segmented(
                        value = settings.mode,
                        options = listOf("rule" to "规则", "global" to "全局", "direct" to "直连"),
                        onChange = ZephyrState::setMode,
                    )
                }
            }
        }

        item {
            ActivityCard(
                connectionCount = connections.connections?.size ?: 0,
                totalDown = connections.downloadTotal,
                totalUp = connections.uploadTotal,
                memory = memory,
                running = status.running,
                startedAt = status.startedAt,
                onOpenConnections = onNavigateConnections,
            )
        }

        item {
            ZCard {
                CardHeader(
                    title = "策略路由",
                    count = "(${summary.size} / ${groups.size})",
                    description = if (pinned.isEmpty()) {
                        "点一行换节点 · 现在显示前几个分组，可以自己选"
                    } else {
                        "点一行换节点 · 显示的是你选的分组"
                    },
                    trailing = {
                        ZButton(
                            "选择",
                            onClick = { picking = true },
                            icon = ZIcon.ListChecks,
                            small = true,
                            enabled = groups.isNotEmpty(),
                        )
                    },
                )

                when {
                    groups.isEmpty() -> CardFoot(
                        if (status.running) "内核还没有返回策略组" else "连接之后这里会列出策略组",
                    )

                    summary.isEmpty() -> CardFoot("你选的分组在当前订阅里都不存在") {
                        ZButton("重新选择", onClick = { picking = true }, small = true)
                    }

                    else -> {
                        summary.forEachIndexed { index, group ->
                            if (index > 0) HairLine()
                            GroupRow(
                                group = group,
                                chain = resolveChain(proxies, group.now),
                                latency = groupLatency(proxies, group),
                                onClick = onNavigateProxies,
                            )
                        }
                        CardFoot("全部 ${groups.size} 个分组在节点页") {
                            ZButton("去节点页", onClick = onNavigateProxies, small = true)
                        }
                    }
                }
            }
        }

        if (currentProfile == null) {
            item {
                ZCard {
                    EmptyState(
                        icon = ZIcon.Download,
                        title = "先添加一个订阅",
                        description = "添加机场给的 Clash 订阅地址之后，就可以连接了。",
                        action = {
                            ZButton("去添加", onClick = onNavigateProfiles, primary = true, icon = ZIcon.Plus)
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (picking) {
        GroupPickerDialog(
            groups = groups,
            proxies = proxies,
            initial = summary.map { it.name },
            onDismiss = { picking = false },
            onSave = { chosen ->
                ZephyrState.updateSettings { it.copy(pinnedGroups = chosen) }
                picking = false
                ZephyrState.toast(
                    if (chosen.isEmpty()) "已恢复默认，显示前 $DEFAULT_SUMMARY 个分组"
                    else "策略路由现在显示 ${chosen.size} 个分组",
                )
            },
        )
    }
}

// ------------------------------------------------------------------ pieces

/** The one blue slab on the page, carrying the live traffic read-out. */
@Composable
private fun HeroCard(traffic: List<TrafficSample>, stage: CoreStage, port: Int) {
    val latest = traffic.lastOrNull() ?: TrafficSample()
    val (downValue, downUnit) = splitRate(latest.down)
    val (upValue, upUnit) = splitRate(latest.up)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Z.blue, RoundedCornerShape(Z.radius))
            .padding(start = 18.dp, end = 18.dp, top = 15.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(7.dp).background(
                    when (stage) {
                        CoreStage.RUNNING -> Color(0xFF6BE3A4)
                        CoreStage.STARTING -> Color(0xFFFFD08A)
                        CoreStage.FAILED -> Color(0xFFFF9A9A)
                        CoreStage.STOPPED -> Color(0x66FFFFFF)
                    },
                    CircleShape,
                ),
            )
            Spacer(Modifier.width(9.dp))
            Text("NETWORK / LIVE", style = KickerStyle, color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.weight(1f))
            Text("最近 60 秒", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.6f))
        }

        Spacer(Modifier.height(12.dp))

        Row {
            RateBlock(ZIcon.ArrowDown, "下载速率", downValue, downUnit, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.22f)))
            Spacer(Modifier.width(18.dp))
            RateBlock(ZIcon.ArrowUp, "上传速率", upValue, upUnit, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        TrafficChart(traffic, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (stage == CoreStage.RUNNING) "混合端口 $port" else "隧道未建立",
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.68f),
            )
            Spacer(Modifier.weight(1f))
            LegendDot(Color.White, "下行")
            Spacer(Modifier.width(12.dp))
            LegendDot(Color.White.copy(alpha = 0.5f), "上行")
        }
    }
}

@Composable
private fun RateBlock(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
        }
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(2.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.68f))
    }
}

@Composable
private fun TrafficChart(samples: List<TrafficSample>, modifier: Modifier) {
    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val peak = samples.maxOf { maxOf(it.up, it.down) }.coerceAtLeast(1L).toFloat()
        val stepX = size.width / (samples.size - 1).toFloat()

        fun y(value: Long): Float = size.height - (value.toFloat() / peak) * size.height * 0.92f

        val downPath = Path()
        val fillPath = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * stepX
            val py = y(sample.down)
            if (index == 0) {
                downPath.moveTo(x, py)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, py)
            } else {
                downPath.lineTo(x, py)
                fillPath.lineTo(x, py)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(fillPath, Color.White.copy(alpha = 0.16f))
        drawPath(downPath, Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round))

        val upPath = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * stepX
            val py = y(sample.up)
            if (index == 0) upPath.moveTo(x, py) else upPath.lineTo(x, py)
        }
        drawPath(upPath, Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))

        drawLine(
            Color.White.copy(alpha = 0.14f),
            Offset(0f, size.height),
            Offset(size.width, size.height),
            strokeWidth = 1f,
        )
    }
}

@Composable
private fun ControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(if (active) Z.bluePale else Color(0xFFF1F2F5), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (active) Z.blue else Z.muted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
            Text(
                subtitle,
                fontSize = 11.5.sp,
                color = Z.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

/**
 * Keeps its own one-second clock so the uptime line ticks without anything
 * upstream re-rendering; the counters it shows come from the store.
 */
@Composable
private fun ActivityCard(
    connectionCount: Int,
    totalDown: Long,
    totalUp: Long,
    memory: Long,
    running: Boolean,
    startedAt: Long,
    onOpenConnections: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    // Reading `now` is what schedules the recomposition each second.
    val uptime = if (running && now > 0) formatUptime(startedAt) else "未启动"

    ZCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("ACTIVITY", Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .background(Z.card, RoundedCornerShape(Z.radiusSm))
                    .border(1.dp, Z.lineDark, RoundedCornerShape(Z.radiusSm))
                    .clickable(role = Role.Button, onClick = onOpenConnections),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ZIcon.ChevronRight, "查看连接", tint = Z.ink, modifier = Modifier.size(14.dp))
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 11.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "$connectionCount",
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = Z.blue,
                letterSpacing = (-1.4).sp,
            )
            Spacer(Modifier.width(8.dp))
            Text("活动连接", fontSize = 13.sp, color = Z.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        HairLine()
        ActivityRow(ZIcon.Download, "本次累计下载", formatBytes(totalDown))
        HairLine()
        ActivityRow(ZIcon.ArrowUp, "本次累计上传", formatBytes(totalUp))
        HairLine()
        ActivityRow(ZIcon.Cpu, "内核内存", formatBytes(memory))
        HairLine()
        ActivityRow(ZIcon.Zap, "运行时长", uptime)
    }
}

@Composable
private fun ActivityRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Z.muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = Z.muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
    }
}

@Composable
private fun GroupRow(group: ProxyItem, chain: String, latency: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ZIcon.Route, null, tint = Z.blue, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Z.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Tag(group.type)
            }
            Text(
                chain,
                fontSize = 11.5.sp,
                color = Z.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        DelayPill(latency)
    }
}
