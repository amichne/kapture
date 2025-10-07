package io.amichne.kapture.core.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declarative authentication configuration that can be serialised in the
 * standard config file and applied by HTTP clients without leaking
 * implementation details into calling code.
 */
@Serializable
sealed class Authentication {
    /** No authentication headers are applied. */
    @Serializable
    @SerialName("none")
    data object None : Authentication()

    /** Sends an `Authorization: Bearer <token>` header with each request. */
    @Serializable
    @SerialName("bearer")
    data class Bearer(val token: String) : Authentication()

    /** Applies HTTP Basic authentication using the supplied credentials. */
    @Serializable
    @SerialName("basic")
    data class Basic(
        val username: String,
        val password: String
    ) : Authentication()

    /**
     * Jira personal access token support. Authenticates with Basic authenticator using
     * the Atlassian recommended email/token pair while keeping the config
     * expressive for future platform-specific mechanisms.
     */
    @Serializable
    @SerialName("pat")
    data class PersonalAccessToken(
        val email: String,
        val token: String
    ) : Authentication()
}
