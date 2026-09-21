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

private val REGION_RULES: List<Pair<Region, List<String>>> = listOf(
    Region("HK", "香港") to listOf("香港", "hk", "hongkong", "hong kong", "🇭🇰"),
    Region("TW", "台湾") to listOf("台湾", "台灣", "tw", "taiwan", "🇹🇼"),
    Region("JP", "日本") to listOf("日本", "jp", "japan", "东京", "大阪", "🇯🇵"),
    Region("SG", "新加坡") to listOf("新加坡", "狮城", "sg", "singapore", "🇸🇬"),
    Region("US", "美国") to listOf("美国", "us", "united states", "洛杉矶", "圣何塞", "🇺🇸"),
    Region("KR", "韩国") to listOf("韩国", "韓國", "kr", "korea", "首尔", "🇰🇷"),
    Region("UK", "英国") to listOf("英国", "uk", "united kingdom", "london", "🇬🇧"),
    Region("DE", "德国") to listOf("德国", "de", "germany", "法兰克福", "🇩🇪"),
    Region("RU", "俄罗斯") to listOf("俄罗斯", "ru", "russia", "🇷🇺"),
    Region("IN", "印度") to listOf("印度", "in", "india", "🇮🇳"),
    Region("TR", "土耳其") to listOf("土耳其", "tr", "turkey", "🇹🇷"),
    Region("AR", "阿根廷") to listOf("阿根廷", "ar", "argentina", "🇦🇷"),
    Region("CN", "国内") to listOf("回国", "国内", "china", "直连"),
)

/** Buckets a node into a region by what its name says, same as the desktop build. */
fun regionOf(nodeName: String): Region {
    val lower = nodeName.lowercase()
    for ((region, keywords) in REGION_RULES) {
        if (keywords.any { lower.contains(it) }) return region
    }
    return Region("XX", "其他")
}

/** The small grey line under a node name: multiplier and protocol. */
fun nodeMeta(nodeName: String, type: String): String {
    val multiplier = Regex("""([0-9]+(?:\.[0-9]+)?)\s*[xX×]""").find(nodeName)?.groupValues?.get(1)
    return if (multiplier != null) "$type · ${multiplier}x" else type
}

fun percentOf(used: Long, total: Long): Float =
    if (total <= 0) 0f else (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

fun abbreviate(value: String, max: Int): String =
    if (value.length <= max) value else value.take(max - 1) + "…"
