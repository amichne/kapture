package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.http.TicketLookupResult

interface Adapter : AutoCloseable {
    fun getTicketStatus(ticketId: String): TicketLookupResult
    fun trackSession(snapshot: SessionSnapshot)
}
