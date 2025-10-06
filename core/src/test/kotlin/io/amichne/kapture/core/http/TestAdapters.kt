package io.amichne.kapture.core.http

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult

/**
 * Mock adapter for testing that tracks all invocations and allows
 * configurable responses.
 */
class MockAdapter(
    private val taskResult: TaskSearchResult = TaskSearchResult.Found("IN_PROGRESS"),
    private val taskResultProvider: ((String) -> TaskSearchResult)? = null
) : Adapter {
    val calls = mutableListOf<AdapterCall>()

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        calls.add(AdapterCall.GetTaskStatus(taskId))
        return taskResultProvider?.invoke(taskId) ?: taskResult
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        calls.add(AdapterCall.TrackSession(snapshot))
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult {
        calls.add(AdapterCall.CreateSubtask(parentId, title))
        return SubtaskCreationResult.Success("${parentId}-1")
    }

    override fun transitionTask(taskId: String, targetStatus: String): TaskTransitionResult {
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
        data class CreateSubtask(val parentId: String, val title: String?) : AdapterCall()
        data class TransitionTask(val taskId: String, val targetStatus: String) : AdapterCall()
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

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult =
        SubtaskCreationResult.Failure(errorMessage)

    override fun transitionTask(taskId: String, targetStatus: String): TaskTransitionResult =
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
    private val taskStatuses = mutableMapOf<String, String>()

    fun setTaskStatus(taskId: String, status: String) {
        taskStatuses[taskId] = status
    }

    fun removeTask(taskId: String) {
        taskStatuses.remove(taskId)
    }

    fun clear() {
        taskStatuses.clear()
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        return when (val status = taskStatuses[taskId]) {
            null -> TaskSearchResult.NotFound
            else -> TaskSearchResult.Found(status)
        }
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        // No-op for stateful adapter
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult =
        SubtaskCreationResult.Success("${parentId}-1")

    override fun transitionTask(taskId: String, targetStatus: String): TaskTransitionResult {
        taskStatuses[taskId] = targetStatus
        return TaskTransitionResult.Success
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult =
        TaskDetailsResult.Success(taskId, "Test summary", "Test description", null)

    override fun close() {
        // No-op
    }
}
