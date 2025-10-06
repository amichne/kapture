package io.amichne.kapture.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshot(
    val branch: String,
    val ticket: String?,
    val startTime: Instant,
    val endTime: Instant,
    val durationMs: Long
)
