package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.Profile
import dev.zephyr.mobile.data.Store
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetching a subscription is the only request this app makes to the public
 * internet, and it goes to the address the user typed and nowhere else. No
 * conversion service, no analytics ping, no update check.
 */
object Subscription {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    class FetchError(message: String) : Exception(message)

    /**
     * Downloads the config and stores it under [uid]. Providers key their
     * response off the User-Agent, so it claims to be a Clash client; that
     * string carries no device or install identifier.
     */
    suspend fun fetch(url: String, uid: String, store: Store, keepName: String? = null): Profile =
        withContext(Dispatchers.IO) {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                throw FetchError("订阅地址需要以 http:// 或 https:// 开头")
            }

            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val (body, headers) = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw FetchError("订阅服务器返回 ${response.code}")
                    }
                    val text = response.body?.string().orEmpty()
                    text to mapOf(
                        "userinfo" to (response.header("subscription-userinfo") ?: ""),
                        "disposition" to (response.header("content-disposition") ?: ""),
                        "profile-web" to (response.header("profile-web-page-url") ?: ""),
                    )
                }
            }.getOrElse { error ->
                throw if (error is FetchError) error else FetchError("下载失败：${error.message ?: error::class.simpleName}")
            }

            if (body.isBlank()) throw FetchError("订阅内容为空")
            if (!looksLikeClashConfig(body)) {
                throw FetchError("这个地址返回的不是 Clash/mihomo 配置，可能是 v2ray 或 base64 订阅")
            }

            store.writeProfileYaml(uid, body)

            val info = parseUserInfo(headers["userinfo"].orEmpty())
            Profile(
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
                nodeCount = countNodes(body),
            )
        }

    /**
     * A base64 or v2ray subscription parses as YAML without error but produces
     * a config with nothing in it, so the shape is checked before it is stored.
     */
    private fun looksLikeClashConfig(body: String): Boolean {
        val head = body.take(200_000)
        return head.contains("proxies:") || head.contains("proxy-providers:")
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

    /** A count good enough for the card; the core's own list is authoritative. */
    private fun countNodes(body: String): Int =
        body.lineSequence().count { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("- {") || trimmed.startsWith("- name:")
        }

    private const val USER_AGENT = "clash-verge/v2.0.0"
}
