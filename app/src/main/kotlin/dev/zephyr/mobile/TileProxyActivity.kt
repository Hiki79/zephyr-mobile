package dev.zephyr.mobile

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.data.CoreStage
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.ui.DelayDot
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZButton
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZTextField
import dev.zephyr.mobile.ui.ZephyrTheme
import dev.zephyr.mobile.ui.nodeMeta

/**
 * Opened by long-pressing the quick settings tile (the QS_TILE_PREFERENCES
 * intent filter in the manifest is what Android routes that gesture to): a
 * bottom sheet for switching nodes without opening the full app. When the
 * tunnel is down it shows the offline subscription preview plus a connect
 * button, mirroring the in-app proxies page.
 */
class TileProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZephyrState.init(applicationContext)
        setFinishOnTouchOutside(true)
        setContent {
            ZephyrTheme { TileProxySheet(onClose = ::finish) }
        }
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
        }
    }

    override fun onResume() {
        super.onResume()
        ZephyrState.refreshProxies()
    }
}

@Composable
private fun TileProxySheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val proxies by ZephyrState.proxies.collectAsState()
    val status by ZephyrState.status.collectAsState()
    val settings by ZephyrState.settings.collectAsState()
    val profiles by ZephyrState.profiles.collectAsState()
    val currentProfile = profiles.find { it.uid == settings.currentProfile }

    val groups = remember(proxies, settings.mode) { selectGroups(proxies, settings.mode) }
    var activeName by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
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

    val nodes = remember(active, query) {
        val needle = query.trim().lowercase()
        active?.all.orEmpty().filter { needle.isEmpty() || it.lowercase().contains(needle) }
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ZephyrState.start(context)
        } else {
            ZephyrState.toast("需要授权 VPN 才能连接")
        }
    }

    fun connect() {
        val intent = ZephyrState.vpnPermissionIntent(context)
        if (intent != null) vpnPermission.launch(intent) else ZephyrState.start(context)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Z.paper, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(34.dp).height(4.dp).background(Z.lineDark, RoundedCornerShape(2.dp)))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("切换节点", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
                Text(
                    when (status.stage) {
                        CoreStage.RUNNING -> "${currentProfile?.name ?: "当前订阅"} · ${groups.size} 个策略组"
                        CoreStage.STARTING -> "正在连接…"
                        CoreStage.FAILED -> status.lastError?.take(24) ?: "连接失败"
                        CoreStage.STOPPED -> "${currentProfile?.name ?: "未选择订阅"} · 未连接"
                    },
                    fontSize = 12.sp,
                    color = Z.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status.running && switchable && active != null) {
                ZButton(
                    if (testing) "测速中" else "测速",
                    onClick = {
                        val name = activeName ?: return@ZButton
                        testing = true
                        ZephyrState.testGroup(name) { testing = false }
                    },
                    icon = ZIcon.Zap,
                    small = true,
                    enabled = !testing,
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier
                    .size(30.dp)
                    .background(Z.card, RoundedCornerShape(999.dp))
                    .border(1.dp, Z.lineDark, RoundedCornerShape(999.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ZIcon.Close, "关闭", tint = Z.muted, modifier = Modifier.size(14.dp))
            }
        }

        when {
            groups.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (currentProfile == null) "还没有订阅" else "订阅里还没有可显示的节点",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Z.ink,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (currentProfile == null) "打开应用添加一个订阅后再来切换。"
                        else "打开应用刷新订阅，或连接后加载节点提供器。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                    )
                    Spacer(Modifier.height(13.dp))
                    ZButton("打开应用", primary = true, onClick = {
                        context.startActivity(
                            android.content.Intent(context, MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        onClose()
                    })
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    groups.forEach { group ->
                        SheetGroupChip(
                            group = group,
                            selected = group.name == activeName,
                            onClick = { activeName = group.name },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                ZTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "查找节点，例如 香港、IEPL、0.5x",
                    leading = ZIcon.Search,
                    rounded = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!status.running) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Z.card, RoundedCornerShape(Z.radiusSm))
                            .border(1.dp, Z.line, RoundedCornerShape(Z.radiusSm))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            status.lastError ?: "订阅预览 · 连接后可切换和测速",
                            fontSize = 12.sp,
                            color = Z.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        ZButton(
                            "连接代理",
                            onClick = ::connect,
                            primary = true,
                            small = true,
                            enabled = status.stage != CoreStage.STARTING,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HairLine()

                if (nodes.isEmpty()) {
                    Text(
                        if (!status.running && active?.all.isNullOrEmpty()) "此组的节点提供器会在连接后加载。"
                        else "没有匹配的节点，换个关键词试试。",
                        fontSize = 12.5.sp,
                        color = Z.muted,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(nodes, key = { it }) { node ->
                            SheetNodeRow(
                                name = node,
                                type = proxies[node]?.type ?: "--",
                                latency = proxies[node]?.latency,
                                selected = active?.now == node,
                                enabled = switchable && status.running,
                                onClick = { activeName?.let { ZephyrState.selectNode(it, node) } },
                            )
                        }
                    }
                    Text(
                        "显示 ${nodes.size} / ${active?.all?.size ?: 0} 个节点 · 点按即可切换",
                        fontSize = 11.5.sp,
                        color = Z.faint,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetGroupChip(group: ProxyItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(if (selected) Z.bluePale else Z.card, RoundedCornerShape(Z.radiusSm))
            .border(1.dp, if (selected) Z.blueLine else Z.line, RoundedCornerShape(Z.radiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            group.name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Z.blueDark else Z.ink,
            maxLines = 1,
        )
        Text(
            group.now ?: group.type,
            fontSize = 10.5.sp,
            color = Z.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(130.dp),
        )
    }
}

@Composable
private fun SheetNodeRow(
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
            .padding(vertical = 3.dp)
            .background(if (selected) Z.bluePale else Z.card, RoundedCornerShape(Z.radiusSm))
            .border(1.dp, if (selected) Z.blue else Z.line, RoundedCornerShape(Z.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                fontSize = 11.sp,
                color = Z.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        DelayDot(latency)
    }
}
