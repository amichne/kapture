package io.amichne.kapture.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class Config(
    val external: ExternalIntegration = ExternalIntegration.Rest(),
    val branchPattern: String = "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null,
    val sessionTrackingIntervalMs: Long = 300_000,
    val ticketSystem: String = "jira",
    val localStateRoot: String = System.getenv("KAPTURE_LOCAL_STATE")
                                 ?: "${System.getProperty("user.home")}/.kapture/state"
) {
    @Serializable
    data class Enforcement(
        val branchPolicy: Mode = Mode.WARN,
        val statusCheck: Mode = Mode.WARN
    ) {
        @Serializable
        enum class Mode { WARN, BLOCK, OFF }
    }

    @Serializable
    data class StatusRules(
        val allowCommitWhen: Set<String> = setOf("IN_PROGRESS", "READY"),
        val allowPushWhen: Set<String> = setOf("READY", "IN_REVIEW")
    )

    companion object {
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
                val stateRoot = Paths.get(defaults.localStateRoot)
                add(stateRoot.resolve("config.json"))
            }

            for (candidate in candidates) {
                val config = runCatching { read(candidate) }.getOrNull()
                if (config != null) {
                    ensureStateRootExists(config)
                    return config
                }
            }

            ensureStateRootExists(defaults)
            return defaults
        }

        private fun read(path: Path): Config {
            if (!Files.exists(path)) {
                throw IOException("Config file not found at $path")
            }
            val raw = Files.readString(path)
            return json.decodeFromString(serializer(), raw)
        }

        private fun ensureStateRootExists(config: Config) {
            val statePath = Paths.get(config.localStateRoot)
            if (!Files.exists(statePath)) {
                runCatching { Files.createDirectories(statePath) }
            }
        }
    }
}

@Serializable
@SerialName("TicketStatusResponse")
data class TicketStatusResponse(val status: String)
