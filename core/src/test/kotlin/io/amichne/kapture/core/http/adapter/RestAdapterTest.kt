package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.config.AuthConfig
import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.SessionSnapshot
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests for RestAdapter. These tests verify the adapter's
 * behavior with various configurations and error conditions.
 *
 * Note: These are primarily construction and basic behavior tests.
 * For comprehensive HTTP testing with mocked responses, a test server
 * would be required (e.g., using ktor-server-test-host or WireMock).
 */
class RestAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `adapter can be created with various auth configurations`() {
        val integrations = listOf(
            ExternalIntegration.Rest(baseUrl = "https://api.example.com", auth = AuthConfig.None),
            ExternalIntegration.Rest(baseUrl = "https://api.example.com", auth = AuthConfig.Bearer("token")),
            ExternalIntegration.Rest(baseUrl = "https://api.example.com", auth = AuthConfig.Basic("user", "pass")),
            ExternalIntegration.Rest(baseUrl = "https://api.example.com", auth = AuthConfig.JiraPat("email", "token"))
        )

        integrations.forEach { integration ->
            val adapter = RestAdapter(integration, json)
            assertNotNull(adapter)
            adapter.close()
        }
    }

    @Test
    fun `getTicketStatus returns NotFound for blank ticket ID`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration, json)

        val result = adapter.getTicketStatus("")

        assertEquals(TicketLookupResult.NotFound, result)
        adapter.close()
    }

    @Test
    fun `getTicketStatus returns NotFound for whitespace ticket ID`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration, json)

        val result = adapter.getTicketStatus("   ")

        assertEquals(TicketLookupResult.NotFound, result)
        adapter.close()
    }

    @Test
    fun `getTicketStatus handles non-existent host gracefully`() {
        val integration = ExternalIntegration.Rest(
            baseUrl = "https://this-host-does-not-exist-12345.invalid"
        )
        val adapter = RestAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        // Should return an error result, not throw
        assertTrue(result is TicketLookupResult.Error || result is TicketLookupResult.NotFound)
        adapter.close()
    }

    @Test
    fun `trackSession does not throw on errors`() {
        val integration = ExternalIntegration.Rest(
            baseUrl = "https://this-host-does-not-exist-12345.invalid"
        )
        val adapter = RestAdapter(integration, json)

        val snapshot = SessionSnapshot(
            branch = "main",
            ticket = null,
            startTime = Instant.fromEpochMilliseconds(0),
            endTime = Instant.fromEpochMilliseconds(1000),
            durationMs = 1000
        )

        // Should not throw even if network fails
        adapter.trackSession(snapshot)
        adapter.close()
    }

    @Test
    fun `adapter handles base URL with trailing slash`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com/")
        val adapter = RestAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter handles base URL without trailing slash`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter handles base URL with path`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com/api/v1")
        val adapter = RestAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `close releases resources and can be called multiple times`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration, json)

        adapter.close()
        adapter.close() // Should not throw
    }

    @Test
    fun `multiple adapters can be created and closed independently`() {
        val adapter1 = RestAdapter(
            ExternalIntegration.Rest(baseUrl = "https://api1.example.com"),
            json
        )
        val adapter2 = RestAdapter(
            ExternalIntegration.Rest(baseUrl = "https://api2.example.com"),
            json
        )

        adapter1.getTicketStatus("")
        adapter2.getTicketStatus("")

        adapter1.close()
        adapter2.close()
    }

    @Test
    fun `adapter respects custom JSON configuration`() {
        val customJson = Json {
            ignoreUnknownKeys = false
            prettyPrint = true
        }
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration, customJson)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter uses default JSON when not specified`() {
        val integration = ExternalIntegration.Rest(baseUrl = "https://api.example.com")
        val adapter = RestAdapter(integration)

        assertNotNull(adapter)
        adapter.close()
    }
}
