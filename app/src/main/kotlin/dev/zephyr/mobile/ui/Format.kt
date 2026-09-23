package dev.zephyr.mobile.ui

import androidx.compose.ui.graphics.Color
import java.util.Locale

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    val digits = if (value >= 100 || unit == 0) 0 else 1
    return String.format(Locale.US, "%.${digits}f %s", value, BYTE_UNITS[unit])
}

/** Splits a per-second rate so the number and its unit can be styled apart. */
fun splitRate(bytesPerSecond: Long): Pair<String, String> {
    if (bytesPerSecond <= 0) return "0" to "B/s"
    var value = bytesPerSecond.toDouble()
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    val digits = if (value >= 100 || unit == 0) 0 else 1
    return String.format(Locale.US, "%.${digits}f", value) to "${BYTE_UNITS[unit]}/s"
}

fun formatUptime(startedAtSeconds: Long): String {
    if (startedAtSeconds <= 0) return "未启动"
    val seconds = (System.currentTimeMillis() / 1000 - startedAtSeconds).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${rest}秒"
        else -> "${rest}秒"
    }
}

fun formatExpiry(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "无期限"
    val remaining = epochSeconds - System.currentTimeMillis() / 1000
    if (remaining <= 0) return "已过期"
    val days = remaining / 86400
    return when {
        days > 30 -> "剩 ${days / 30} 个月"
        days > 0 -> "剩 $days 天"
        else -> "今天到期"
    }
}

fun formatUpdated(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "从未更新"
    val ago = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        ago < 60 -> "刚刚更新"
        ago < 3600 -> "${ago / 60} 分钟前"
        ago < 86400 -> "${ago / 3600} 小时前"
        else -> "${ago / 86400} 天前"
    }
}

enum class DelayTone { OK, MID, BAD, NONE }

fun delayTone(ms: Int?): DelayTone = when {
    ms == null -> DelayTone.NONE
    ms <= 0 -> DelayTone.BAD
    ms <= 150 -> DelayTone.OK
    ms <= 300 -> DelayTone.MID
    else -> DelayTone.BAD
}

fun delayText(ms: Int?): String = when {
    ms == null -> "--"
    ms <= 0 -> "超时"
    else -> "$ms ms"
}

fun DelayTone.color(): Color = when (this) {
    DelayTone.OK -> Z.green
    DelayTone.MID -> Z.orange
    DelayTone.BAD -> Z.red
    DelayTone.NONE -> Z.faint
}

data class Region(val code: String, val name: String)

val OTHER_REGION = Region("XX", "其他")

// Short codes only count as whole words, so "us" does not match "Plus" or
// "Russia" and "in" does not match "Spain". The boundary is spelled out, not
// written as \b: Android's ICU counts Chinese as word characters, so "香港HK"
// would have no boundary on the phone while the JVM tests saw one.
private fun region(code: String, name: String, words: String, vararg codes: String): Pair<Region, Regex> {
    val whole = codes.joinToString("|") { "(?<![a-z0-9])$it(?![a-z0-9])" }
    return Region(code, name) to Regex("$words|$whole", RegexOption.IGNORE_CASE)
}

// The desktop build's table, plus Thailand and mainland relays.
private val REGION_RULES: List<Pair<Region, Regex>> = listOf(
    region("HK", "香港", "香港|hong ?kong", "hk"),
    region("TW", "台湾", "台湾|台灣|taiwan", "tw"),
    region("SG", "新加坡", "新加坡|狮城|singapore", "sg"),
    region("JP", "日本", "日本|东京|東京|大阪|japan", "jp"),
    region("KR", "韩国", "韩国|韓國|首尔|korea", "kr"),
    region("US", "美国", "美国|美國|洛杉矶|圣何塞|西雅图|纽约|united states", "us"),
    region("UK", "英国", "英国|伦敦|united kingdom|london", "uk", "gb"),
    region("DE", "德国", "德国|法兰克福|germany", "de"),
    region("FR", "法国", "法国|巴黎|france", "fr"),
    region("NL", "荷兰", "荷兰|阿姆斯特丹|netherlands", "nl"),
    region("RU", "俄罗斯", "俄罗斯|莫斯科|russia", "ru"),
    region("MY", "马来西亚", "马来|malaysia", "my"),
    region("TH", "泰国", "泰国|曼谷|thailand", "th"),
    region("TR", "土耳其", "土耳其|turkey", "tr"),
    region("AR", "阿根廷", "阿根廷|argentina", "ar"),
    region("IN", "印度", "印度|india", "in"),
    region("AU", "澳大利亚", "澳大利亚|澳洲|悉尼|australia", "au"),
    region("CA", "加拿大", "加拿大|canada", "ca"),
    region("CN", "国内", "回国|中国大陆", "china"),
)

