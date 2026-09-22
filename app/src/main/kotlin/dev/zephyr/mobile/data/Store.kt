package dev.zephyr.mobile.data

import android.content.Context
import java.io.File
import java.security.SecureRandom
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Plain JSON files in the app's private directory, the same shape the Windows
 * build keeps in %APPDATA%. No database, no cloud, nothing readable by other
 * apps; `allowBackup` is off so none of it leaves the device either.
 */
class Store(context: Context) {

    private val root: File = context.filesDir
    private val assets = context.assets
    val profilesDir: File = File(root, "profiles").apply { mkdirs() }
    val runtimeDir: File = File(root, "runtime").apply { mkdirs() }

    private val settingsFile = File(root, "settings.json")
    private val profilesFile = File(root, "profiles.json")
    private val stateFile = StateFile(File(root, "state.json"))
    private var loadFailure: Throwable? = null
    var recoveryNotice: String? = null
        private set

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun loadState(): SavedState = try {
        val existing = stateFile.read()
        val legacy = existing ?: SavedState(
            if (settingsFile.exists()) json.decodeFromString<Settings>(settingsFile.readText()) else Settings(),
            if (profilesFile.exists()) json.decodeFromString<ProfileList>(profilesFile.readText()).items else emptyList(),
        )
        val loaded = if (legacy.settings.secret.isBlank()) {
            legacy.copy(settings = legacy.settings.copy(secret = randomSecret()))
        } else legacy
        if (existing == null || loaded != existing) stateFile.write(loaded)
        if (stateFile.recovered) recoveryNotice = "配置文件损坏，已恢复上次有效备份"
        loaded
    } catch (error: Exception) {
        loadFailure = error
        throw IllegalStateException("本地配置无法读取，原文件已保留，请勿卸载应用", error)
    }

    fun saveState(settings: Settings, items: List<Profile>) {
        check(loadFailure == null) { "本地配置损坏，已暂停写入以保留原数据" }
        stateFile.write(SavedState(settings, items))
    }

    fun profileFile(uid: String): File = File(profilesDir, "$uid.yaml")

    fun readProfileYaml(uid: String?): String? {
        if (uid == null) return null
        val file = profileFile(uid)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    fun writeProfileYaml(uid: String, yaml: String) {
        val file = android.util.AtomicFile(profileFile(uid))
        val output = file.startWrite()
        try {
            output.write(yaml.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    /** Avoid needing a working proxy to download the rules needed to start that proxy. */
    fun prepareGeoData() {
        val revisionFile = File(runtimeDir, "bundled-geodata.json")
        val previous = runCatching { json.decodeFromString<Map<String, String>>(revisionFile.readText()) }.getOrDefault(emptyMap())
        val revision = json.decodeFromString<Map<String, String>>(assets.open("geodata/manifest.json").bufferedReader().use { it.readText() })
        for (name in listOf("GeoIP.dat", "GeoSite.dat", "geoip.metadb", "ASN.mmdb")) {
            val target = File(runtimeDir, name)
            if (target.isFile && target.length() > 0) {
                if (previous[name] == revision[name]) continue
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                target.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                }
                val current = digest.digest().joinToString("") { "%02x".format(it) }
                if (current == revision[name]) continue
                // Preserve independently updated databases; only replace an older bundled copy.
                if (previous[name] == null || current != previous[name]) continue
            }
            val file = android.util.AtomicFile(target)
            val output = file.startWrite()
            try {
                assets.open("geodata/$name").use { it.copyTo(output) }
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }
        val marker = android.util.AtomicFile(revisionFile)
        val stream = marker.startWrite()
        try {
            stream.write(json.encodeToString(revision).toByteArray(Charsets.UTF_8))
            marker.finishWrite(stream)
        } catch (error: Throwable) { marker.failWrite(stream); throw error }
    }

    fun deleteProfileYaml(uid: String) {
        runCatching { profileFile(uid).delete() }
    }

    companion object {
        private const val ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun randomSecret(length: Int = 24): String {
            val random = SecureRandom()
            return buildString(length) {
                repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            }
        }

        fun newUid(): String =
            java.lang.Long.toString(System.currentTimeMillis(), 36) +
                java.lang.Long.toString(SecureRandom().nextInt(1 shl 20).toLong(), 36)
    }
}
