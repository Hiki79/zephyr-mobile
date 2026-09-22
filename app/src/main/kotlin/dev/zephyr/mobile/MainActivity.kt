package dev.zephyr.mobile

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.zephyr.mobile.ui.HairLine
import dev.zephyr.mobile.ui.Z
import dev.zephyr.mobile.ui.ZIcon
import dev.zephyr.mobile.ui.ZephyrTheme
import dev.zephyr.mobile.ui.screens.ConnectionsScreen
import dev.zephyr.mobile.ui.screens.LogsScreen
import dev.zephyr.mobile.ui.screens.OverviewScreen
import dev.zephyr.mobile.ui.screens.ProfilesScreen
import dev.zephyr.mobile.ui.screens.ProxiesScreen
import dev.zephyr.mobile.ui.screens.RulesScreen
import dev.zephyr.mobile.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        ZephyrState.setUiVisible(true)
    }

    override fun onStop() {
        ZephyrState.setUiVisible(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZephyrState.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            ZephyrTheme { AppRoot() }
        }
    }
}

/** The five bottom tabs. Connections and rules open on top of whichever tab is active. */
private enum class Tab(val label: String, val icon: ImageVector) {
    OVERVIEW("总览", ZIcon.Activity),
    PROXIES("节点", ZIcon.Globe),
    PROFILES("订阅", ZIcon.Download),
    LOGS("日志", ZIcon.Terminal),
    SETTINGS("设置", ZIcon.Sliders),
}

private enum class Overlay { NONE, CONNECTIONS, RULES }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ZephyrState.start(context)
        } else {
            ZephyrState.toast("需要授权 VPN 才能连接")
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            ZephyrState.toast("没有通知权限，连接状态不会显示在通知栏")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun toggleVpn(on: Boolean) {
        if (!on) {
            ZephyrState.stop(context)
            return
        }
        val intent = ZephyrState.vpnPermissionIntent(context)
        if (intent != null) vpnPermission.launch(intent) else ZephyrState.start(context)
    }

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && tab != Tab.OVERVIEW) { tab = Tab.OVERVIEW }

    Box(Modifier.fillMaxSize().background(Z.paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f)) {
                when (overlay) {
                    Overlay.CONNECTIONS -> ConnectionsScreen(onBack = { overlay = Overlay.NONE })
                    Overlay.RULES -> RulesScreen(onBack = { overlay = Overlay.NONE })
                    Overlay.NONE -> when (tab) {
                        Tab.OVERVIEW -> OverviewScreen(
                            onNavigateProxies = { tab = Tab.PROXIES },
                            onNavigateProfiles = { tab = Tab.PROFILES },
                            onNavigateConnections = { overlay = Overlay.CONNECTIONS },
                            onToggleVpn = ::toggleVpn,
                        )

                        Tab.PROXIES -> ProxiesScreen(onNavigateProfiles = { tab = Tab.PROFILES }, onConnect = { toggleVpn(true) })
                        Tab.PROFILES -> ProfilesScreen()
                        Tab.LOGS -> LogsScreen()
                        Tab.SETTINGS -> SettingsScreen(
                            onOpenLogs = { tab = Tab.LOGS },
                            onOpenConnections = { overlay = Overlay.CONNECTIONS },
                            onOpenRules = { overlay = Overlay.RULES },
                        )
                    }
                }
            }

            BottomNav(
                current = tab,
                onSelect = {
                    overlay = Overlay.NONE
                    tab = it
                },
            )
        }

        dev.zephyr.mobile.ui.MessageHost(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 76.dp))
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Z.card)) {
        HairLine(Z.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { entry ->
                val active = entry == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = active, role = Role.Tab) { onSelect(entry) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .background(
                                if (active) Z.bluePale else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            entry.icon,
                            entry.label,
                            tint = if (active) Z.blue else Z.muted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        entry.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Z.blueDark else Z.muted,
                    )
                }
            }
        }
    }
}
