package io.amichne.kapture.core.model.task

/**
 * Canonical internal status values used across enforcement policies. Additional
 * values may be added over time; callers should avoid relying on ordinal
 * positions and instead compare by name.
 */
enum class InternalStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,
    BLOCKED,
    DONE
}
