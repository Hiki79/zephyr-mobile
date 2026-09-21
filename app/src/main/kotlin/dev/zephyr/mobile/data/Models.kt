package dev.zephyr.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the user can change, persisted as settings.json in the app's
 * private directory. The field names mirror the Windows build so the two
 * versions stay conceptually identical.
 */
@Serializable
data class Settings(
    val mixedPort: Int = 7890,
    val ctrlPort: Int = 9090,
    /** Filled with a random value on first launch; guards the local REST API. */
    val secret: String = "",
    val mode: String = "rule",
    val ipv6: Boolean = false,
    val allowLan: Boolean = false,
    val unifiedDelay: Boolean = true,
    val logLevel: String = "info",
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val currentProfile: String? = null,
    /** Groups the overview's routing card shows; empty means the first few. */
    val pinnedGroups: List<String> = emptyList(),
    val autoUpdateHours: Int = 24,
)

/** One subscription. The traffic counters come from the provider's own header. */
@Serializable
data class Profile(
    val uid: String,
    val name: String,
    val url: String,
    val updated: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val home: String? = null,
    val nodeCount: Int = 0,
)

@Serializable
data class ProfileList(val items: List<Profile> = emptyList())

// ---------------------------------------------------------------- core API

@Serializable
data class DelayHistory(val time: String = "", val delay: Int = 0)

/** One entry of mihomo's /proxies map: either a node or a group. */
@Serializable
data class ProxyItem(
    val name: String = "",
    val type: String = "",
    val now: String? = null,
    val all: List<String>? = null,
    val udp: Boolean = false,
    val history: List<DelayHistory> = emptyList(),
) {
    val isGroup: Boolean get() = all != null

    /** Last measured latency; 0 means it timed out, null means never tested. */
    val latency: Int? get() = history.lastOrNull()?.delay
}

@Serializable
data class ProxiesResponse(val proxies: Map<String, ProxyItem> = emptyMap())

@Serializable
data class ConnectionMeta(
    val network: String = "",
    val type: String = "",
    val sourceIP: String = "",
    val destinationIP: String = "",
    val host: String = "",
    val destinationPort: String = "",
    val process: String? = null,
)

@Serializable
data class Connection(
    val id: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val start: String = "",
    val chains: List<String> = emptyList(),
    val rule: String = "",
    val rulePayload: String = "",
    val metadata: ConnectionMeta = ConnectionMeta(),
)

@Serializable
data class ConnectionsResponse(
    val downloadTotal: Long = 0,
    val uploadTotal: Long = 0,
    val connections: List<Connection>? = null,
)

@Serializable
data class Rule(
    val type: String = "",
    val payload: String = "",
    val proxy: String = "",
    val size: Int = -1,
)

@Serializable
data class RulesResponse(val rules: List<Rule> = emptyList())

@Serializable
data class TrafficSample(val up: Long = 0, val down: Long = 0)

@Serializable
data class MemorySample(@SerialName("inuse") val inUse: Long = 0)

@Serializable
data class CoreLogLine(val type: String = "info", val payload: String = "")

// ---------------------------------------------------------------- UI state

enum class LogLevel { INFO, WARN, ERROR }

data class LogLine(
    val id: Long,
    val time: String,
    val text: String,
    val level: LogLevel,
)

/** What the UI shows about the tunnel right now. */
enum class CoreStage { STOPPED, STARTING, RUNNING, FAILED }

data class CoreStatus(
    val stage: CoreStage = CoreStage.STOPPED,
    val startedAt: Long = 0,
    val coreVersion: String? = null,
    val lastError: String? = null,
) {
    val running: Boolean get() = stage == CoreStage.RUNNING
}
