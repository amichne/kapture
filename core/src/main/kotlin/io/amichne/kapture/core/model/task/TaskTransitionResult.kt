package io.amichne.kapture.core.model.task

sealed class TaskTransitionResult {
    object Success : TaskTransitionResult()
    data class Failure(val message: String) : TaskTransitionResult()
}
