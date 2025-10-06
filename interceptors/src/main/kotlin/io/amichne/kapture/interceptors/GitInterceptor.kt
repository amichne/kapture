package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.model.Invocation

interface GitInterceptor {
    fun before(invocation: Invocation, config: Config, client: ExternalClient): Int? = null

    fun after(invocation: Invocation, exitCode: Int, config: Config, client: ExternalClient) {}
}
