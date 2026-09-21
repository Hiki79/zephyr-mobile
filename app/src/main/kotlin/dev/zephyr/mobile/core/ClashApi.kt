package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.ConnectionsResponse
import dev.zephyr.mobile.data.CoreLogLine
import dev.zephyr.mobile.data.MemorySample
import dev.zephyr.mobile.data.ProxiesResponse
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.data.Rule
import dev.zephyr.mobile.data.RulesResponse
import dev.zephyr.mobile.data.TrafficSample
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The app's only channel to the running core, and the same one the Windows
 * build uses: mihomo's REST API on loopback, guarded by a random per-install
 * secret. Nothing here reaches the public internet.
 */
class ClashApi(private val port: Int, private val secret: String) {

    private val base = "http://127.0.0.1:$port"

    private fun builder(path: String): Request.Builder =
        Request.Builder()
            .url(base + path)
            .header("Authorization", "Bearer $secret")

    private suspend fun text(path: String, readTimeoutSeconds: Long = 8): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                clientWithReadTimeout(readTimeoutSeconds)
                    .newCall(builder(path).get().build())
                    .execute()
                    .use { response -> if (response.isSuccessful) response.body?.string().orEmpty() else null }
            }.getOrNull()
        }

    private suspend fun send(path: String, method: String, body: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = (body ?: "").toRequestBody(JSON_MEDIA)
                val request = builder(path)
                    .method(method, if (method == "DELETE" && body == null) null else payload)
                    .build()
                shared.newCall(request).execute().use(Response::isSuccessful)
            }.getOrElse { false }
        }

    /** Confirms the core is actually answering, and reports which build it is. */
    suspend fun version(): String? {
        val body = text("/version", readTimeoutSeconds = 3) ?: return null
        return runCatching {
            json.decodeFromString<JsonObject>(body)["version"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    suspend fun proxies(): Map<String, ProxyItem> {
        val body = text("/proxies") ?: return emptyMap()
        return runCatching { json.decodeFromString<ProxiesResponse>(body).proxies }
            .getOrElse { emptyMap() }
    }

    suspend fun selectNode(group: String, node: String): Boolean =
        send("/proxies/${encode(group)}", "PUT", """{"name":${quote(node)}}""")

    /** Tests a whole group at once; the core returns a name to latency map. */
    suspend fun groupDelay(group: String, testUrl: String, timeoutMillis: Int = 5000): Map<String, Int> {
        val path = "/group/${encode(group)}/delay?timeout=$timeoutMillis&url=${encode(testUrl)}"
        val body = text(path, readTimeoutSeconds = (timeoutMillis / 1000L) + 20) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Int>>(body)
        }.getOrElse { emptyMap() }
    }

    suspend fun rules(): List<Rule> {
        val body = text("/rules") ?: return emptyList()
        return runCatching { json.decodeFromString<RulesResponse>(body).rules }
            .getOrElse { emptyList() }
    }

    suspend fun connections(): ConnectionsResponse {
        val body = text("/connections") ?: return ConnectionsResponse()
        return runCatching { json.decodeFromString<ConnectionsResponse>(body) }
            .getOrElse { ConnectionsResponse() }
    }

    suspend fun closeConnection(id: String): Boolean =
        send("/connections/${encode(id)}", "DELETE", null)

    suspend fun closeAllConnections(): Boolean = send("/connections", "DELETE", null)

    /** Mode is hot-swappable; it never needs the core restarted. */
    suspend fun patchMode(mode: String): Boolean =
        send("/configs", "PATCH", """{"mode":${quote(mode)}}""")

    /**
     * /traffic and /memory are plain chunked HTTP streams of one JSON object
     * per line, so they are read directly rather than over a socket.
     */
    fun trafficFlow(): Flow<TrafficSample> = lineFlow("/traffic") { line ->
        runCatching { json.decodeFromString<TrafficSample>(line) }.getOrNull()
    }

    fun memoryFlow(): Flow<MemorySample> = lineFlow("/memory") { line ->
        runCatching { json.decodeFromString<MemorySample>(line) }.getOrNull()
    }

    private fun <T> lineFlow(path: String, parse: (String) -> T?): Flow<T> = flow {
        streaming.newCall(builder(path).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use
            val source = response.body?.source() ?: return@use
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                parse(line)?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * mihomo serves /logs over WebSocket only, and it reads the token from the
     * Authorization header rather than a query parameter, which is why this
     * cannot be a plain HTTP stream like the two above.
     */
    fun logFlow(level: String): Flow<CoreLogLine> = callbackFlow {
        val request = builder("/logs?level=$level").build()
        val socket = streaming.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<CoreLogLine>(text) }
                    .getOrNull()
                    ?.let { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { socket.cancel() }
    }

    private fun clientWithReadTimeout(seconds: Long): OkHttpClient =
        if (seconds == DEFAULT_READ_TIMEOUT) {
            shared
        } else {
            shared.newBuilder().readTimeout(seconds, TimeUnit.SECONDS).build()
        }

    companion object {
        private const val DEFAULT_READ_TIMEOUT = 8L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Loopback only, so connection and call timeouts can be short. */
        private val shared: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        /** Long-lived streams must never time out mid-flight. */
        private val streaming: OkHttpClient = shared.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        /** Group and node names routinely contain quotes, emoji and slashes. */
        private fun quote(value: String): String = buildString {
            append('"')
            for (character in value) {
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character < ' ') {
                        append("\\u").append("%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }
    }
}
