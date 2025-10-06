package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.model.Invocation

interface GitInterceptor {
    /**
     * Hook executed before the underlying Git command runs. Return `null` to
     * continue, or provide an exit code to block execution and abort early.
     */
    fun before(
        invocation: Invocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? = null

    /**
     * Hook executed after Git completes, receiving the exit code so
     * implementations can emit telemetry or mutate state regardless of
     * success.
     */
    fun after(
        invocation: Invocation,
        exitCode: Int,
        config: Config,
        client: ExternalClient<*>
    ) {
    }
}
