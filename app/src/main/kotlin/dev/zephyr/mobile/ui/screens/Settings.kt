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
import androidx.compose.foundation.lazy.item
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.ui.CardHeader
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.MonoSmall
import dev.zephyr.mobile.ui.PageHeader
import dev.zephyr.mobile.ui.Segmented
import dev.zephyr.mobile.ui.SettingRow
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZCard
import dev.zephyr.mobile.ui.ZSwitch
import dev.zephyr.mobile.ui.ZTextField

@Composable
fun SettingsScreen(onOpenLogs: () -> Unit, onOpenConnections: () -> Unit) {
    val settings by ZephyrState.settings.collectAsState()
    val status by ZephyrState.status.collectAsState()

    var mixedPort by remember(settings.mixedPort) { mutableStateOf(settings.mixedPort.toString()) }
    var ctrlPort by remember(settings.ctrlPort) { mutableStateOf(settings.ctrlPort.toString()) }
    var testUrl by remember(settings.testUrl) { mutableStateOf(settings.testUrl) }

    fun commitPort(raw: String, isMixed: Boolean) {
        val port = raw.toIntOrNull()
        if (port == null || port < 1024 || port > 65535) {
            ZephyrState.toast("端口需要是 1024 到 65535 之间的整数")
            return
        }
        ZephyrState.updateSettings { if (isMixed) it.copy(mixedPort = port) else it.copy(ctrlPort = port) }
        if (status.running) ZephyrState.toast("重新连接后生效")
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Z.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                kicker = "06 / SETTINGS",
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
                            modifier = Modifier.width(96.dp),
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "保存端口",
                    description = "填好之后点一下应用",
                    onClick = {
                        commitPort(mixedPort, isMixed = true)
                        commitPort(ctrlPort, isMixed = false)
                    },
                    trailing = {
                        dev.zephyr.mobile.ui.ZButton(
                            "应用",
                            onClick = {
                                commitPort(mixedPort, isMixed = true)
                                commitPort(ctrlPort, isMixed = false)
                            },
                            small = true,
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "允许局域网连接",
                    description = "让同一个网络里的其他设备也能用这个代理",
                    trailing = {
                        ZSwitch(
                            checked = settings.allowLan,
                            onChange = { next ->
                                ZephyrState.updateSettings { it.copy(allowLan = next) }
                                if (status.running) ZephyrState.toast("重新连接后生效")
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
                                ZephyrState.updateSettings { it.copy(ipv6 = next) }
                                if (status.running) ZephyrState.toast("重新连接后生效")
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
                                ZephyrState.updateSettings { it.copy(unifiedDelay = next) }
                                if (status.running) ZephyrState.toast("重新连接后生效")
                            },
                        )
                    },
                )
            }
        }

        item {
            ZCard {
                CardHeader("测速与日志", description = "延迟测试地址与内核日志级别")
                SettingRow(
                    title = "测速地址",
                    description = "点测速时请求的地址",
                    trailing = {},
                )
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    ZTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        placeholder = "https://www.gstatic.com/generate_204",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    dev.zephyr.mobile.ui.ZButton(
                        "保存",
                        onClick = {
                            ZephyrState.updateSettings { it.copy(testUrl = testUrl.trim()) }
                            ZephyrState.toast("测速地址已保存")
                        },
                        small = true,
                    )
                }
                HairLine()
                SettingRow(
                    title = "日志级别",
                    description = "调试时调到 debug，平时 info 就够",
                    trailing = {
                        Segmented(
                            value = settings.logLevel,
                            options = listOf(
                                "warning" to "警告",
                                "info" to "信息",
                                "debug" to "调试",
                            ),
                            onChange = { next ->
                                ZephyrState.updateSettings { it.copy(logLevel = next) }
                                if (status.running) ZephyrState.toast("重新连接后生效")
                            },
                        )
                    },
                )
                HairLine()
                SettingRow(
                    title = "查看日志",
                    description = "内核实时输出",
                    onClick = onOpenLogs,
                    trailing = {
                        dev.zephyr.mobile.ui.ZButton("打开", onClick = onOpenLogs, small = true)
                    },
                )
                HairLine()
                SettingRow(
                    title = "查看连接",
                    description = "每一条经过内核的流量",
                    onClick = onOpenConnections,
                    trailing = {
                        dev.zephyr.mobile.ui.ZButton("打开", onClick = onOpenConnections, small = true)
                    },
                )
            }
        }

        item {
            ZCard {
                CardHeader("关于", description = "这个应用做了什么，没做什么")
                InfoRow("界面版本", "0.1.0")
                HairLine()
                InfoRow("内核", status.coreVersion ?: "未运行")
                HairLine()
                InfoRow("状态", if (status.running) "运行中" else "已停止")
                HairLine()
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "它只做三件事",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Z.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "下载你填的订阅地址、把流量交给 mihomo 内核、在本机读取内核状态显示出来。" +
                            "没有应用内更新，没有统计上报，没有云端服务，不读取通讯录、位置、相册和已安装应用列表。" +
                            "申请的权限一共五个：联网、读取网络状态、前台服务、特殊用途前台服务、发送通知。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "订阅和配置保存在应用私有目录，系统备份已关闭，其他应用读不到。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "内核 mihomo 来自 MetaCubeX，按 GPL-3.0 授权；界面是自己写的。",
                        style = MonoSmall,
                        color = Z.faint,
                        lineHeight = 17.sp,
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
