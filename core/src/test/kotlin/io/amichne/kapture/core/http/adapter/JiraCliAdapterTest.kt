package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.exec.Exec
import io.amichne.kapture.core.exec.ExecResult
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.SessionSnapshot
import io.mockk.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraCliAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        mockkObject(Exec)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Exec)
    }

    @Test
    fun `getTicketStatus returns Found when CLI returns valid JSON`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "In Progress"
                    }
                }
            }
        """.trimIndent()

        every {
            Exec.capture(
                cmd = listOf("jira", "issue", "view", "TEST-123", "--output", "json"),
                env = any(),
                timeoutSeconds = 15
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.Found("In Progress"), result)
    }

    @Test
    fun `getTicketStatus returns NotFound for blank ticket ID`() {
        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("")

        assertEquals(TicketLookupResult.NotFound, result)
        verify(exactly = 0) { Exec.capture(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getTicketStatus returns NotFound for whitespace ticket ID`() {
        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("   ")

        assertEquals(TicketLookupResult.NotFound, result)
        verify(exactly = 0) { Exec.capture(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getTicketStatus returns Error when CLI exits with non-zero code`() {
        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 1, stdout = "", stderr = "Error: ticket not found")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("MISSING-999")

        assertTrue(result is TicketLookupResult.Error)
        assertTrue((result as TicketLookupResult.Error).message.contains("exit 1"))
    }

    @Test
    fun `getTicketStatus returns NotFound when fields are missing`() {
        val invalidResponse = """
            {
                "key": "TEST-123"
            }
        """.trimIndent()

        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.NotFound, result)
    }

    @Test
    fun `getTicketStatus returns NotFound when status is missing`() {
        val invalidResponse = """
            {
                "fields": {
                    "summary": "Test issue"
                }
            }
        """.trimIndent()

        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.NotFound, result)
    }

    @Test
    fun `getTicketStatus returns NotFound when name is missing`() {
        val invalidResponse = """
            {
                "fields": {
                    "status": {
                        "id": "10001"
                    }
                }
            }
        """.trimIndent()

        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.NotFound, result)
    }

    @Test
    fun `getTicketStatus returns NotFound when status name is empty`() {
        val invalidResponse = """
            {
                "fields": {
                    "status": {
                        "name": ""
                    }
                }
            }
        """.trimIndent()

        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertEquals(TicketLookupResult.NotFound, result)
    }

    @Test
    fun `getTicketStatus returns Error when JSON is malformed`() {
        val malformedResponse = "{invalid json"

        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = malformedResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result = adapter.getTicketStatus("TEST-123")

        assertTrue(result is TicketLookupResult.Error)
    }

    @Test
    fun `getTicketStatus uses custom executable when configured`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "Done"
                    }
                }
            }
        """.trimIndent()

        val commandSlot = slot<List<String>>()
        every {
            Exec.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli(executable = "/usr/local/bin/jira")
        val adapter = JiraCliAdapter(integration, json)

        adapter.getTicketStatus("PROJ-456")

        assertEquals("/usr/local/bin/jira", commandSlot.captured[0])
    }

    @Test
    fun `getTicketStatus uses default executable when not configured`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "Done"
                    }
                }
            }
        """.trimIndent()

        val commandSlot = slot<List<String>>()
        every {
            Exec.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli(executable = "")
        val adapter = JiraCliAdapter(integration, json)

        adapter.getTicketStatus("PROJ-456")

        assertEquals("jira", commandSlot.captured[0])
    }

    @Test
    fun `getTicketStatus passes environment variables to CLI`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "In Review"
                    }
                }
            }
        """.trimIndent()

        val envSlot = slot<Map<String, String>>()
        every {
            Exec.capture(
                cmd = any(),
                env = capture(envSlot),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val environment = mapOf(
            "JIRA_API_TOKEN" to "test-token",
            "JIRA_SITE" to "https://example.atlassian.net"
        )
        val integration = ExternalIntegration.JiraCli(environment = environment)
        val adapter = JiraCliAdapter(integration, json)

        adapter.getTicketStatus("TEST-123")

        assertEquals(environment, envSlot.captured)
    }

    @Test
    fun `getTicketStatus uses custom timeout when configured`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "Done"
                    }
                }
            }
        """.trimIndent()

        val timeoutSlot = slot<Long>()
        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = capture(timeoutSlot)
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli(timeoutSeconds = 30)
        val adapter = JiraCliAdapter(integration, json)

        adapter.getTicketStatus("TEST-123")

        assertEquals(30L, timeoutSlot.captured)
    }

    @Test
    fun `getTicketStatus constructs correct command`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "Backlog"
                    }
                }
            }
        """.trimIndent()

        val commandSlot = slot<List<String>>()
        every {
            Exec.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        adapter.getTicketStatus("MYPROJ-789")

        val command = commandSlot.captured
        assertEquals(listOf("jira", "issue", "view", "MYPROJ-789", "--output", "json"), command)
    }

    @Test
    fun `trackSession does nothing and logs debug message`() {
        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            ticket = "TEST-123",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = Instant.fromEpochMilliseconds(2000),
            durationMs = 1000
        )

        // Should not throw and should not invoke Exec
        adapter.trackSession(snapshot)

        verify(exactly = 0) { Exec.capture(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { Exec.passthrough(any(), any(), any(), any()) }
    }

    @Test
    fun `close does not throw`() {
        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        adapter.close()

        // Should complete without errors
    }

    @Test
    fun `multiple getTicketStatus calls work correctly`() {
        val responses = listOf(
            """{"fields": {"status": {"name": "To Do"}}}""",
            """{"fields": {"status": {"name": "In Progress"}}}""",
            """{"fields": {"status": {"name": "Done"}}}"""
        )

        var callCount = 0
        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } answers {
            ExecResult(exitCode = 0, stdout = responses[callCount++], stderr = "")
        }

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        val result1 = adapter.getTicketStatus("TEST-1")
        val result2 = adapter.getTicketStatus("TEST-2")
        val result3 = adapter.getTicketStatus("TEST-3")

        assertEquals(TicketLookupResult.Found("To Do"), result1)
        assertEquals(TicketLookupResult.Found("In Progress"), result2)
        assertEquals(TicketLookupResult.Found("Done"), result3)
        assertEquals(3, callCount)
    }

    @Test
    fun `getTicketStatus handles various Jira ticket ID formats`() {
        val validResponse = """
            {
                "fields": {
                    "status": {
                        "name": "In Progress"
                    }
                }
            }
        """.trimIndent()

        val commandSlot = mutableListOf<List<String>>()
        every {
            Exec.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns ExecResult(exitCode = 0, stdout = validResponse, stderr = "")

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        // Test various ticket formats
        adapter.getTicketStatus("PROJ-1")
        adapter.getTicketStatus("ABC-12345")
        adapter.getTicketStatus("X-1")

        assertEquals("PROJ-1", commandSlot[0][3])
        assertEquals("ABC-12345", commandSlot[1][3])
        assertEquals("X-1", commandSlot[2][3])
    }

    @Test
    fun `getTicketStatus handles status names with spaces and special characters`() {
        val statuses = listOf(
            "In Progress",
            "Ready for Review",
            "Done / Closed",
            "Waiting-For-Feedback"
        )

        var callCount = 0
        every {
            Exec.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } answers {
            val status = statuses[callCount++]
            val response = """{"fields": {"status": {"name": "$status"}}}"""
            ExecResult(exitCode = 0, stdout = response, stderr = "")
        }

        val integration = ExternalIntegration.JiraCli()
        val adapter = JiraCliAdapter(integration, json)

        statuses.forEach { expectedStatus ->
            val result = adapter.getTicketStatus("TEST-123")
            assertEquals(TicketLookupResult.Found(expectedStatus), result)
        }
    }
}
