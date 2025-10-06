package io.amichne.kapture.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TimeSession(
    val branch: String,
    val ticket: String?,
    val startTime: Instant,
    val lastActivityTime: Instant
) {
    fun withActivity(now: Instant): TimeSession = copy(lastActivityTime = now)

    fun durationUntil(end: Instant): Long = end.toEpochMilliseconds() - startTime.toEpochMilliseconds()
}

@Serializable
data class SessionSnapshot(
    val branch: String,
    val ticket: String?,
    val startTime: Instant,
    val endTime: Instant,
    val durationMs: Long
)
