package io.amichne.kapture.core.model.task

/**
 * Represents a task status as returned by an external provider, including the
 * normalized internal status used for enforcement decisions.
 */
data class TaskStatus(
    val provider: String,
    val key: String,
    val raw: String?,
    val internal: InternalStatus? = null
)
