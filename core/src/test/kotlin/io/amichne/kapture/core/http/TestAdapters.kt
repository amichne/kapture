package io.amichne.kapture.core.http

import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.http.adapter.SubtaskCreationResult
import io.amichne.kapture.core.http.adapter.TransitionResult
import io.amichne.kapture.core.http.adapter.IssueDetailsResult
import io.amichne.kapture.core.model.SessionSnapshot

/**
 * Mock adapter for testing that tracks all invocations and allows
 * configurable responses.
 */
class MockAdapter(
    private val ticketResult: TicketLookupResult = TicketLookupResult.Found("IN_PROGRESS"),
    private val ticketResultProvider: ((String) -> TicketLookupResult)? = null
) : Adapter {
    val calls = mutableListOf<AdapterCall>()

    override fun getTicketStatus(ticketId: String): TicketLookupResult {
        calls.add(AdapterCall.GetTicketStatus(ticketId))
        return ticketResultProvider?.invoke(ticketId) ?: ticketResult
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        calls.add(AdapterCall.TrackSession(snapshot))
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult {
        calls.add(AdapterCall.CreateSubtask(parentId, title))
        return SubtaskCreationResult.Success("${parentId}-1")
    }

    override fun transitionIssue(issueId: String, targetStatus: String): TransitionResult {
        calls.add(AdapterCall.TransitionIssue(issueId, targetStatus))
        return TransitionResult.Success
    }

    override fun getIssueDetails(issueId: String): IssueDetailsResult {
        calls.add(AdapterCall.GetIssueDetails(issueId))
        return IssueDetailsResult.Success(issueId, "Test summary", "Test description", null)
    }

    override fun close() {
        calls.add(AdapterCall.Close)
    }

    fun reset() {
        calls.clear()
    }

    sealed class AdapterCall {
        data class GetTicketStatus(val ticketId: String) : AdapterCall()
        data class TrackSession(val snapshot: SessionSnapshot) : AdapterCall()
        data class CreateSubtask(val parentId: String, val title: String?) : AdapterCall()
        data class TransitionIssue(val issueId: String, val targetStatus: String) : AdapterCall()
        data class GetIssueDetails(val issueId: String) : AdapterCall()
        data object Close : AdapterCall()
    }
}

/**
 * Adapter that always fails with a specific error message.
 */
class FailingAdapter(
    private val errorMessage: String = "Service unavailable"
) : Adapter {
    override fun getTicketStatus(ticketId: String): TicketLookupResult =
        TicketLookupResult.Error(errorMessage)

    override fun trackSession(snapshot: SessionSnapshot) {
        // No-op for failing adapter
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult =
        SubtaskCreationResult.Failure(errorMessage)

    override fun transitionIssue(issueId: String, targetStatus: String): TransitionResult =
        TransitionResult.Failure(errorMessage)

    override fun getIssueDetails(issueId: String): IssueDetailsResult =
        IssueDetailsResult.Failure(errorMessage)

    override fun close() {
        // No-op
    }
}

/**
 * Adapter that simulates various ticket statuses for testing.
 */
class StatefulAdapter : Adapter {
    private val ticketStatuses = mutableMapOf<String, String>()

    fun setTicketStatus(ticketId: String, status: String) {
        ticketStatuses[ticketId] = status
    }

    fun removeTicket(ticketId: String) {
        ticketStatuses.remove(ticketId)
    }

    fun clear() {
        ticketStatuses.clear()
    }

    override fun getTicketStatus(ticketId: String): TicketLookupResult {
        return when (val status = ticketStatuses[ticketId]) {
            null -> TicketLookupResult.NotFound
            else -> TicketLookupResult.Found(status)
        }
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        // No-op for stateful adapter
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult =
        SubtaskCreationResult.Success("${parentId}-1")

    override fun transitionIssue(issueId: String, targetStatus: String): TransitionResult {
        ticketStatuses[issueId] = targetStatus
        return TransitionResult.Success
    }

    override fun getIssueDetails(issueId: String): IssueDetailsResult =
        IssueDetailsResult.Success(issueId, "Test summary", "Test description", null)

    override fun close() {
        // No-op
    }
}
