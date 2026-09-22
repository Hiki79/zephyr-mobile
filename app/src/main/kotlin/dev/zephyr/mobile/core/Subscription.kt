package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.Profile
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetching a subscription is the only request this app makes to the public
 * internet, and it goes to the address the user typed and nowhere else. No
 * conversion service, no analytics ping, no update check.
 */
object Subscription {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .dns(ResilientDns)
        .build()

    class FetchError(message: String) : Exception(message)

    /**
     * Downloads the config and stores it under [uid]. Providers key their
     * response off the User-Agent, so it claims to be a Clash client; that
     * string carries no device or install identifier.
     */
    data class Download(val profile: Profile, val yaml: String)

    suspend fun fetch(url: String, uid: String, keepName: String? = null): Download =
        withContext(Dispatchers.IO) {
            val trimmed = url.trim()
            // A subscription body holds every node's password. Over plain HTTP
            // anyone on the path reads it, so the network security config blocks
            // cleartext to all hosts but loopback; this check only turns that
            // socket-level refusal into a message the user can act on.
            if (trimmed.startsWith("http://", ignoreCase = true)) {
                throw FetchError("订阅地址必须是 https://，明文 http 会把节点密码暴露在路上")
            }
            if (!trimmed.startsWith("https://", ignoreCase = true)) {
                throw FetchError("订阅地址需要以 https:// 开头")
            }

            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val (body, headers) = runCatching {
                client.newCall(request).readResponse { response ->
                    if (!response.isSuccessful) {
                        throw FetchError("订阅服务器返回 ${response.code}")
                    }
                    val text = response.body?.boundedText(16L * 1024 * 1024).orEmpty()
                    text to mapOf(
                        "userinfo" to (response.header("subscription-userinfo") ?: ""),
                        "disposition" to (response.header("content-disposition") ?: ""),
                        "profile-web" to (response.header("profile-web-page-url") ?: ""),
                    )
                }
            }.getOrElse { error ->
                throw when (error) {
                    is CancellationException -> error
                    is FetchError -> error
                    is UnknownHostException -> FetchError(
                        "域名解析失败：本机 DNS 和加密 DNS 都查不到 ${request.url.host}，检查订阅地址或换个网络",
                    )
                    else -> FetchError("下载失败：${error.message ?: error::class.simpleName}")
                }
            }

            if (body.isBlank()) throw FetchError("订阅内容为空")
            val preview = runCatching { ProfileConfig.parse(body) }.getOrElse {
                throw FetchError("订阅配置无效：${it.message}")
            }

            val info = parseUserInfo(headers["userinfo"].orEmpty())
            Download(Profile(
                uid = uid,
                name = keepName?.takeIf { it.isNotBlank() }
                    ?: parseFileName(headers["disposition"].orEmpty())
                    ?: defaultName(trimmed),
                url = trimmed,
                updated = System.currentTimeMillis() / 1000,
                upload = info["upload"] ?: 0,
                download = info["download"] ?: 0,
                total = info["total"] ?: 0,
                expire = info["expire"] ?: 0,
                home = headers["profile-web"]?.takeIf { it.isNotBlank() },
                nodeCount = preview.nodeCount,
            ), body)
        }

    /** `upload=1; download=2; total=3; expire=4` on the subscription-userinfo header. */
    private fun parseUserInfo(header: String): Map<String, Long> =
        header.split(';')
            .mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size != 2) return@mapNotNull null
                val value = pair[1].trim().toLongOrNull() ?: return@mapNotNull null
                pair[0].trim().lowercase() to value
            }
            .toMap()

    private fun parseFileName(disposition: String): String? {
        val star = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.get(1)
        if (star != null) {
            return runCatching { java.net.URLDecoder.decode(star.trim(), "UTF-8") }.getOrNull()
        }
        return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.get(1)?.trim()
            ?.removeSuffix(".yaml")
            ?.removeSuffix(".yml")
            ?.takeIf { it.isNotBlank() }
    }

    private fun defaultName(url: String): String =
        runCatching { java.net.URI(url).host ?: "订阅" }.getOrElse { "订阅" }

    /**
     * System DNS first; when it comes back empty — the usual symptom of a
     * poisoned resolver, which airport domains attract — the question is asked
     * again over HTTPS to AliDNS and DNSPod. Their own hostnames resolve from
     * pinned addresses so a broken system resolver cannot take the fallback
     * down with it; the certificate is still checked against the hostname.
     */
    private object ResilientDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (_: UnknownHostException) {
                // fall through to the DoH fallback
            }
            val fallback = dohLookup(hostname)
            if (fallback.isNotEmpty()) return fallback
            throw UnknownHostException("$hostname: system DNS and DoH both empty")
        }
    }

    private val dohEndpoints = listOf(
        "https://dns.alidns.com/resolve" to "1",
        "https://doh.pub/dns-query" to "A",
    )

    private val dohBootstrap = mapOf(
        "dns.alidns.com" to listOf("223.5.5.5", "223.6.6.6"),
        "doh.pub" to listOf("119.29.29.29", "1.12.12.12"),
    )

    private val dohClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .dns { hostname ->
            dohBootstrap[hostname]?.map(InetAddress::getByName) ?: Dns.SYSTEM.lookup(hostname)
        }
        .build()

    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** dns-json from either provider; only validated A records are returned. */
    private fun dohLookup(hostname: String): List<InetAddress> {
        for ((base, type) in dohEndpoints) {
            val addresses = runCatching {
                val request = Request.Builder()
                    .url("$base?name=$hostname&type=$type")
                    .header("accept", "application/dns-json")
                    .get()
                    .build()
                dohClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val answers = JSONObject(response.body?.string().orEmpty())
                        .optJSONArray("Answer") ?: return@use emptyList()
                    buildList {
                        for (index in 0 until answers.length()) {
                            val answer = answers.optJSONObject(index) ?: continue
                            if (answer.optInt("type") != 1) continue
                            val data = answer.optString("data")
                            val match = ipv4.matchEntire(data) ?: continue
                            if (match.groupValues.drop(1).any { it.toInt() > 255 }) continue
                            add(InetAddress.getByName(data))
                        }
                    }
                }
            }.getOrElse { emptyList() }
            if (addresses.isNotEmpty()) return addresses
        }
        return emptyList()
    }

    private const val USER_AGENT = "clash-verge/v2.0.0"
}
