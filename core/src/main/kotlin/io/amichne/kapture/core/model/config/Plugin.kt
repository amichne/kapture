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
        val auth: Authentication = Authentication.None,
        val timeoutMs: Long = 10_000,
        val provider: String = "jira"
    ) : Plugin(Type.HTTP)

    companion object {


        fun Integration<Cli>.toPlugin(
            executable: String = connection.executable,
            environment: Map<String, String> = connection.environment,
            timeoutSeconds: Long = connection.timeoutSeconds,
        ): Cli.Jira = Cli.Jira(
            executable = executable,
            environment = environment,
            timeoutSeconds = timeoutSeconds
        )
    }
}