private val REGION_BY_CODE = REGION_RULES.associate { (region, _) -> region.code to region }

/** A flag emoji names its country outright, for every country there is. */
private fun flagRegion(name: String): Region? {
    var index = 0
    while (index < name.length) {
        val first = name.codePointAt(index)
        val next = index + Character.charCount(first)
        if (first in 0x1F1E6..0x1F1FF && next < name.length) {
            val second = name.codePointAt(next)
            if (second in 0x1F1E6..0x1F1FF) {
                val iso = "${'A' + (first - 0x1F1E6)}${'A' + (second - 0x1F1E6)}"
                val code = if (iso == "GB") "UK" else iso
                REGION_BY_CODE[code]?.let { return it }
                val display = runCatching {
                    Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
                }.getOrNull()
                return Region(iso, display?.takeIf { it.isNotBlank() && it != iso } ?: iso)
            }
        }
        index = next
    }
    return null
}

/** Buckets a node into a region by what its name says: flag first, then words. */
fun regionOf(nodeName: String): Region =
    flagRegion(nodeName)
        ?: REGION_RULES.firstOrNull { (_, pattern) -> pattern.containsMatchIn(nodeName) }?.first
        ?: OTHER_REGION

// `x0.5`, `0.5x`, `×2`, `[2X]` and `倍率 2` all occur in the wild. The number
// must stand apart from letters, so `Hy2` and `Xray` are not multipliers.
private val MULTIPLIER = Regex(
    """(?<![a-z0-9.])(?:[x×]\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*[x×]|倍率\s*[:：]?\s*(\d+(?:\.\d+)?))(?![a-z0-9])""",
    RegexOption.IGNORE_CASE,
)

// Some providers end the name with a bare rate: "美国 直连 | 0.25". Only a
// decimal counts, so a plain "| 01" line number is not read as 1×.
private val TRAILING_RATE = Regex("""[|｜]\s*(\d+\.\d+)\s*$""")

/** The traffic multiplier a provider writes into a node name, if any. */
fun multiplierOf(nodeName: String): Double? =
    (MULTIPLIER.find(nodeName) ?: TRAILING_RATE.find(nodeName))
        ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toDoubleOrNull()

fun formatMultiplier(multiplier: Double): String =
    if (multiplier % 1.0 == 0.0) "×${multiplier.toLong()}" else "×$multiplier"

// Providers smuggle account notices into the node list as fake nodes.
private val INFO_NODE = Regex(
    """剩余|到期|过期|重置|官网|余额|域名|网址|套餐|公告|客服|频道|重启|expire|traffic""",
    RegexOption.IGNORE_CASE,
)

/** "剩余流量：491 GB", "套餐到期：…": a notice dressed up as a node, not a server. */
fun isInfoNode(nodeName: String): Boolean = INFO_NODE.containsMatchIn(nodeName)

private val WHITESPACE = Regex("""\s+""")
private val MULTIPLIER_TOKEN = Regex("""^(?:[x×](\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)[x×])$""")

/**
 * Space-separated words must all appear, so "香港 家宽" finds "香港|家宽|x1".
 * A multiplier word compares as a number in either spelling: "0.5x" finds
 * "x0.5", and "x2" does not find "x25".
 */
fun matchesQuery(name: String, query: String): Boolean {
    val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val lower = name.lowercase()
    return tokens.all { token ->
        val wanted = MULTIPLIER_TOKEN.matchEntire(token)?.groupValues?.drop(1)
            ?.firstOrNull { it.isNotEmpty() }?.toDoubleOrNull()
        if (wanted != null) multiplierOf(name) == wanted else lower.contains(token)
    }
}

fun percentOf(used: Long, total: Long): Float =
    if (total <= 0) 0f else (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

fun abbreviate(value: String, max: Int): String =
    if (value.length <= max) value else value.take(max - 1) + "…"
