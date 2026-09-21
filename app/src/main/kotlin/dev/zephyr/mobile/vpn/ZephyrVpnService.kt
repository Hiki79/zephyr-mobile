package dev.zephyr.mobile.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.zephyr.mobile.MainActivity
import dev.zephyr.mobile.R
import dev.zephyr.mobile.ZephyrState
import dev.zephyr.mobile.core.ConfigBuilder
import dev.zephyr.mobile.data.LogLevel
import dev.zephyr.zephyrcore.Protector
import dev.zephyr.zephyrcore.Zephyrcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the tunnel. Android hands a file descriptor to whoever calls establish();
 * that descriptor is the one thing mihomo cannot obtain for itself, so this
 * service exists to open it, pass it down, and keep the process alive.
 *
 * Descriptor ownership is one-directional: once detached and handed to the
 * core it belongs to the core, which closes it on stop or on an early failure.
 * Nothing here closes it, so there is never a second owner.
 */
class ZephyrVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var coreUp = false

    @Volatile private var starting = false

    /**
     * Every socket the core opens comes through here. Without this the core's
     * connection to a proxy server would be routed into the tunnel the core is
     * itself serving, and nothing would ever leave the phone.
     */
    private val protector = object : Protector {
        override fun protect(fd: Int): Boolean = this@ZephyrVpnService.protect(fd)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        // A service started with startForegroundService must post its
        // notification within a few seconds or the system kills the process.
        goForeground(getString(R.string.notif_connecting), null)

        if (!coreUp && !starting) {
            starting = true
            scope.launch { bringUp() }
        }
        return START_STICKY
    }

    private suspend fun bringUp() {
        val settings = ZephyrState.settings.value
        val profileYaml = ZephyrState.store.readProfileYaml(settings.currentProfile)

        val configYaml = runCatching { ConfigBuilder.build(profileYaml, settings) }
            .getOrElse { error ->
                fail("配置无法解析：${error.message ?: "格式错误"}")
                return
            }

        val descriptor: ParcelFileDescriptor = runCatching { establishTunnel(settings.ipv6) }
            .getOrElse { error ->
                fail("建立隧道失败：${error.message ?: "系统拒绝"}")
                return
            }
            ?: run {
                fail("建立隧道失败，VPN 授权可能已被撤销")
                return
            }

        // From here the number belongs to the core; see the class comment.
        val fd = descriptor.detachFd()

        val gateway = buildString {
            append(TUN_V4_ADDRESS).append('/').append(TUN_V4_PREFIX)
            if (settings.ipv6) append(',').append(TUN_V6_ADDRESS).append('/').append(TUN_V6_PREFIX)
        }
        val dnsHijack = buildString {
            append(TUN_V4_DNS)
            if (settings.ipv6) append(',').append(TUN_V6_DNS)
        }

        val failure = runCatching {
            Zephyrcore.start(
                ZephyrState.store.runtimeDir.absolutePath,
                configYaml,
                fd,
                gateway,
                dnsHijack,
                protector,
            )
        }.exceptionOrNull()

        if (failure != null) {
            fail("内核启动失败：${failure.message ?: failure::class.java.simpleName}")
            return
        }

        coreUp = true
        starting = false
        withContext(Dispatchers.Main) {
            ZephyrState.onCoreStarted()
            ZephyrState.pushLog("内核已启动", LogLevel.INFO)
            goForeground(
                getString(R.string.notif_connected),
                ZephyrState.profiles.value
                    .find { it.uid == ZephyrState.settings.value.currentProfile }
                    ?.name,
            )
        }
    }

    private fun establishTunnel(ipv6: Boolean): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(TUN_V4_ADDRESS, TUN_V4_PREFIX)
            .addDnsServer(TUN_V4_DNS)
            // The core decides what is proxied and what goes direct, so the
            // whole address space is routed in and sorted out by the rules.
            .addRoute("0.0.0.0", 0)

        if (ipv6) {
            builder.addAddress(TUN_V6_ADDRESS, TUN_V6_PREFIX)
                .addDnsServer(TUN_V6_DNS)
                .addRoute("::", 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.setUnderlyingNetworks(null)

        return builder.establish()
    }

    private suspend fun fail(message: String) {
        starting = false
        withContext(Dispatchers.Main) { ZephyrState.onCoreFailed(message) }
        shutdown()
    }

    private fun shutdown() {
        starting = false
        coreUp = false
        // Stops the listeners, which is also what closes the TUN descriptor.
        runCatching { Zephyrcore.stop() }
        ZephyrState.onCoreStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The user revoked the VPN, or another app took it over. */
    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        coreUp = false
        runCatching { Zephyrcore.stop() }
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notification

    private fun goForeground(title: String, profileName: String?) {
        ensureChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ZephyrVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zephyr)
            .setContentTitle(title)
            .setContentText(profileName ?: getString(R.string.notif_subtitle))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
    }

    companion object {
        const val ACTION_START = "dev.zephyr.mobile.action.START"
        const val ACTION_STOP = "dev.zephyr.mobile.action.STOP"

        private const val CHANNEL_ID = "zephyr.status"
        private const val NOTIFICATION_ID = 1

        // A /30 gives the tunnel exactly two usable addresses: the portal and
        // the resolver DNS is hijacked to. Nothing else needs to live in there.
        private const val TUN_V4_ADDRESS = "172.19.0.1"
        private const val TUN_V4_PREFIX = 30
        private const val TUN_V4_DNS = "172.19.0.2"

        private const val TUN_V6_ADDRESS = "fdfe:dcba:9876::1"
        private const val TUN_V6_PREFIX = 126
        private const val TUN_V6_DNS = "fdfe:dcba:9876::2"

        // gvisor terminates TCP inside the process, so a large MTU costs
        // nothing on the wire and saves a great many syscalls.
        private const val MTU = 9000
    }
}
