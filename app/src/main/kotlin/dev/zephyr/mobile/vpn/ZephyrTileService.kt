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
            ZephyrState.status.collectLatest {
                updateTileState()
            }
        }
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
                    tile.subtitle = currentProfile?.name ?: getString(R.string.notif_connected)
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
