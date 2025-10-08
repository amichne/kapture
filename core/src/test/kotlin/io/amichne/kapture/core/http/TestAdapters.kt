package io.amichne.kapture.core.http

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskStatus
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.test.foundStatus

/**
 * Mock adapter for testing that tracks all invocations and allows
 * configurable responses.
 */
class MockAdapter(
    private val taskResult: TaskSearchResult = foundStatus("In Progress"),
    private val taskResultProvider: ((String) -> TaskSearchResult)? = null
) : Adapter {
    val calls = mutableListOf<AdapterCall>()

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        calls.add(AdapterCall.GetTaskStatus(taskId))
        val provided = taskResultProvider?.invoke(taskId)
        if (provided != null) return provided
        return when (taskResult) {
            is TaskSearchResult.Found -> TaskSearchResult.Found(taskResult.status.copy(key = taskId))
            else -> taskResult
        }
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        calls.add(AdapterCall.TrackSession(snapshot))
    }

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult {
        calls.add(AdapterCall.CreateSubtask(parentId, title))
        return SubtaskCreationResult.Success("${parentId}-1")
    }

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult {
        calls.add(AdapterCall.TransitionTask(taskId, targetStatus))
        return TaskTransitionResult.Success
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult {
        calls.add(AdapterCall.GetTaskDetails(taskId))
        return TaskDetailsResult.Success(taskId, "Test summary", "Test description", null)
    }

    override fun close() {
        calls.add(AdapterCall.Close)
    }

    fun reset() {
        calls.clear()
    }

    sealed class AdapterCall {
        data class GetTaskStatus(val taskId: String) : AdapterCall()
        data class TrackSession(val snapshot: SessionSnapshot) : AdapterCall()
        data class CreateSubtask(
            val parentId: String,
            val title: String?
        ) : AdapterCall()

        data class TransitionTask(
            val taskId: String,
            val targetStatus: String
        ) : AdapterCall()

        data class GetTaskDetails(val taskId: String) : AdapterCall()
        data object Close : AdapterCall()
    }
}

/**
 * Adapter that always fails with a specific error message.
 */
class FailingAdapter(
    private val errorMessage: String = "Plugin unavailable"
) : Adapter {
    override fun getTaskStatus(taskId: String): TaskSearchResult =
        TaskSearchResult.Error(errorMessage)

    override fun trackSession(snapshot: SessionSnapshot) {
        // No-op for failing adapter
    }

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult =
        SubtaskCreationResult.Failure(errorMessage)

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult =
        TaskTransitionResult.Failure(errorMessage)

    override fun getTaskDetails(taskId: String): TaskDetailsResult =
        TaskDetailsResult.Failure(errorMessage)

    override fun close() {
        // No-op
    }
}

/**
 * Adapter that simulates various task statuses for testing.
 */
class StatefulAdapter : Adapter {
    private val taskStatuses = mutableMapOf<String, TaskStatus>()

    fun setTaskStatus(
        taskId: String,
        status: String
    ) {
        taskStatuses[taskId] = TaskStatus(
            provider = "stateful",
            key = taskId,
            raw = status,
            internal = inferInternal(status)
        )
    }

    fun removeTask(taskId: String) {
        taskStatuses.remove(taskId)
    }

    fun clear() {
        taskStatuses.clear()
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        return taskStatuses[taskId]?.let { TaskSearchResult.Found(it) } ?: TaskSearchResult.NotFound
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        // No-op for stateful adapter
    }

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult =
        SubtaskCreationResult.Success("${parentId}-1")

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult {
        setTaskStatus(taskId, targetStatus)
        return TaskTransitionResult.Success
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult =
        TaskDetailsResult.Success(taskId, "Test summary", "Test description", null)

    override fun close() {
        // No-op
    }
}

private fun inferInternal(rawStatus: String?): InternalStatus? {
    if (rawStatus.isNullOrBlank()) return null
    val canonical = rawStatus.trim().replace("\\s+".toRegex(), "_").uppercase()
    return when {
        canonical.contains("BLOCK") -> InternalStatus.BLOCKED
        canonical.contains("REVIEW") -> InternalStatus.REVIEW
        canonical.contains("PROGRESS") -> InternalStatus.IN_PROGRESS
        canonical.contains("DONE") || canonical.contains("CLOSE") -> InternalStatus.DONE
        canonical.contains("READY") || canonical.contains("TODO") || canonical.contains("OPEN") -> InternalStatus.TODO
        else -> null
    }
}
