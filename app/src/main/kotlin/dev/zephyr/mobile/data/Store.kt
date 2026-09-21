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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun loadSettings(): Settings {
        val loaded = runCatching {
            json.decodeFromString<Settings>(settingsFile.readText())
        }.getOrElse { Settings() }

        // A fresh install has no secret; without one anything on the device
        // could drive the core through its REST port.
        return if (loaded.secret.isBlank()) {
            loaded.copy(secret = randomSecret()).also(::saveSettings)
        } else {
            loaded
        }
    }

    fun saveSettings(settings: Settings) {
        runCatching { settingsFile.writeText(json.encodeToString(settings)) }
    }

    fun loadProfiles(): List<Profile> = runCatching {
        json.decodeFromString<ProfileList>(profilesFile.readText()).items
    }.getOrElse { emptyList() }

    fun saveProfiles(items: List<Profile>) {
        runCatching { profilesFile.writeText(json.encodeToString(ProfileList(items))) }
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
        for (name in listOf("GeoIP.dat", "GeoSite.dat", "geoip.metadb", "ASN.mmdb")) {
            val target = File(runtimeDir, name)
            if (target.isFile && target.length() > 0) continue
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
