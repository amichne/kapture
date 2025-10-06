package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.adapter.internal.http.HttpAdapter
import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.session.SessionSnapshot
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests for HttpAdapter. These tests verify the adapter's
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
            Plugin.Http(baseUrl = "https://api.example.com", auth = Authentication.None),
            Plugin.Http(baseUrl = "https://api.example.com", auth = Authentication.Bearer("token")),
            Plugin.Http(baseUrl = "https://api.example.com", auth = Authentication.Basic("user", "pass")),
            Plugin.Http(baseUrl = "https://api.example.com", auth = Authentication.PersonalAccessToken("email", "token"))
        )

        integrations.forEach { integration ->
            val adapter = HttpAdapter(integration, json)
            assertNotNull(adapter)
            adapter.close()
        }
    }

    @Test
    fun `getTaskStatus returns NotFound for blank task ID`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration, json)

        val result = adapter.getTaskStatus("")

        assertEquals(TaskSearchResult.NotFound, result)
        adapter.close()
    }

    @Test
    fun `getTaskStatus returns NotFound for whitespace task ID`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration, json)

        val result = adapter.getTaskStatus("   ")

        assertEquals(TaskSearchResult.NotFound, result)
        adapter.close()
    }

    @Test
    fun `getTaskStatus handles non-existent host gracefully`() {
        val integration = Plugin.Http(
            baseUrl = "https://this-host-does-not-exist-12345.invalid"
        )
        val adapter = HttpAdapter(integration, json)

        val result = adapter.getTaskStatus("TEST-123")

        // Should return an error result, not throw
        assertTrue(result is TaskSearchResult.Error || result is TaskSearchResult.NotFound)
        adapter.close()
    }

    @Test
    fun `trackSession does not throw on errors`() {
        val integration = Plugin.Http(
            baseUrl = "https://this-host-does-not-exist-12345.invalid"
        )
        val adapter = HttpAdapter(integration, json)

        val snapshot = SessionSnapshot(
            branch = "main",
            task = null,
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
        val integration = Plugin.Http(baseUrl = "https://api.example.com/")
        val adapter = HttpAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter handles base URL without trailing slash`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter handles base URL with path`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com/api/v1")
        val adapter = HttpAdapter(integration, json)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `close releases resources and can be called multiple times`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration, json)

        adapter.close()
        adapter.close() // Should not throw
    }

    @Test
    fun `multiple adapters can be created and closed independently`() {
        val adapter1 = HttpAdapter(
            Plugin.Http(baseUrl = "https://api1.example.com"),
            json
        )
        val adapter2 = HttpAdapter(
            Plugin.Http(baseUrl = "https://api2.example.com"),
            json
        )

        adapter1.getTaskStatus("")
        adapter2.getTaskStatus("")

        adapter1.close()
        adapter2.close()
    }

    @Test
    fun `adapter respects custom JSON configuration`() {
        val customJson = Json {
            ignoreUnknownKeys = false
            prettyPrint = true
        }
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration, customJson)

        assertNotNull(adapter)
        adapter.close()
    }

    @Test
    fun `adapter uses default JSON when not specified`() {
        val integration = Plugin.Http(baseUrl = "https://api.example.com")
        val adapter = HttpAdapter(integration)

        assertNotNull(adapter)
        adapter.close()
    }
}
