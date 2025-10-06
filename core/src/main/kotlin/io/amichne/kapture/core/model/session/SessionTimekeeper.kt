package io.amichne.kapture.core.model.session

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SessionTimekeeper(
    val branch: String,
    val task: String?,
    val startTime: Instant,
    val lastActivityTime: Instant
) {
    /** Returns a new session with the `lastActivityTime` advanced to `now`. */
    fun withActivity(now: Instant): SessionTimekeeper = copy(lastActivityTime = now)

    /**
     * Calculates the elapsed milliseconds between the session start and the
     * supplied end instant; negative values indicate a clock skew upstream.
     */
    fun durationUntil(end: Instant): Long = end.toEpochMilliseconds() - startTime.toEpochMilliseconds()
}
