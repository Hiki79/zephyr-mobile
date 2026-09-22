package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.label
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

@Composable
fun ConnectionsScreen(onBack: () -> Unit) {
    val data by ZephyrState.connections.collectAsStateWithLifecycle()
    val status by ZephyrState.status.collectAsStateWithLifecycle()
    val list = data.connections.orEmpty().sortedByDescending { it.download + it.upload }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(
                kicker = "04 / CONNECTIONS",
                title = "连接",
                subtitle = "${list.size} 条活动连接 · 累计下载 ${formatBytes(data.downloadTotal)}",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZButton("返回", onClick = onBack, small = true)
                        Spacer(Modifier.width(7.dp))
                        ZButton(
                            "全部关闭",
                            onClick = ZephyrState::closeAllConnections,
                            danger = true,
                            small = true,
                            enabled = list.isNotEmpty(),
                        )
                    }
                },
            )
        }

        if (list.isEmpty()) {
            item {
                ZCard {
                    EmptyState(
                        icon = ZIcon.Link,
                        title = if (status.running) "暂时没有连接" else "内核未运行",
                        description = if (status.running) {
                            "有应用开始联网时，连接会立刻出现在这里。"
                        } else {
                            "连接之后，每一条经过内核的流量都会列在这里。"
                        },
                    )
                }
            }
        }

        items(list, key = { it.id }) { connection ->
            ZCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            connection.label(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Z.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            connection.chains.reversed().joinToString(" → ").ifBlank { "--" },
                            fontSize = 11.5.sp,
                            color = Z.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ZButton(
                        "关闭",
                        onClick = { ZephyrState.closeConnection(connection.id) },
                        small = true,
                    )
                }
                HairLine()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tag(connection.metadata.network.ifBlank { "tcp" })
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${connection.rule}${if (connection.rulePayload.isNotBlank()) "(${connection.rulePayload})" else ""}",
                        style = MonoSmall.copy(fontSize = 10.5.sp),
                        color = Z.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "↓ ${formatBytes(connection.download)}  ↑ ${formatBytes(connection.upload)}",
                        style = MonoSmall.copy(fontSize = 10.5.sp),
                        color = Z.muted,
                        maxLines = 1,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
