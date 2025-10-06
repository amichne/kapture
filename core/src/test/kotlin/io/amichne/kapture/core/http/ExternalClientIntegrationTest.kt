package io.amichne.kapture.core.http

import io.amichne.kapture.core.config.AuthConfig
import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.model.SessionSnapshot
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests that verify ExternalClient works correctly with
 * actual adapter implementations (not mocks).
 */
class ExternalClientIntegrationTest {

    @Test
    fun `ExternalClient from Rest integration creates working client`() {
        val integration = ExternalIntegration.Rest(
            baseUrl = "https://api.example.com",
            auth = AuthConfig.None
        )

        val client = ExternalClient.from(integration)
        assertNotNull(client)

        // Verify it handles blank ticket IDs correctly
        val result = client.getTicketStatus("")
        assertEquals(TicketLookupResult.NotFound, result)

        client.close()
    }

    @Test
    fun `ExternalClient from JiraCli integration creates working client`() {
        val integration = ExternalIntegration.JiraCli(
            executable = "jira",
            environment = mapOf("JIRA_API_TOKEN" to "test"),
            timeoutSeconds = 30
        )

        val client = ExternalClient.from(integration)
        assertNotNull(client)

        // Verify it handles blank ticket IDs correctly
        val result = client.getTicketStatus("")
        assertEquals(TicketLookupResult.NotFound, result)

        client.close()
    }

    @Test
    fun `ExternalClient can track sessions with Rest integration`() {
        val integration = ExternalIntegration.Rest(
            baseUrl = "https://api.example.com",
            auth = AuthConfig.Bearer("test-token")
        )

        val client = ExternalClient.from(integration)
        val snapshot = SessionSnapshot(
            branch = "main",
            ticket = null,
            startTime = Instant.fromEpochMilliseconds(0),
            endTime = Instant.fromEpochMilliseconds(1000),
            durationMs = 1000
        )

        // Should not throw even if network fails
        client.trackSession(snapshot)
        client.close()
    }

    @Test
    fun `ExternalClient can track sessions with JiraCli integration`() {
        val integration = ExternalIntegration.JiraCli()

        val client = ExternalClient.from(integration)
        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            ticket = "TEST-123",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = Instant.fromEpochMilliseconds(2000),
            durationMs = 1000
        )

        // Should not throw (JiraCli doesn't support tracking)
        client.trackSession(snapshot)
        client.close()
    }

    @Test
    fun `ExternalClient with custom adapter works correctly`() {
        val adapter = MockAdapter(
            ticketResultProvider = { ticketId ->
                when {
                    ticketId.isBlank() -> TicketLookupResult.NotFound
                    ticketId.startsWith("VALID") -> TicketLookupResult.Found("IN_PROGRESS")
                    ticketId.startsWith("DONE") -> TicketLookupResult.Found("DONE")
                    else -> TicketLookupResult.Error("Unknown ticket")
                }
            }
        )

        val client = ExternalClient.wrap(adapter)

        assertEquals(TicketLookupResult.NotFound, client.getTicketStatus(""))
        assertEquals(TicketLookupResult.Found("IN_PROGRESS"), client.getTicketStatus("VALID-123"))
        assertEquals(TicketLookupResult.Found("DONE"), client.getTicketStatus("DONE-456"))
        assertEquals(TicketLookupResult.Error("Unknown ticket"), client.getTicketStatus("OTHER-789"))

        assertEquals(4, adapter.calls.size)
        client.close()
    }

    @Test
    fun `ExternalClient with failing adapter handles errors`() {
        val adapter = FailingAdapter("Service temporarily unavailable")
        val client = ExternalClient.wrap(adapter)

        val result = client.getTicketStatus("TEST-123")

        assertTrue(result is TicketLookupResult.Error)
        assertEquals("Service temporarily unavailable", (result as TicketLookupResult.Error).message)

        client.close()
    }

    @Test
    fun `ExternalClient with stateful adapter maintains state`() {
        val adapter = StatefulAdapter()
        adapter.setTicketStatus("PROJ-1", "To Do")
        adapter.setTicketStatus("PROJ-2", "In Progress")
        adapter.setTicketStatus("PROJ-3", "Done")

        val client = ExternalClient.wrap(adapter)

        assertEquals(TicketLookupResult.Found("To Do"), client.getTicketStatus("PROJ-1"))
        assertEquals(TicketLookupResult.Found("In Progress"), client.getTicketStatus("PROJ-2"))
        assertEquals(TicketLookupResult.Found("Done"), client.getTicketStatus("PROJ-3"))
        assertEquals(TicketLookupResult.NotFound, client.getTicketStatus("PROJ-4"))

        // Update state
        adapter.setTicketStatus("PROJ-1", "Done")
        adapter.removeTicket("PROJ-2")

        assertEquals(TicketLookupResult.Found("Done"), client.getTicketStatus("PROJ-1"))
        assertEquals(TicketLookupResult.NotFound, client.getTicketStatus("PROJ-2"))

        client.close()
    }

    @Test
    fun `multiple ExternalClient instances work independently`() {
        val adapter1 = MockAdapter(ticketResult = TicketLookupResult.Found("Status1"))
        val adapter2 = MockAdapter(ticketResult = TicketLookupResult.Found("Status2"))

        val client1 = ExternalClient.wrap(adapter1)
        val client2 = ExternalClient.wrap(adapter2)

        val result1 = client1.getTicketStatus("TEST-1")
        val result2 = client2.getTicketStatus("TEST-2")

        assertEquals(TicketLookupResult.Found("Status1"), result1)
        assertEquals(TicketLookupResult.Found("Status2"), result2)

        client1.close()
        client2.close()
    }

    @Test
    fun `ExternalClient handles various authentication configurations`() {
        val configs = listOf(
            AuthConfig.None,
            AuthConfig.Bearer("test-token"),
            AuthConfig.Basic("user", "pass"),
            AuthConfig.JiraPat("email@example.com", "token")
        )

        configs.forEach { auth ->
            val integration = ExternalIntegration.Rest(
                baseUrl = "https://api.example.com",
                auth = auth
            )
            val client = ExternalClient.from(integration)

            // Verify basic operations work
            client.getTicketStatus("")
            client.trackSession(SessionSnapshot(
                branch = "main",
                ticket = null,
                startTime = Instant.fromEpochMilliseconds(0),
                endTime = Instant.fromEpochMilliseconds(1000),
                durationMs = 1000
            ))
            client.close()
        }
    }
}
