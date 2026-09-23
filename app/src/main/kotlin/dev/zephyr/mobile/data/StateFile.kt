package dev.zephyr.mobile.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedState(val settings: Settings, val profiles: List<Profile>)

/** One atomic document makes settings + profile index a single commit. */
class StateFile(private val file: File) {
    private val backup = File(file.path + ".bak")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var recovered = false
        private set

    @Synchronized
    fun read(): SavedState? {
        if (!file.exists() && !backup.exists()) return null
        return runCatching { json.decodeFromString<SavedState>(file.readText()) }.getOrElse {
            val previous = json.decodeFromString<SavedState>(backup.readText())
            recovered = true
            previous
        }
    }

    /** The previous snapshot, which a restore would bring back; null when unreadable. */
    @Synchronized
    fun readBackup(): SavedState? =
        if (!backup.isFile) null
        else runCatching { json.decodeFromString<SavedState>(backup.readText()) }.getOrNull()

    @Synchronized
    fun write(state: SavedState) {
        val bytes = json.encodeToString(state).toByteArray(Charsets.UTF_8)
        val old = if (file.isFile) runCatching {
            val raw = file.readBytes()
            json.decodeFromString<SavedState>(raw.toString(Charsets.UTF_8))
            raw
        }.getOrNull() else null
        // Keep the last valid snapshot, including when recovering a corrupt primary.
        if (old != null) replace(backup, old)
        replace(file, bytes)
    }

    private fun replace(target: File, bytes: ByteArray) {
        val temp = File(target.path + ".tmp")
        try {
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            runCatching {
                Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            }.getOrElse {
                // Some Android filesystems do not implement ATOMIC_MOVE; the
                // fully written temp file is still safer than writeText().
                Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }
}
