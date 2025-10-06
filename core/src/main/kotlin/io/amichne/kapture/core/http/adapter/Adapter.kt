package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.http.TicketLookupResult

interface Adapter : AutoCloseable {
    fun getTicketStatus(ticketId: String): TicketLookupResult
    fun trackSession(snapshot: SessionSnapshot)

    /**
     * Creates a subtask under the specified parent issue.
     * @param parentId The parent issue key (e.g., "PROJ-123")
     * @param title The subtask title (optional - will prompt if not provided)
     * @return SubtaskCreationResult containing the created subtask key or error
     */
    fun createSubtask(parentId: String, title: String? = null): SubtaskCreationResult

    /**
     * Transitions an issue to a new status.
     * @param issueId The issue key to transition
     * @param targetStatus The target status name (e.g., "In Progress")
     * @return TransitionResult containing success/failure information
     */
    fun transitionIssue(issueId: String, targetStatus: String): TransitionResult

    /**
     * Retrieves full issue details including description and links.
     * @param issueId The issue key
     * @return IssueDetails or null if not found
     */
    fun getIssueDetails(issueId: String): IssueDetailsResult
}

sealed class SubtaskCreationResult {
    data class Success(val subtaskKey: String) : SubtaskCreationResult()
    data class Failure(val message: String) : SubtaskCreationResult()
}

sealed class TransitionResult {
    object Success : TransitionResult()
    data class Failure(val message: String) : TransitionResult()
}

sealed class IssueDetailsResult {
    data class Success(val key: String, val summary: String, val description: String, val parentKey: String?) : IssueDetailsResult()
    data class Failure(val message: String) : IssueDetailsResult()
}
