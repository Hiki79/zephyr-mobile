package dev.zephyr.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zephyr.mobile.ZephyrState

@Composable
fun SessionNotice(modifier: Modifier = Modifier) {
    val status by ZephyrState.status.collectAsStateWithLifecycle()
    val settings by ZephyrState.settings.collectAsStateWithLifecycle()
    val profiles by ZephyrState.profiles.collectAsStateWithLifecycle()
    val lines = buildList {
        status.monitoringError?.let { add(it) }
        if (status.running && status.runtimeSettings?.currentProfile != settings.currentProfile) {
            val next = profiles.find { it.uid == settings.currentProfile }?.name ?: "未选择"
            add("运行中：${status.profileName ?: "当前订阅"} · 下次连接：$next")
        }
    }
    if (lines.isNotEmpty()) Text(lines.joinToString("\n"), modifier = modifier,
        color = Z.orange, fontSize = 12.sp, lineHeight = 17.sp)
}
