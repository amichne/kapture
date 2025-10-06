package io.amichne.kapture.core.http

sealed class TicketLookupResult {
    data class Found(val status: String) : TicketLookupResult()
    object NotFound : TicketLookupResult()
    data class Error(val message: String) : TicketLookupResult()
}
