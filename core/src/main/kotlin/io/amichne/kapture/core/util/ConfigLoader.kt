package io.amichne.kapture.core.util

import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Config.Companion.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Loads configuration from the explicit path, the `KAPTURE_CONFIG`
     * environment override, or the default state directory. Missing files
     * fall back to built-in defaults while ensuring the state root exists.
     */
    fun load(explicitPath: Path? = null): Config {
        val defaults = Config()
        val candidates = buildList {
            explicitPath?.let(::add)
            System.getenv("KAPTURE_CONFIG")
                ?.takeUnless { it.isBlank() }
                ?.let { Paths.get(it).toAbsolutePath().normalize() }
                ?.let(::add)
            val stateRoot = Paths.get(defaults.root)
            add(stateRoot.resolve("config.json"))
        }

        for (candidate in candidates) {
            val config = runCatching { read(candidate) }.getOrNull()
            if (config != null) {
                ensureRootExists(config)
                return config
            }
        }

        ensureRootExists(defaults)
        return defaults
    }

    private fun read(path: Path): Config {
        if (!Files.exists(path)) {
            throw IOException("Config file not found at $path")
        }
        val raw = Files.readString(path)
        return json.decodeFromString(serializer(), raw)
    }

    private fun ensureRootExists(config: Config) {
        val statePath = Paths.get(config.root)
        if (!Files.exists(statePath)) {
            runCatching { Files.createDirectories(statePath) }
        }
    }
}
