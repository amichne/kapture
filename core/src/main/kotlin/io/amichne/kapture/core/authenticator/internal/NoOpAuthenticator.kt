package io.amichne.kapture.core.authenticator.internal

import io.amichne.kapture.core.authenticator.RequestAuthenticator
import io.ktor.client.request.HttpRequestBuilder

internal object NoOpAuthenticator : RequestAuthenticator {
    override fun apply(builder: HttpRequestBuilder) = Unit
}
