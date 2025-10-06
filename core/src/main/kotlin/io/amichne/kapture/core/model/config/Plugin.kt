package io.amichne.kapture.core.model.config

import io.amichne.kapture.core.config.Integration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes how Kapture communicates with external systems such as Jira. The
 * configuration is polymorphic so we can add new integration styles (REST,
 * CLI, GraphQL, etc.) without changing the rest of the codebase.
 */
@Serializable
sealed class Plugin(val type: Type) {

    enum class Type {
        HTTP, CLI
    }

    /**
     * Talks directly to an HTTP API using the provided base URL and
     * authentication settings.
     */
    @Serializable
    @SerialName("rest")
    data class Http(
        val baseUrl: String = "http://localhost:8080",
        val auth: Authentication = Authentication.None
    ) : Plugin(Type.HTTP)

    /**
     * Delegates to the community Jira CLI tool. The executable is expected to
     * be available on the user's PATH unless overridden. Optional environment
     * variables can be supplied for authentication (PAT, email, site).
     */
    @Serializable
    @SerialName("cli")
    data class Cli(
        val executable: String,
        val environment: Map<String, String>,
        val timeoutSeconds: Long
    ) : Plugin(Type.CLI)

    companion object {
        fun Integration<Cli>.toPlugin(
            executable: String = connection.executable,
            environment: Map<String, String> = connection.environment,
            timeoutSeconds: Long = connection.timeoutSeconds
        ): Cli = Cli(
            executable = executable,
            environment = environment,
            timeoutSeconds = timeoutSeconds
        )
    }
}
