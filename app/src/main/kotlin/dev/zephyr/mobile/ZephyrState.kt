package dev.zephyr.mobile

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.zephyr.mobile.core.ClashApi
import dev.zephyr.mobile.core.Subscription
import dev.zephyr.mobile.core.ProfileConfig
import dev.zephyr.mobile.data.Connection
import dev.zephyr.mobile.data.ConnectionsResponse
import dev.zephyr.mobile.data.CoreStage
import dev.zephyr.mobile.data.CoreStatus
import dev.zephyr.mobile.data.LogLevel
import dev.zephyr.mobile.data.LogLine
import dev.zephyr.mobile.data.Profile
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.data.Rule
import dev.zephyr.mobile.data.Settings
import dev.zephyr.mobile.data.Store
import dev.zephyr.mobile.data.TrafficSample
import dev.zephyr.mobile.vpn.ZephyrVpnService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One place holding everything both the UI and the VPN service need, which is
 * possible because they share a process. It plays the part the Rust backend
 * plays in the Windows build.
 */
object ZephyrState {

    private const val TRAFFIC_HISTORY = 60
    private const val MAX_LOGS = 600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logSeq = AtomicLong(0)

    lateinit var store: Store
        private set

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _status = MutableStateFlow(CoreStatus())
    val status: StateFlow<CoreStatus> = _status.asStateFlow()

    private val _proxies = MutableStateFlow<Map<String, ProxyItem>>(emptyMap())
    val proxies: StateFlow<Map<String, ProxyItem>> = _proxies.asStateFlow()

