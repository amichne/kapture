package io.amichne.kapture.interceptors.session

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.model.Invocation
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.model.TimeSession
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.interceptors.GitInterceptor
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SessionTrackingInterceptor(
    private val clock: Clock = Clock.System,
    private val json: Json = Json { encodeDefaults = true }
) : GitInterceptor {
    /**
     * Updates the stored session after each Git invocation, rolling a new
     * session whenever the branch changes or the elapsed time exceeds the
     * configured tracking interval, and optionally emitting telemetry.
     */
    override fun after(
        invocation: Invocation,
        exitCode: Int,
        config: Config,
        client: ExternalClient
    ) {
        val store = SessionStore(config.localStateRoot, json)

        if (!config.trackingEnabled) {
            store.save(null)
            return
        }

        val branch = currentBranch(invocation) ?: return
        val now = clock.now()
        val ticket = BranchUtils.extractTicket(branch, config.branchPattern)
        val active = store.load()

        if (active == null) {
            store.save(TimeSession(branch, ticket, now, now))
            return
        }

        val branchChanged = active.branch != branch
        val gap = durationBetween(active.lastActivityTime, now)
        val timedOut = gap >= config.sessionTrackingIntervalMs.milliseconds

        if (branchChanged || timedOut) {
            closeSession(active, now, client)
            store.save(TimeSession(branch, ticket, now, now))
            return
        }

        val resolvedTicket = ticket ?: active.ticket
        store.save(active.copy(ticket = resolvedTicket, lastActivityTime = now))
    }

    private fun currentBranch(invocation: Invocation): String? {
        val result = invocation.captureGit("rev-parse", "--abbrev-ref", "HEAD")
        if (result.exitCode != 0) {
            Environment.debug { "Unable to resolve branch: ${result.stderr}" }
            return null
        }
        val branch = result.stdout.trim()
        return branch.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    private fun closeSession(
        session: TimeSession,
        end: Instant,
        client: ExternalClient
    ) {
        val duration = session.durationUntil(end)
        if (duration <= 0) return
        val snapshot = SessionSnapshot(
            branch = session.branch,
            ticket = session.ticket,
            startTime = session.startTime,
            endTime = end,
            durationMs = duration
        )
        client.trackSession(snapshot)
    }

    private fun durationBetween(
        start: Instant,
        end: Instant
    ): Duration = end - start
}
