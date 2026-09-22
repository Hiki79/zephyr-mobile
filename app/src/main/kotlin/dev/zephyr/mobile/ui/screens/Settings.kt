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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.BuildConfig
import dev.zephyr.mobile.ui.CardFoot
import dev.zephyr.mobile.ui.CardHeader
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Segmented
import dev.zephyr.mobile.ui.SettingRow
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZSwitch
import dev.zephyr.mobile.ui.ZTextField

private const val PORT_MIN = 1024
private const val PORT_MAX = 65535

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenRules: () -> Unit,
) {
    val settings by ZephyrState.settings.collectAsStateWithLifecycle()
    val status by ZephyrState.status.collectAsStateWithLifecycle()

    var mixedPort by remember(settings.mixedPort) { mutableStateOf(settings.mixedPort.toString()) }
    var ctrlPort by remember(settings.ctrlPort) { mutableStateOf(settings.ctrlPort.toString()) }
    var testUrl by remember(settings.testUrl) { mutableStateOf(settings.testUrl) }

    val portsDirty = mixedPort != settings.mixedPort.toString() || ctrlPort != settings.ctrlPort.toString()
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

    fun needsReconnect() {
        if (status.running) ZephyrState.toast("重新连接后生效")
    }

    fun applyPorts() {
        val mixed = mixedPort.toIntOrNull()
        val ctrl = ctrlPort.toIntOrNull()
        if (mixed == null || ctrl == null || mixed !in PORT_MIN..PORT_MAX || ctrl !in PORT_MIN..PORT_MAX) {
            ZephyrState.toast("端口需要是 $PORT_MIN 到 $PORT_MAX 之间的整数")
            return
        }
        if (mixed == ctrl) {
            ZephyrState.toast("两个端口不能相同")
            return
        }
        ZephyrState.updateSettings(
            transform = { it.copy(mixedPort = mixed, ctrlPort = ctrl) },
            onSaved = { ZephyrState.toast("端口已保存"); needsReconnect() },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                kicker = "07 / SETTINGS",
                title = "设置",
                subtitle = "端口与网络行为的改动需要重新连接，模式切换不用",
            )
        }

        item {
            ZCard {
                CardHeader("网络", description = "本地端口与出站行为")
                SettingRow(
                    title = "混合端口",
                    description = "HTTP 和 SOCKS5 共用的本地端口",
                    trailing = {
                        ZTextField(
                            value = mixedPort,
                            onValueChange = { mixedPort = it.filter(Char::isDigit).take(5) },
                            placeholder = "7890",
                            keyboardOptions = numeric,
                            modifier = Modifier.width(96.dp),
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "控制端口",
                    description = "界面读取内核状态用的本地接口",
                    trailing = {
                        ZTextField(
                            value = ctrlPort,
                            onValueChange = { ctrlPort = it.filter(Char::isDigit).take(5) },
                            placeholder = "9090",
                            keyboardOptions = numeric,
                            modifier = Modifier.width(96.dp),
                        )
                    },
                )
                CardFoot(if (portsDirty) "端口改了还没保存" else if (settings.allowLan) "混合端口允许局域网访问，控制端口仅限本机" else "两个端口只在本机 127.0.0.1 上监听") {
                    ZButton("保存端口", onClick = ::applyPorts, small = true, enabled = portsDirty)
                }
                HairLine()
                SettingRow(
                    title = "允许局域网连接",
                    description = "让同一个网络里的其他设备也能用这个代理",
                    trailing = {
                        ZSwitch(
                            checked = settings.allowLan,
                            onChange = { next ->
                                ZephyrState.updateSettings(transform = { it.copy(allowLan = next) }, onSaved = ::needsReconnect)
                            },
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "IPv6",
                    description = "隧道同时承载 IPv6 流量",
                    trailing = {
                        ZSwitch(
                            checked = settings.ipv6,
                            onChange = { next ->
                                ZephyrState.updateSettings(transform = { it.copy(ipv6 = next) }, onSaved = ::needsReconnect)
                            },
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "统一延迟",
                    description = "扣掉握手时间，让不同协议的延迟可比",
                    trailing = {
                        ZSwitch(
                            checked = settings.unifiedDelay,
                            onChange = { next ->
                                ZephyrState.updateSettings(transform = { it.copy(unifiedDelay = next) }, onSaved = ::needsReconnect)
                            },
                        )
                    },
                )
            }
        }

        item {
            ZCard {
                CardHeader("测速与日志", description = "延迟测试地址与内核日志级别")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("测速地址", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
                    Text("点测速时请求的地址，建议保留默认", fontSize = 12.sp, color = Z.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZTextField(
                            value = testUrl,
                            onValueChange = { testUrl = it },
                            placeholder = "https://www.gstatic.com/generate_204",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ZButton(
                            "保存",
                            onClick = {
                                val next = testUrl.trim()
                                if (!next.startsWith("https://") && !next.startsWith("http://")) {
                                    ZephyrState.toast("测速地址需要是一个网址")
                                    return@ZButton
                                }
                                ZephyrState.updateSettings(
                                    transform = { it.copy(testUrl = next) },
                                    onSaved = { ZephyrState.toast("测速地址已保存") },
                                )
                            },
                            small = true,
                            enabled = testUrl.trim() != settings.testUrl,
                        )
                    }
                }
                HairLine()
                SettingRow(
                    title = "日志级别",
                    description = "调试时调到 debug，平时 info 就够",
                    trailing = {
                        Segmented(
                            value = settings.logLevel,
                            options = listOf("warning" to "警告", "info" to "信息", "debug" to "调试"),
                            onChange = { next ->
                                ZephyrState.updateSettings(transform = { it.copy(logLevel = next) }, onSaved = ::needsReconnect)
                            },
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "查看日志",
                    description = "内核实时输出",
                    onClick = onOpenLogs,
                    trailing = { ZButton("打开", onClick = onOpenLogs, small = true) },
                )
                HairLine()
                SettingRow(
                    title = "查看连接",
                    description = "每一条经过内核的流量",
                    onClick = onOpenConnections,
                    trailing = { ZButton("打开", onClick = onOpenConnections, small = true) },
                )
                HairLine()
                SettingRow(
                    title = "查看规则",
                    description = "订阅里的分流规则，按匹配顺序",
                    onClick = onOpenRules,
                    trailing = { ZButton("打开", onClick = onOpenRules, small = true) },
                )
            }
        }

        item {
            ZCard {
                CardHeader("关于", description = "应用信息与开源许可")
                InfoRow("应用名称", "Zephyr")
                HairLine()
                InfoRow("界面版本", BuildConfig.VERSION_NAME)
                HairLine()
                InfoRow("内核版本", status.coreVersion ?: "未运行")
                HairLine()
                InfoRow("运行状态", if (status.running) "运行中" else "已停止")
                HairLine()
                InfoRow("开源许可", "GPL-3.0 (MetaCubeX)")
                HairLine()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Text(
                        "基于 Mihomo 内核的轻量 Android 代理客户端。所有规则匹配与流量转发均在本地处理，纯本地运行，无遥测、无数据上报。",
                        fontSize = 12.sp,
                        color = Z.muted,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = Z.muted, modifier = Modifier.weight(1f))
        Text(value, style = MonoSmall, color = Z.ink, maxLines = 1)
    }
}
