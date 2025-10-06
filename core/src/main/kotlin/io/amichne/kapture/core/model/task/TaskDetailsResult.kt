package io.amichne.kapture.core.model.task

sealed class TaskDetailsResult {
    data class Success(val key: String, val summary: String, val description: String, val parentKey: String?) : TaskDetailsResult()
    data class Failure(val message: String) : TaskDetailsResult()
}
