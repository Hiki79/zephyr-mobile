package dev.zephyr.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.LogLevel
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZSwitch

@Composable
fun LogsScreen() {
    val logs by ZephyrState.logs.collectAsStateWithLifecycle()
    val status by ZephyrState.status.collectAsStateWithLifecycle()

    var follow by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.lastOrNull()?.id, follow) {
        if (follow && logs.isNotEmpty()) {
            listState.scrollToItem(logs.lastIndex)
        }
    }

    // Dragging up to read pauses following; letting go at the bottom resumes it.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> follow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> if (!listState.canScrollForward) follow = true
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Z.gutter)) {
        PageHeader(
            kicker = "04 / LOGS",
            title = "日志",
            subtitle = "内核实时输出 · 只保留最近 600 行，不写入文件",
            trailing = {
                ZButton("清空", onClick = ZephyrState::clearLogs, icon = ZIcon.Trash, small = true)
            },
        )

        ZCard(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${logs.size} 行",
                    fontSize = 12.sp,
                    color = Z.muted,
                    modifier = Modifier.weight(1f),
                )
                Text("自动滚动", fontSize = 12.sp, color = Z.muted)
                Spacer(Modifier.width(7.dp))
                ZSwitch(checked = follow, onChange = { follow = it })
            }
            HairLine()

            if (logs.isEmpty()) {
                EmptyState(
                    icon = ZIcon.Terminal,
                    title = if (status.running) "还没有日志" else "内核未运行",
                    description = if (status.running) {
                        "内核安静的时候这里就是空的，有事件会立刻出现。"
                    } else {
                        "连接之后，内核的路由决策和错误会显示在这里。"
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(logs, key = { it.id }) { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when (line.level) {
                                        LogLevel.ERROR -> Z.red.copy(alpha = 0.05f)
                                        LogLevel.WARN -> Z.orange.copy(alpha = 0.05f)
                                        LogLevel.INFO -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 3.dp),
                        ) {
                            Text(
                                line.time,
                                style = MonoSmall.copy(fontSize = 10.5.sp),
                                color = Z.faint,
                                modifier = Modifier.width(58.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line.text,
                                style = MonoSmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
                                color = when (line.level) {
                                    LogLevel.ERROR -> Z.red
                                    LogLevel.WARN -> Z.orange
                                    LogLevel.INFO -> Z.ink
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
