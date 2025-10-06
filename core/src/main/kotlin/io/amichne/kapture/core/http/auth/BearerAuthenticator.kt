package io.amichne.kapture.core.http.auth

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

class BearerAuthenticator(private val token: String) : RequestAuthenticator {
    override fun apply(builder: HttpRequestBuilder) {
        if (token.isNotBlank()) {
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
