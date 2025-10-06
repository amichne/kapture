package io.amichne.kapture.core.authenticator.internal

import io.amichne.kapture.core.authenticator.RequestAuthenticator
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.amichne.kapture.core.util.JsonProvider

internal class JiraPersonalAccessToken(
    private val email: String,
    private val token: String
) : RequestAuthenticator {
    override fun apply(builder: HttpRequestBuilder) {
        if (email.isBlank() || token.isBlank()) return
        val encoded = JsonProvider.encodeBasic("$email:$token")
        builder.header(HttpHeaders.Authorization, "Basic $encoded")
    }
}
