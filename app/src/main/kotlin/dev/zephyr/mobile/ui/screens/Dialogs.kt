package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.resolveChain
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.Tag
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZTextField

/** The desktop build's modal, rebuilt: white sheet, thin rules, no Material chrome. */
@Composable
fun ZDialog(
    title: String,
    onDismiss: () -> Unit,
    // A plain lambda would leave the footer without RowScope, and every caller
    // needs Modifier.weight to push its buttons to the right.
    footer: @Composable RowScope.() -> Unit,
    body: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Z.card, RoundedCornerShape(12.dp))
                .border(1.dp, Z.line, RoundedCornerShape(12.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Z.ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(26.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ZIcon.Close, "关闭", tint = Z.muted, modifier = Modifier.size(15.dp))
                }
            }
            body()
            HairLine()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                footer()
            }
        }
    }
}

/**
 * Chooses which groups the overview's routing card shows. Ticking nothing and
 * saving restores the default of the first few, which is why the empty list is
 * a meaningful value rather than an error.
 */
@Composable
fun GroupPickerDialog(
    groups: List<ProxyItem>,
    proxies: Map<String, ProxyItem>,
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val draft = remember { initial.toMutableStateList() }

    ZDialog(
        title = "选择要显示的分组",
        onDismiss = onDismiss,
        body = {
            Column {
                Text(
                    "勾选的分组会出现在总览的策略路由卡片里，顺序跟订阅一致。节点页始终显示全部分组。",
                    fontSize = 12.5.sp,
                    color = Z.muted,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(horizontal = 14.dp),
                ) {
                    items(groups, key = { it.name }) { group ->
                        val on = draft.contains(group.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (on) Z.bluePale else Color.Transparent,
                                    RoundedCornerShape(Z.radiusSm),
                                )
                                .clickable {
                                    if (on) draft.remove(group.name) else draft.add(group.name)
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckBox(on)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) Z.blueDark else Z.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    resolveChain(proxies, group.now),
                                    fontSize = 11.5.sp,
                                    color = Z.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Tag(group.type)
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        },
        footer = {
            Text(
                if (draft.isEmpty()) "未选＝显示默认" else "已选 ${draft.size} / ${groups.size}",
                fontSize = 12.sp,
                color = Z.muted,
                modifier = Modifier.weight(1f),
            )
            ZButton("恢复默认", onClick = { onSave(emptyList()) }, small = true)
            Spacer(Modifier.width(8.dp))
            ZButton("保存", onClick = { onSave(draft.toList()) }, primary = true, small = true)
        },
    )
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .background(if (checked) Z.blue else Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, if (checked) Z.blueDark else Z.lineDark, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(ZIcon.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** Adding a subscription: one field, and the error the server gave back. */
@Composable
fun AddProfileDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    ZDialog(
        title = "添加订阅",
        onDismiss = onDismiss,
        body = {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("订阅地址", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Z.muted)
                Spacer(Modifier.height(6.dp))
                ZTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://...",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "机场给的 Clash / mihomo 订阅链接。地址只会发给这个服务器本身。",
                    fontSize = 12.sp,
                    color = if (error != null) Z.red else Z.muted,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(14.dp))
            }
        },
        footer = {
            Spacer(Modifier.weight(1f))
            ZButton("取消", onClick = onDismiss, small = true, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            ZButton(
                if (busy) "下载中" else "添加",
                onClick = { onSubmit(url.trim()) },
                primary = true,
                small = true,
                enabled = !busy && url.isNotBlank(),
            )
        },
    )
}

/** Deleting a subscription is the only destructive action in the app. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ZDialog(
        title = title,
        onDismiss = onDismiss,
        body = {
            Text(
                message,
                fontSize = 13.sp,
                color = Z.muted,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
            )
        },
        footer = {
            Spacer(Modifier.weight(1f))
            ZButton("取消", onClick = onDismiss, small = true)
            Spacer(Modifier.width(8.dp))
            ZButton(confirmText, onClick = onConfirm, danger = true, small = true)
        },
    )
}
