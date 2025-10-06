package io.amichne.kapture.core.http

import io.amichne.kapture.core.http.adapter.Adapter
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

    override fun close() {
        calls.add(AdapterCall.Close)
    }

    fun reset() {
        calls.clear()
    }

    sealed class AdapterCall {
        data class GetTicketStatus(val ticketId: String) : AdapterCall()
        data class TrackSession(val snapshot: SessionSnapshot) : AdapterCall()
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

    override fun close() {
        // No-op
    }
}
