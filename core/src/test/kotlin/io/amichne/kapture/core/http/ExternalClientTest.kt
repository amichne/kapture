package io.amichne.kapture.core.http

import io.amichne.kapture.core.config.AuthConfig
import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.model.SessionSnapshot
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExternalClientTest {

    @Test
    fun `wrap creates client with custom adapter`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)

        val result = client.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.Found("IN_PROGRESS"), result)
        assertEquals(1, adapter.getTicketStatusCallCount)
    }

    @Test
    fun `getTicketStatus delegates to adapter`() {
        val adapter = TestAdapter(ticketResult = TicketLookupResult.NotFound)
        val client = ExternalClient.wrap(adapter)

        val result = client.getTicketStatus("MISSING-999")

        assertEquals(TicketLookupResult.NotFound, result)
        assertEquals(1, adapter.getTicketStatusCallCount)
    }

    @Test
    fun `getTicketStatus handles error result from adapter`() {
        val adapter = TestAdapter(ticketResult = TicketLookupResult.Error("timeout"))
        val client = ExternalClient.wrap(adapter)

        val result = client.getTicketStatus("TEST-456")

        assertEquals(TicketLookupResult.Error("timeout"), result)
    }

    @Test
    fun `trackSession delegates to adapter`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)
        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            ticket = "TEST-123",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = Instant.fromEpochMilliseconds(2000),
            durationMs = 1000
        )

        client.trackSession(snapshot)

        assertEquals(1, adapter.trackSessionCallCount)
        assertEquals(snapshot, adapter.lastSessionSnapshot)
    }

    @Test
    fun `close delegates to adapter`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)

        client.close()

        assertEquals(1, adapter.closeCallCount)
    }

    @Test
    fun `from creates RestAdapter for Rest integration`() {
        val integration = ExternalIntegration.Rest(
            baseUrl = "https://api.example.com",
            auth = AuthConfig.None
        )

        val client = ExternalClient.from(integration)

        // Verify it's wrapped properly by closing it
        client.close()
    }

    @Test
    fun `from creates JiraCliAdapter for JiraCli integration`() {
        val integration = ExternalIntegration.JiraCli(
            executable = "jira",
            environment = mapOf("JIRA_API_TOKEN" to "test-token"),
            timeoutSeconds = 30
        )

        val client = ExternalClient.from(integration)

        // Verify it's wrapped properly by closing it
        client.close()
    }

    @Test
    fun `multiple operations work correctly`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)

        client.getTicketStatus("TICKET-1")
        client.getTicketStatus("TICKET-2")
        val snapshot = SessionSnapshot(
            branch = "main",
            ticket = null,
            startTime = Instant.fromEpochMilliseconds(0),
            endTime = Instant.fromEpochMilliseconds(1000),
            durationMs = 1000
        )
        client.trackSession(snapshot)
        client.close()

        assertEquals(2, adapter.getTicketStatusCallCount)
        assertEquals(1, adapter.trackSessionCallCount)
        assertEquals(1, adapter.closeCallCount)
    }

    private class TestAdapter(
        private val ticketResult: TicketLookupResult = TicketLookupResult.Found("IN_PROGRESS")
    ) : Adapter {
        var getTicketStatusCallCount = 0
        var trackSessionCallCount = 0
        var closeCallCount = 0
        var lastSessionSnapshot: SessionSnapshot? = null

        override fun getTicketStatus(ticketId: String): TicketLookupResult {
            getTicketStatusCallCount++
            return ticketResult
        }

        override fun trackSession(snapshot: SessionSnapshot) {
            trackSessionCallCount++
            lastSessionSnapshot = snapshot
        }

        override fun close() {
            closeCallCount++
        }
    }
}
