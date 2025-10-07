package io.amichne.kapture.core.authenticator.internal

import io.amichne.kapture.core.authenticator.RequestAuthenticator
import io.ktor.client.request.HttpRequestBuilder

/**
 * Jira Personal Access Token authenticator that delegates to Basic authentication
 * using email as username and token as password.
 */
internal class JiraPersonalAccessToken(
    email: String,
    token: String
) : RequestAuthenticator {
    private val delegate = BasicAuthenticator(email, token)

    override fun apply(builder: HttpRequestBuilder) {
        delegate.apply(builder)
    }
}
