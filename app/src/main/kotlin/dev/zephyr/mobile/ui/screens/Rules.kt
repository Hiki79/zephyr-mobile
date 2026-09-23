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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.ui.EmptyState
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Tag
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZTextField

/** The core's rule table, in the order it evaluates them. Read-only, as on desktop. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val rules by ZephyrState.rules.collectAsStateWithLifecycle()
    val status by ZephyrState.status.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(status.running) {
        if (status.running) ZephyrState.refreshRules()
    }

    val shown = remember(rules, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) rules
        else rules.filter { rule ->
            rule.payload.lowercase().contains(needle) ||
                rule.proxy.lowercase().contains(needle) ||
                rule.type.lowercase().contains(needle)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                kicker = "RULES",
                title = "规则",
                subtitle = "${rules.size} 条 · 自上而下匹配，命中即停",
                trailing = { ZButton("返回", onClick = onBack, small = true) },
            )
        }

        if (rules.isEmpty()) {
            item {
                ZCard {
                    EmptyState(
                        icon = ZIcon.Shield,
                        title = if (status.running) "内核还没有返回规则" else "内核未运行",
                        description = if (status.running) {
                            "稍等片刻，规则表加载后会显示在这里。"
                        } else {
                            "连接之后，订阅里的分流规则会按顺序列在这里。"
                        },
                    )
                }
            }
            return@LazyColumn
        }

        item {
            ZCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "查找规则，例如 google、DIRECT、GEOSITE",
                        leading = ZIcon.Search,
                        rounded = true,
                        clearable = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                HairLine()
                if (shown.isEmpty()) {
                    Text(
                        "没有匹配的规则。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        itemsIndexed(shown, key = { index, rule -> "$index-${rule.type}-${rule.payload}" }) { index, rule ->
            ZCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%03d".format(index + 1),
                        style = MonoSmall.copy(fontSize = 10.5.sp),
                        color = Z.muted,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            rule.payload.ifBlank { "（无条件）" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Z.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(rule.type)
                            if (rule.size >= 0) {
                                Spacer(Modifier.width(6.dp))
                                Text("${rule.size} 条", fontSize = 11.sp, color = Z.muted)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        rule.proxy,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (rule.proxy) {
                            "DIRECT" -> Z.green
                            "REJECT", "REJECT-DROP" -> Z.red
                            else -> Z.blueDark
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
