package io.amichne.kapture.core.http.auth

import io.ktor.client.request.HttpRequestBuilder

object NoOpAuthenticator : RequestAuthenticator {
    override fun apply(builder: HttpRequestBuilder) = Unit
}
