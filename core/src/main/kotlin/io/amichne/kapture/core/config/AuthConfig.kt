package io.amichne.kapture.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declarative authentication configuration that can be serialised in the
 * standard config file and applied by HTTP clients without leaking
 * implementation details into calling code.
 */
@Serializable
sealed class AuthConfig {
    /** No authentication headers are applied. */
    @Serializable
    @SerialName("none")
    data object None : AuthConfig()

    /** Sends an `Authorization: Bearer <token>` header with each request. */
    @Serializable
    @SerialName("bearer")
    data class Bearer(val token: String) : AuthConfig()

    /** Applies HTTP Basic authentication using the supplied credentials. */
    @Serializable
    @SerialName("basic")
    data class Basic(val username: String, val password: String) : AuthConfig()

    /**
     * Jira personal access token support. Authenticates with Basic auth using
     * the Atlassian recommended email/token pair while keeping the config
     * expressive for future platform-specific mechanisms.
     */
    @Serializable
    @SerialName("jira_pat")
    data class JiraPat(val email: String, val token: String) : AuthConfig()
}