    private val _traffic = MutableStateFlow(List(TRAFFIC_HISTORY) { TrafficSample() })
    val traffic: StateFlow<List<TrafficSample>> = _traffic.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    private val _connections = MutableStateFlow(ConnectionsResponse())
    val connections: StateFlow<ConnectionsResponse> = _connections.asStateFlow()

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _memory = MutableStateFlow(0L)
    val memory: StateFlow<Long> = _memory.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    // Touched from the main thread and from the service's IO coroutine, so
    // every access goes through the lock below.
    private val pollJobs = mutableListOf<Job>()
    @Volatile private var runtimeSettings: Settings? = null

    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = Store(appContext)
        _settings.value = store.loadSettings()
        _profiles.value = store.loadProfiles()
        refreshLocalProxies()
        notifyTile()
    }

    fun api(): ClashApi = (runtimeSettings ?: _settings.value).let { ClashApi(it.ctrlPort, it.secret) }

    private fun refreshLocalProxies() {
        if (_status.value.running || _status.value.stage == CoreStage.STARTING) return
        val uid = _settings.value.currentProfile
        scope.launch(Dispatchers.IO) {
            val preview = store.readProfileYaml(uid)?.let { yaml ->
                runCatching { ProfileConfig.parse(yaml) }.getOrElse {
                    pushLog("订阅预览失败：${it.message ?: it.javaClass.simpleName}", LogLevel.ERROR)
                    null
                }
            }
            if (_settings.value.currentProfile == uid && !_status.value.running && _status.value.stage != CoreStage.STARTING) {
                _proxies.value = preview?.proxies.orEmpty()
                if (preview != null) {
                    _profiles.update { items -> items.map { if (it.uid == uid) it.copy(nodeCount = preview.nodeCount) else it } }
                }
            }
        }
    }

    fun toast(message: String) {
        _toasts.tryEmit(message)
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        store.saveSettings(next)
    }

    /**
     * Mode is the one setting the core can change while running, so it is
     * pushed over the API instead of forcing a reconnect.
     */
    fun setMode(mode: String) {
        val previous = _settings.value.mode
        updateSettings { it.copy(mode = mode) }
        if (_status.value.running) {
            scope.launch {
                if (!api().patchMode(mode)) {
                    if (_settings.value.mode == mode) updateSettings { it.copy(mode = previous) }
                    toast("模式切换失败，请重试")
                }
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    /**
     * Returns the intent the system wants shown before a VPN may start, or null
     * when the user has already granted it.
     */
    fun vpnPermissionIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        if (_status.value.running || _status.value.stage == CoreStage.STARTING) return
        if (_profiles.value.none { it.uid == _settings.value.currentProfile }) {
            toast("请先选择一个订阅")
            return
        }
        _status.value = CoreStatus(stage = CoreStage.STARTING)
        notifyTile()
        val intent = Intent(context, ZephyrVpnService::class.java)
            .setAction(ZephyrVpnService.ACTION_START)
        runCatching { context.startForegroundService(intent) }
            .onFailure { onCoreFailed("无法启动 VPN 服务：${it.message ?: it.javaClass.simpleName}") }
    }

    fun stop(context: Context) {
        val intent = Intent(context, ZephyrVpnService::class.java)
            .setAction(ZephyrVpnService.ACTION_STOP)
        runCatching { context.startService(intent) }
    }

    /** Called by the service once the core reports itself healthy. */
    fun onCoreStarted(settings: Settings) {
        runtimeSettings = settings
        _status.value = CoreStatus(
            stage = CoreStage.RUNNING,
            startedAt = System.currentTimeMillis() / 1000,
        )
        notifyTile()
        startPolling()
    }

    fun onCoreFailed(message: String) {
        stopPolling()
        _status.value = CoreStatus(stage = CoreStage.FAILED, lastError = message)
        notifyTile()
        pushLog(message, LogLevel.ERROR)
        toast(message)
    }

    fun onCoreStopped() {
        stopPolling()
        runtimeSettings = null
        if (_status.value.stage != CoreStage.FAILED) {
            _status.value = CoreStatus(stage = CoreStage.STOPPED)
        }
        notifyTile()
        _connections.value = ConnectionsResponse()
        _rules.value = emptyList()
        _memory.value = 0
        _traffic.value = List(TRAFFIC_HISTORY) { TrafficSample() }
        refreshLocalProxies()
    }

    private fun notifyTile() {
        if (::appContext.isInitialized) {
            dev.zephyr.mobile.vpn.ZephyrTileService.requestUpdate(appContext)
        }
    }

    // ------------------------------------------------------------ polling

    private fun startPolling() {
        stopPolling()
        val api = api()
        val jobs = mutableListOf<Job>()

        jobs += scope.launch {
            api.trafficFlow()
                .catch { }
                .collect { sample ->
                    _traffic.value = (_traffic.value + sample).takeLast(TRAFFIC_HISTORY)
                }
        }

        jobs += scope.launch {
            api.memoryFlow().catch { }.collect { _memory.value = it.inUse }
        }

        jobs += scope.launch {
            api.logFlow(_settings.value.logLevel)
                .catch { }
                .collect { line ->
                    val level = when (line.type.lowercase()) {
                        "error" -> LogLevel.ERROR
                        "warning", "warn" -> LogLevel.WARN
                        else -> LogLevel.INFO
                    }
                    pushLog(line.payload, level)
                }
        }

        // Version confirms the core is really answering, not just launched.
        jobs += scope.launch {
            repeat(20) {
                val version = api.version()
                if (version != null) {
                    _status.value = _status.value.copy(coreVersion = version)
                    return@launch
                }
                delay(300)
            }
        }

        jobs += scope.launch {
            while (isActive) {
                _proxies.value = api.proxies()
                delay(6_000)
            }
        }

        jobs += scope.launch {
            while (isActive) {
                _connections.value = api.connections()
                delay(3_000)
            }
        }

        jobs += scope.launch {
            _rules.value = api.rules()
        }

        synchronized(pollJobs) { pollJobs += jobs }
    }

    private fun stopPolling() {
        val stale = synchronized(pollJobs) {
            val copy = pollJobs.toList()
            pollJobs.clear()
            copy
        }
        stale.forEach(Job::cancel)
    }

    fun refreshProxies() {
        if (!_status.value.running) {
            refreshLocalProxies()
            return
        }
        scope.launch { _proxies.value = api().proxies() }
    }

    fun refreshRules() {
        scope.launch { _rules.value = api().rules() }
    }

    fun pushLog(text: String, level: LogLevel) {
        if (text.isBlank()) return
        val line = LogLine(
            id = logSeq.incrementAndGet(),
            time = synchronized(timeFormat) { timeFormat.format(Date()) },
            text = text,
            level = level,
        )
        _logs.update { (it + line).takeLast(MAX_LOGS) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // ------------------------------------------------------------ proxies

    fun selectNode(group: String, node: String) {
        if (!_status.value.running) {
            toast("连接后可切换节点")
            return
        }
        // Show the change at once, then let the next refresh confirm it.
        _proxies.value = _proxies.value.toMutableMap().apply {
            this[group]?.let { this[group] = it.copy(now = node) }
        }
        scope.launch {
            if (!api().selectNode(group, node)) {
                toast("切换失败，节点可能已从订阅里移除")
            }
            _proxies.value = api().proxies()
        }
    }

    fun testGroup(group: String, onDone: () -> Unit = {}) {
        scope.launch {
            api().groupDelay(group, _settings.value.testUrl)
            _proxies.value = api().proxies()
            onDone()
        }
    }

    // ------------------------------------------------------------ profiles

    fun addProfile(url: String, onResult: (String?) -> Unit) {
        scope.launch {
            val uid = Store.newUid()
            runCatching { Subscription.fetch(url, uid, store) }
                .onSuccess { profile ->
                    _profiles.value = _profiles.value + profile
                    store.saveProfiles(_profiles.value)
                    if (_settings.value.currentProfile == null) {
                        updateSettings { it.copy(currentProfile = uid) }
                    }
                    refreshLocalProxies()
                    onResult(null)
                    toast("${profile.name} 已添加")
                }
                .onFailure { error ->
                    store.deleteProfileYaml(uid)
                    onResult(error.message ?: "下载失败")
                }
        }
    }

    fun updateProfile(uid: String, onDone: () -> Unit = {}) {
        val existing = _profiles.value.find { it.uid == uid } ?: return onDone()
        scope.launch {
            runCatching { Subscription.fetch(existing.url, uid, store, keepName = existing.name) }
                .onSuccess { fresh ->
                    _profiles.value = _profiles.value.map { if (it.uid == uid) fresh else it }
                    store.saveProfiles(_profiles.value)
                    toast("${fresh.name} 已更新")
                    refreshLocalProxies()
                    if (_settings.value.currentProfile == uid && _status.value.running) {
                        toast("重新连接后生效")
                    }
                }
                .onFailure { toast(it.message ?: "更新失败") }
            onDone()
        }
    }

    fun selectProfile(uid: String) {
        updateSettings { it.copy(currentProfile = uid) }
        refreshLocalProxies()
        notifyTile()
        if (_status.value.running) toast("重新连接后生效")
    }

    fun deleteProfile(uid: String) {
        _profiles.value = _profiles.value.filterNot { it.uid == uid }
        store.saveProfiles(_profiles.value)
        store.deleteProfileYaml(uid)
        if (_settings.value.currentProfile == uid) {
            updateSettings { it.copy(currentProfile = _profiles.value.firstOrNull()?.uid) }
        }
        refreshLocalProxies()
    }

    // ------------------------------------------------------------ connections

    fun closeConnection(id: String) {
        scope.launch {
            api().closeConnection(id)
            _connections.value = api().connections()
        }
    }

    fun closeAllConnections() {
        scope.launch {
            api().closeAllConnections()
            _connections.value = api().connections()
        }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
}

/** Groups in config order, with the plain nodes filtered out. */
fun selectGroups(proxies: Map<String, ProxyItem>, mode: String = "rule"): List<ProxyItem> {
    val global = proxies["GLOBAL"]?.takeIf { it.isGroup }
    if (mode == "global" && global != null) return listOf(global)
    val ordered = proxies["GLOBAL"]?.all
        ?.mapNotNull { proxies[it] }
        ?.filter { it.isGroup }
        .orEmpty()
    if (ordered.isNotEmpty()) return ordered
    return proxies.values.filter { it.isGroup && it.name != "GLOBAL" }
        .ifEmpty { listOfNotNull(global) }
}

/** A group shows the latency of whichever node it currently points at. */
fun groupLatency(proxies: Map<String, ProxyItem>, group: ProxyItem, depth: Int = 0): Int? {
    if (depth > 4) return null
    val target = proxies[group.now ?: return null] ?: return null
    return if (target.isGroup) groupLatency(proxies, target, depth + 1) else target.latency
}

/** `节点选择 → 香港 IEPL 01` for groups that point at another group. */
fun resolveChain(proxies: Map<String, ProxyItem>, name: String?, depth: Int = 0): String {
    if (name == null) return "--"
    val target = proxies[name]
    if (target == null || depth > 4) return name
    val next = target.now
    return if (target.isGroup && next != null) {
        "$name → ${resolveChain(proxies, next, depth + 1)}"
    } else {
        name
    }
}

fun Connection.label(): String =
    metadata.host.takeIf { it.isNotBlank() }
        ?: "${metadata.destinationIP}:${metadata.destinationPort}"
