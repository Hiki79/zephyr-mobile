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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
    private val dataLock = Mutex()
    private val commandLock = Mutex()
    private var session = 0L
    private val monitorFailures = mutableSetOf<String>()
    private val _uiVisible = MutableStateFlow(0)
    private var loaded = false

    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = Store(appContext)
        try {
            val saved = store.loadState()
            _settings.value = saved.settings
            _profiles.value = saved.profiles
            loaded = true
            store.recoveryNotice?.let { pushLog(it, LogLevel.WARN) }
        } catch (error: Exception) {
            _status.value = CoreStatus(stage = CoreStage.FAILED, lastError = error.message)
            pushLog(error.message ?: "配置读取失败", LogLevel.ERROR)
        }
        refreshLocalProxies()
        notifyTile()
    }

    fun api(): ClashApi = (_status.value.runtimeSettings ?: _settings.value).let { ClashApi(it.ctrlPort, it.secret) }

    private fun refreshLocalProxies() {
        if (_status.value.running || _status.value.stage == CoreStage.STARTING) return
        val uid = _settings.value.currentProfile
        scope.launch(Dispatchers.IO) {
            val preview = store.readProfileYaml(_profiles.value.find { it.uid == uid }?.configId ?: uid)?.let { yaml ->
                runCatching { ProfileConfig.parse(yaml) }.getOrElse {
                    pushLog("订阅预览失败：${it.message ?: it.javaClass.simpleName}", LogLevel.ERROR)
                    null
                }
            }
            if (_settings.value.currentProfile == uid && !_status.value.running && _status.value.stage != CoreStage.STARTING) {
                _proxies.value = preview?.proxies.orEmpty()
            }
        }
    }

    fun toast(message: String) {
        _toasts.tryEmit(message)
    }

    fun setUiVisible(visible: Boolean) {
        _uiVisible.update { count -> if (visible) count + 1 else (count - 1).coerceAtLeast(0) }
    }

    // ------------------------------------------------------------ settings

    private suspend fun persist(settings: Settings = _settings.value, profiles: List<Profile> = _profiles.value) {
        check(loaded) { "本地配置未成功读取，已保留原文件" }
        withContext(Dispatchers.IO) { store.saveState(settings, profiles) }
        _settings.value = settings
        _profiles.value = profiles
    }

    private suspend fun attempt(message: String, block: suspend () -> Unit) {
        try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val detail = "$message：${error.message ?: error.javaClass.simpleName}"
            pushLog(detail, LogLevel.ERROR)
            toast(detail)
        }
    }

    fun updateSettings(onSaved: () -> Unit = {}, transform: (Settings) -> Settings) {
        scope.launch {
            attempt("保存失败") {
                dataLock.withLock { persist(settings = transform(_settings.value)) }
                onSaved()
            }
        }
    }

    fun setMode(mode: String) {
        if (mode !in listOf("rule", "global", "direct")) return
        if (_status.value.stage == CoreStage.STARTING) {
            toast("连接完成后再切换模式")
            return
        }
        val expected = session
        scope.launch {
            commandLock.withLock command@{
                if (expected != session) return@command
                attempt("模式切换失败") {
                    dataLock.withLock data@{
                        if (_status.value.stage == CoreStage.STARTING) error("连接完成后再切换模式")
                        val previous = _settings.value.mode
                        val running = _status.value.running
                        val controller = api()
                        if (running && !controller.patchMode(mode)) error("控制接口拒绝切换")
                        if (expected != session) return@data
                        if (running) _status.update { it.copy(runtimeSettings = it.runtimeSettings?.copy(mode = mode)) }
                        try { persist(settings = _settings.value.copy(mode = mode)) }
                        catch (error: Exception) {
                            if (running && expected == session && controller.patchMode(previous)) {
                                _status.update { it.copy(runtimeSettings = it.runtimeSettings?.copy(mode = previous)) }
                            }
                            throw error
                        }
                    }
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
        if (!loaded || dataLock.isLocked) { toast("请等待配置保存完成"); return }
        session++
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
            .onFailure { toast("停止失败：${it.message}") }
    }

    /** Called by the service once the core reports itself healthy. */
    fun onCoreStarted(settings: Settings) {
        _status.value = CoreStatus(
            stage = CoreStage.RUNNING,
            startedAt = System.currentTimeMillis() / 1000,
            runtimeSettings = settings,
            profileName = _profiles.value.find { it.uid == settings.currentProfile }?.name,
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
        session++
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

    private fun monitor(name: String, failed: Boolean) {
        if (failed) monitorFailures += name else monitorFailures -= name
        _status.update { it.copy(monitoringError = monitorFailures.takeIf { it.isNotEmpty() }
            ?.joinToString("、", postfix = "暂不可用，正在重试")) }
    }

    /** Start a stream only while a visible UI actually observes its data. */
    private fun <T> observeStream(
        name: String, subscribers: StateFlow<Int>, stream: () -> Flow<T>, consume: (T) -> Unit,
    ): Job = scope.launch {
        subscribers.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
            if (!visible) { monitor(name, false); return@collectLatest }
            var backoff = 1_000L
            while (currentCoroutineContext().isActive) {
                try {
                    stream().collect { value -> monitor(name, false); backoff = 1_000L; consume(value) }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { }
                monitor(name, true)
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun pollVisible(name: String, subscribers: StateFlow<Int>, interval: Long, read: suspend () -> Unit): Job = scope.launch {
        subscribers.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
            if (!visible) { monitor(name, false); return@collectLatest }
            while (currentCoroutineContext().isActive) {
                try { read(); monitor(name, false) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { monitor(name, true) }
                delay(interval)
            }
        }
    }

    private fun startPolling() {
        stopPolling()
        val controller = api()
        pollJobs += observeStream("流量", _uiVisible, controller::trafficFlow) {
            _traffic.value = (_traffic.value + it).takeLast(TRAFFIC_HISTORY)
        }
        pollJobs += observeStream("内存", _uiVisible, controller::memoryFlow) { _memory.value = it.inUse }
        pollJobs += observeStream("日志", _uiVisible, { controller.logFlow(_status.value.runtimeSettings?.logLevel ?: "info") }) {
            pushLog(it.payload, when (it.type.lowercase()) {
                "error" -> LogLevel.ERROR
                "warning", "warn" -> LogLevel.WARN
                else -> LogLevel.INFO
            })
        }
        pollJobs += pollVisible("节点", _uiVisible, 6_000) { _proxies.value = controller.proxies() }
        pollJobs += pollVisible("连接", _uiVisible, 3_000) { _connections.value = controller.connections() }
        pollJobs += pollVisible("规则", _uiVisible, 30_000) { _rules.value = controller.rules() }
        // Lightweight health check remains active when every screen is closed.
        pollJobs += scope.launch {
            while (isActive) {
                val version = controller.version()
                monitor("控制接口", version == null)
                if (version != null) _status.update { it.copy(coreVersion = version) }
                delay(30_000)
            }
        }
    }

    private fun stopPolling() {
        pollJobs.forEach(Job::cancel)
        pollJobs.clear()
        monitorFailures.clear()
    }

    fun refreshProxies() {
        if (!_status.value.running) { refreshLocalProxies(); return }
        val expected = session
        val controller = api()
        scope.launch { attempt("刷新节点失败") {
            val result = controller.proxies()
            if (session == expected && _status.value.running) _proxies.value = result
        } }
    }

    fun refreshRules() {
        if (!_status.value.running) return
        val expected = session
        val controller = api()
        scope.launch { attempt("刷新规则失败") {
            val result = controller.rules()
            if (session == expected && _status.value.running) _rules.value = result
        } }
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
        if (!_status.value.running) { toast("连接后可切换节点"); return }
        val expected = session
        val controller = api()
        scope.launch {
            commandLock.withLock command@{
                if (expected != session || !_status.value.running) return@command
                attempt("节点切换失败") {
                    check(controller.selectNode(group, node)) { "控制接口拒绝切换" }
                    val result = controller.proxies()
                    if (expected == session && _status.value.running) _proxies.value = result
                }
            }
        }
    }

    fun testGroup(group: String, onDone: () -> Unit = {}) {
        val expected = session
        val controller = api()
        scope.launch {
            try { attempt("测速失败") {
                check(_status.value.running) { "请先连接" }
                val result = controller.groupDelay(group, _settings.value.testUrl)
                if (result.isEmpty()) toast("未测得可用延迟，请检查网络或节点")
                val proxies = controller.proxies()
                if (expected == session && _status.value.running) _proxies.value = proxies
            } } finally { onDone() }
        }
    }

    fun addProfile(url: String, onResult: (String?) -> Unit) {
        scope.launch {
            val uid = Store.newUid()
            try {
                val download = Subscription.fetch(url, uid)
                dataLock.withLock {
                    withContext(Dispatchers.IO) { store.writeProfileYaml(uid, download.yaml) }
                    val next = if (_settings.value.currentProfile == null) _settings.value.copy(currentProfile = uid) else _settings.value
                    persist(next, _profiles.value + download.profile)
                }
                refreshLocalProxies()
                onResult(null)
                toast("${download.profile.name} 已添加")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                withContext(Dispatchers.IO) { store.deleteProfileYaml(uid) }
                onResult(error.message ?: "添加失败")
            }
        }
    }

    private val updatingProfiles = mutableSetOf<String>()

    fun updateProfile(uid: String, onDone: () -> Unit = {}) {
        val existing = _profiles.value.find { it.uid == uid } ?: return onDone()
        if (!updatingProfiles.add(uid)) return onDone()
        scope.launch {
            try { attempt("更新失败") {
                val download = Subscription.fetch(existing.url, uid, keepName = existing.name)
                dataLock.withLock {
                    check(_profiles.value.any { it.uid == uid }) { "订阅已删除，更新已取消" }
                    // New revisions get their own immutable YAML; commit its ID with the index.
                    val revision = Store.newUid()
                    withContext(Dispatchers.IO) { store.writeProfileYaml(revision, download.yaml) }
                    try {
                        val fresh = download.profile.copy(configId = revision)
                        persist(profiles = _profiles.value.map { if (it.uid == uid) fresh else it })
                    } catch (error: Exception) {
                        withContext(Dispatchers.IO) { store.deleteProfileYaml(revision) }
                        throw error
                    }
                }
                toast("${download.profile.name} 已更新" + if (_status.value.runtimeSettings?.currentProfile == uid) "，重新连接后生效" else "")
                refreshLocalProxies()
            } } finally { updatingProfiles.remove(uid); onDone() }
        }
    }

    fun selectProfile(uid: String) {
        scope.launch { attempt("切换订阅失败") {
            dataLock.withLock {
                check(_profiles.value.any { it.uid == uid }) { "订阅不存在" }
                persist(settings = _settings.value.copy(currentProfile = uid))
            }
            refreshLocalProxies()
            notifyTile()
            if (_status.value.running) toast("已设为下次连接订阅，当前连接保持不变")
        } }
    }

    fun deleteProfile(uid: String) {
        scope.launch { attempt("删除失败") {
            dataLock.withLock {
                val items = _profiles.value.filterNot { it.uid == uid }
                val next = if (_settings.value.currentProfile == uid) _settings.value.copy(currentProfile = items.firstOrNull()?.uid) else _settings.value
                persist(next, items)
                // Revision files are retained for last-good-state recovery. They stay private.
            }
            refreshLocalProxies()
            notifyTile()
            toast("订阅已删除" + if (_status.value.runtimeSettings?.currentProfile == uid) "，当前会话将在断开时结束" else "")
        } }
    }

    fun closeConnection(id: String) = closeConnections(id)
    fun closeAllConnections() = closeConnections(null)
    private fun closeConnections(id: String?) {
        if (!_status.value.running) return
        val expected = session
        val controller = api()
        scope.launch { attempt("关闭连接失败") {
            check(if (id == null) controller.closeAllConnections() else controller.closeConnection(id))
            val result = controller.connections()
            if (session == expected && _status.value.running) _connections.value = result
        } }
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
