package dev.zephyr.mobile.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.zephyr.mobile.MainActivity
import dev.zephyr.mobile.R
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.data.CoreStage
import dev.zephyr.mobile.finalExit
import dev.zephyr.mobile.selectGroups
import dev.zephyr.mobile.ui.formatMultiplier
import dev.zephyr.mobile.ui.multiplierOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Quick Settings tile allowing the user to toggle the VPN directly from the
 * notification shade or lock screen.
 */
class ZephyrTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        ZephyrState.init(applicationContext)

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        observeJob = s.launch {
            ZephyrState.status.combine(ZephyrState.proxies) { status, _ -> status }.collectLatest {
                updateTileState()
            }
        }
        // Nothing polls while the app is closed; fetch once so the exit shown is current.
        if (ZephyrState.status.value.running) ZephyrState.refreshProxies()
        updateTileState()
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        ZephyrState.init(applicationContext)

        val status = ZephyrState.status.value
        if (status.running || status.stage == CoreStage.STARTING) {
            ZephyrState.stop(this)
            return
        }

        val permissionIntent = ZephyrState.vpnPermissionIntent(this)
        val hasProfile = ZephyrState.profiles.value.any { it.uid == ZephyrState.settings.value.currentProfile }

        if (permissionIntent != null || !hasProfile) {
            val appIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // The consent dialog needs an activity; the app asks for it on arrival.
                if (hasProfile) putExtra(MainActivity.EXTRA_CONNECT, true)
            }
            val launchAction = {
                openApp(appIntent)
                if (!hasProfile) {
                    ZephyrState.toast("请先选择或添加订阅")
                }
            }
            if (isLocked) {
                unlockAndRun(launchAction)
            } else {
                launchAction()
            }
            return
        }

        ZephyrState.start(this)
    }

    private fun openApp(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val status = ZephyrState.status.value
        val profiles = ZephyrState.profiles.value
        val settings = ZephyrState.settings.value
        val currentProfile = profiles.find { it.uid == settings.currentProfile }

        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_zephyr)
        tile.label = getString(R.string.app_name)

        when (status.stage) {
            CoreStage.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = exitSubtitle() ?: status.profileName ?: getString(R.string.notif_connected)
                }
            }
            CoreStage.STARTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.notif_connecting)
                }
            }
            CoreStage.FAILED -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = status.lastError?.take(16) ?: "连接失败"
                }
            }
            CoreStage.STOPPED -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = currentProfile?.name ?: "未连接"
                }
            }
        }
        tile.updateTile()
    }

    /** The node traffic finally leaves through, with its multiplier when it is costly. */
    private fun exitSubtitle(): String? {
        val status = ZephyrState.status.value
        val mode = status.runtimeSettings?.mode ?: ZephyrState.settings.value.mode
        if (mode == "direct") return "直连模式"
        val proxies = ZephyrState.proxies.value
        val exit = finalExit(proxies, selectGroups(proxies, mode).firstOrNull()?.now) ?: return null
        val multiplier = multiplierOf(exit.name)?.takeIf { it >= 3 }
        return (multiplier?.let { formatMultiplier(it) + " " } ?: "") + exit.name.take(18)
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, ZephyrTileService::class.java),
                )
            }
        }
    }
}
