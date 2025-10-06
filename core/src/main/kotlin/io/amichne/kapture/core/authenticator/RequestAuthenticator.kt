package io.amichne.kapture.core.authenticator

import io.amichne.kapture.core.authenticator.internal.BasicAuthenticator
import io.amichne.kapture.core.authenticator.internal.BearerAuthenticator
import io.amichne.kapture.core.authenticator.internal.JiraPersonalAccessToken
import io.amichne.kapture.core.authenticator.internal.NoOpAuthenticator
import io.amichne.kapture.core.model.config.Authentication
import io.ktor.client.request.HttpRequestBuilder

interface RequestAuthenticator {
    fun apply(builder: HttpRequestBuilder)

    companion object {
        fun from(auth: Authentication): RequestAuthenticator = when (auth) {
            is Authentication.None -> NoOpAuthenticator
            is Authentication.Bearer -> BearerAuthenticator(auth.token)
            is Authentication.Basic -> BasicAuthenticator(auth.username, auth.password)
            is Authentication.PersonalAccessToken -> JiraPersonalAccessToken(auth.email, auth.token)
        }
    }
}
