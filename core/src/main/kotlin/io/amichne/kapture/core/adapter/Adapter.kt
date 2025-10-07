package io.amichne.kapture.core.adapter

import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskTransitionResult

interface Adapter : AutoCloseable {
    fun getTaskStatus(taskId: String): TaskSearchResult
    fun trackSession(snapshot: SessionSnapshot)

    /**
     * Creates a subtask under the specified parent task.
     * @param parentId The parent task key (e.g., "PROJ-123")
     * @param title The subtask title (optional - will prompt if not provided)
     * @return SubtaskCreationResult containing the created subtask key or error
     */
    fun createSubtask(
        parentId: String,
        title: String? = null
    ): SubtaskCreationResult

    /**
     * Transitions a task to a new status.
     * @param taskId The task key to transition
     * @param targetStatus The target status name (e.g., "In Progress")
     * @return TaskTransitionResult containing success/failure information
     */
    fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult

    /**
     * Retrieves full task details including description and links.
     * @param taskId The task key
     * @return TaskDetails or null if not found
     */
    fun getTaskDetails(taskId: String): TaskDetailsResult
}
