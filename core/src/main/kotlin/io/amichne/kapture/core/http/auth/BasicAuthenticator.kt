package io.amichne.kapture.core.http.auth

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.amichne.kapture.core.http.JsonProvider

class BasicAuthenticator(
    private val username: String,
    private val password: String
) : RequestAuthenticator {
    override fun apply(builder: HttpRequestBuilder) {
        if (username.isBlank() && password.isBlank()) return
        val encoded = JsonProvider.encodeBasic("$username:$password")
        builder.header(HttpHeaders.Authorization, "Basic $encoded")
    }
}
