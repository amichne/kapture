package io.amichne.kapture.core.model.task

sealed class TaskSearchResult {
    data class Found(val status: String) : TaskSearchResult()
    object NotFound : TaskSearchResult()
    data class Error(val message: String) : TaskSearchResult()
}
