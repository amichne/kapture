package io.amichne.kapture.core.model.session

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshot(
    val branch: String,
    val task: String?,
    val startTime: Instant,
    val endTime: Instant,
    val durationMs: Long
)
