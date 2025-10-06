package io.amichne.kapture.interceptors

import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.model.command.CommandInvocation

interface GitInterceptor {
    /**
     * Hook executed before the underlying Git command runs. Return `null` to
     * continue, or provide an exit code to block execution and abort early.
     */
    fun before(
        commandInvocation: CommandInvocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? = null

    /**
     * Hook executed after Git completes, receiving the exit code so
     * implementations can emit telemetry or mutate state regardless of
     * success.
     */
    fun after(
        commandInvocation: CommandInvocation,
        exitCode: Int,
        config: Config,
        client: ExternalClient<*>
    ) {
    }
}
