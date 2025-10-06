package io.amichne.kapture.core.http.auth

import io.amichne.kapture.core.config.AuthConfig
import io.ktor.client.request.HttpRequestBuilder
import io.amichne.kapture.core.http.JsonProvider

interface RequestAuthenticator {
    fun apply(builder: HttpRequestBuilder)

    companion object {
        fun from(auth: AuthConfig): RequestAuthenticator = when (auth) {
            is AuthConfig.None -> NoOpAuthenticator
            is AuthConfig.Bearer -> BearerAuthenticator(auth.token)
            is AuthConfig.Basic -> BasicAuthenticator(auth.username, auth.password)
            is AuthConfig.JiraPat -> JiraPatAuthenticator(auth.email, auth.token)
        }
    }
}
