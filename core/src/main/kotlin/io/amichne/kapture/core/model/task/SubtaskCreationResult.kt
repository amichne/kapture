package io.amichne.kapture.core.model.task

sealed class SubtaskCreationResult {
    data class Success(val subtaskKey: String) : SubtaskCreationResult()
    data class Failure(val message: String) : SubtaskCreationResult()
}
