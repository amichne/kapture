package io.amichne.kapture.interceptors.session

import io.amichne.kapture.core.model.TimeSession
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.core.util.FileUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class SessionStore(
    stateRoot: String,
    private val json: Json
) {
    private val sessionPath: Path = Path.of(stateRoot).resolve("session.json")
    private val logPath: Path = Path.of(stateRoot).resolve("tracking.log")

    /**
     * Reads the persisted session file if present and returns the decoded
     * `TimeSession`, logging any failures to the debug log.
     */
    fun load(): TimeSession? = runCatching {
        if (!Files.exists(sessionPath)) return@runCatching null
        val text = Files.readString(sessionPath)
        json.decodeFromString(TimeSession.serializer(), text)
    }.getOrElse { throwable ->
        log("Failed to load session: ${throwable.message}")
        null
    }

    /**
     * Persists the provided session, removing the backing file when `null`
     * so that tracking can be cleanly disabled or reset.
     */
    fun save(session: TimeSession?) {
        if (session == null) {
            runCatching { Files.deleteIfExists(sessionPath) }
            return
        }
        runCatching {
            FileUtils.writeAtomically(sessionPath, json.encodeToString(session))
        }.onFailure { throwable ->
            log("Failed to persist session: ${throwable.message}")
        }
    }

    /**
     * Appends a timestamped debug entry to the session log when `KAPTURE_DEBUG`
     * is enabled, creating the log file on demand.
     */
    fun log(message: String) {
        if (!Environment.debugEnabled) return
        val timestamp = Instant.now().toString()
        val entry = "[$timestamp] $message\n"
        runCatching {
            Files.createDirectories(logPath.parent)
            Files.writeString(
                logPath,
                entry,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
    }
}
