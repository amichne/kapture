package io.amichne.kapture.core.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes how Kapture communicates with external systems such as Jira. The
 * configuration is polymorphic with a discriminated `type` field so we can add
 * new integration styles (REST, CLI, GraphQL, etc.) without changing the rest
 * of the codebase.
 */
object PluginDefaults {
    const val DEFAULT_CLI_EXECUTABLE: String = "jira-cli"
    val DEFAULT_CLI_PATHS: List<String> = listOf(DEFAULT_CLI_EXECUTABLE, "jira")
}

@Serializable
sealed class Plugin(val type: Type) {

    enum class Type {
        REST, CLI
    }

//    abstract val type: String

    /**
     * Talks directly to an HTTP REST API using the provided base URL and
     * authentication settings.
     */
    @Serializable
    @SerialName("REST")
    data class Rest(
        val baseUrl: String = "http://localhost:8080",
        val auth: Authentication = Authentication.None,
        val timeoutMs: Int? = null,
        val provider: String = "jira"
    ) : Plugin(Type.REST)

    /**
     * Delegates to an external CLI tool. Kapture will try each path in the
     * `paths` array in order until it finds a valid executable. Environment
     * variables can be supplied for authentication and configuration.
     */
    @Serializable
    @SerialName("CLI")
    data class Cli(
        val paths: List<String>,
        val environment: Map<String, String> = emptyMap(),
        val timeoutMs: Int? = null,
        val provider: String = "jira"
    ) : Plugin(Type.CLI)
}
