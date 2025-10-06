package io.amichne.kapture.core.http

import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.http.adapter.JiraCliAdapter
import io.amichne.kapture.core.http.adapter.RestAdapter
import kotlinx.serialization.json.Json

open class ExternalClient<A : Adapter> protected constructor(
    val adapter: A
) : AutoCloseable {
    override fun close() {
        adapter.close()
    }

    /**
     * Looks up the ticket status using the active integration backend. The
     * default implementation delegates to the configured adapter but can be
     * overridden in tests.
     */
    open fun getTicketStatus(ticketId: String): TicketLookupResult = adapter.getTicketStatus(ticketId)

    /**
     * Forwards session tracking to the backend, allowing individual adapters to
     * opt-in or no-op depending on available capabilities.
     */
    open fun trackSession(snapshot: SessionSnapshot) {
        adapter.trackSession(snapshot)
    }

    companion object {
        fun <A : Adapter> wrap(adapter: A): ExternalClient<A> = ExternalClientImpl(adapter)

        fun from(
            integration: ExternalIntegration,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> = when (integration) {
            is ExternalIntegration.Rest -> wrap(RestAdapter(integration, json))
            is ExternalIntegration.JiraCli -> wrap(JiraCliAdapter(integration, json))
        }
    }
}

private class ExternalClientImpl<A : Adapter>(adapter: A) : ExternalClient<A>(adapter)
