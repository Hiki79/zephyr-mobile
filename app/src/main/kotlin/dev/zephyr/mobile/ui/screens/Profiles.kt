package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.Profile
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Tag
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.formatBytes
import dev.zephyr.mobile.ui.formatExpiry
import dev.zephyr.mobile.ui.formatUpdated
import dev.zephyr.mobile.ui.percentOf

@Composable
fun ProfilesScreen() {
    val profiles by ZephyrState.profiles.collectAsState()
    val settings by ZephyrState.settings.collectAsState()

    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                kicker = "03 / PROFILES",
                title = "订阅",
                subtitle = "${profiles.size} 个订阅 · 只会连接你填的地址",
                trailing = {
                    ZButton("添加", onClick = { adding = true; addError = null }, icon = ZIcon.Plus, primary = true)
                },
            )
        }

        if (profiles.isEmpty()) {
            item {
                ZCard {
                    EmptyState(
                        icon = ZIcon.Download,
                        title = "还没有订阅",
                        description = "粘贴机场给的 Clash 订阅链接，Zephyr 会把它下载到本机。",
                        action = {
                            ZButton(
                                "添加订阅",
                                onClick = { adding = true; addError = null },
                                primary = true,
                                icon = ZIcon.Plus,
                            )
                        },
                    )
                }
            }
        }

        items(profiles, key = { it.uid }) { profile ->
            ProfileCard(
                profile = profile,
                current = profile.uid == settings.currentProfile,
                updating = updating == profile.uid,
                onUse = { ZephyrState.selectProfile(profile.uid) },
                onUpdate = {
                    updating = profile.uid
                    ZephyrState.updateProfile(profile.uid) { updating = null }
                },
                onDelete = { deleting = profile },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (adding) {
        AddProfileDialog(
            busy = busy,
            error = addError,
            onDismiss = { if (!busy) adding = false },
            onSubmit = { url ->
                busy = true
                addError = null
                ZephyrState.addProfile(url) { error ->
                    busy = false
                    if (error == null) adding = false else addError = error
                }
            },
        )
    }

    deleting?.let { profile ->
        ConfirmDialog(
            title = "删除订阅",
            message = "确定删除「${profile.name}」吗？本机保存的配置文件会一起删除，这个操作不能撤销。",
            confirmText = "删除",
            onDismiss = { deleting = null },
            onConfirm = {
                ZephyrState.deleteProfile(profile.uid)
                deleting = null
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    current: Boolean,
    updating: Boolean,
    onUse: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val used = profile.upload + profile.download
    val ratio = percentOf(used, profile.total)

    ZCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Z.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (current) {
                        Spacer(Modifier.width(8.dp))
                        Tag("使用中", tone = Z.blueDark)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    profile.url,
                    style = MonoSmall,
                    color = Z.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HairLine()

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCell("已用流量", formatBytes(used), Modifier.weight(1f))
            CellDivider()
            MetricCell(
                "总量",
                if (profile.total > 0) formatBytes(profile.total) else "未知",
                Modifier.weight(1f),
            )
            CellDivider()
            MetricCell("到期", formatExpiry(profile.expire), Modifier.weight(1f))
        }

        if (profile.total > 0) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Z.bluePale, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(5.dp)
                        .background(
                            when {
                                ratio > 0.95f -> Z.red
                                ratio > 0.8f -> Z.orange
                                else -> Z.blue
                            },
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        HairLine()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${formatUpdated(profile.updated)} · ${profile.nodeCount} 个节点",
                fontSize = 12.sp,
                color = Z.muted,
                modifier = Modifier.weight(1f),
            )
            if (!current) {
                ZButton("使用", onClick = onUse, small = true)
                Spacer(Modifier.width(7.dp))
            }
            ZButton(
                if (updating) "更新中" else "更新",
                onClick = onUpdate,
                icon = ZIcon.Refresh,
                small = true,
                enabled = !updating,
            )
            Spacer(Modifier.width(7.dp))
            ZButton("删除", onClick = onDelete, icon = ZIcon.Trash, danger = true, small = true)
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Z.muted)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, color = Z.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CellDivider() {
    Box(Modifier.width(1.dp).height(52.dp).background(Z.lineIn))
}
