package io.amichne.kapture.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes how Kapture communicates with external systems such as Jira. The
 * configuration is polymorphic so we can add new integration styles (REST,
 * CLI, GraphQL, etc.) without changing the rest of the codebase.
 */
@Serializable
sealed class ExternalIntegration {
    /**
     * Talks directly to an HTTP API using the provided base URL and
     * authentication settings.
     */
    @Serializable
    @SerialName("rest")
    data class Rest(
        val baseUrl: String = "http://localhost:8080",
        val auth: AuthConfig = AuthConfig.None
    ) : ExternalIntegration()

    /**
     * Delegates to the community Jira CLI tool. The executable is expected to
     * be available on the user's PATH unless overridden. Optional environment
     * variables can be supplied for authentication (PAT, email, site).
     */
    @Serializable
    @SerialName("jiraCli")
    data class JiraCli(
        val executable: String = "jira",
        val environment: Map<String, String> = emptyMap(),
        val timeoutSeconds: Long = 15
    ) : ExternalIntegration()
}
