package io.amichne.kapture.core.http

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.config.Integration
import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.config.Plugin.Companion.toPlugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult
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
        val integration = Plugin.Http(
            baseUrl = "https://api.example.com",
            auth = Authentication.None
        )

        val client = ExternalClient.from(integration)
        assertNotNull(client)

        // Verify it handles blank task IDs correctly
        val result = client.getTaskStatus("")
        assertEquals(TaskSearchResult.NotFound, result)

        client.close()
    }

    @Test
    fun `ExternalClient from JiraCli integration creates working client`() {
        val integration = Plugin.Cli(
            executable = "jira",
            environment = mapOf("JIRA_API_TOKEN" to "test"),
            timeoutSeconds = 30
        )

        val client = ExternalClient.from(integration)
        assertNotNull(client)

        // Verify it handles blank task IDs correctly
        val result = client.getTaskStatus("")
        assertEquals(TaskSearchResult.NotFound, result)

        client.close()
    }

    @Test
    fun `ExternalClient can track sessions with Rest integration`() {
        val integration = Plugin.Http(
            baseUrl = "https://api.example.com",
            auth = Authentication.Bearer("test-token")
        )

        val client = ExternalClient.from(integration)
        val snapshot = SessionSnapshot(
            branch = "main",
            task = null,
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
        val integration = Integration.Jira

        val client = ExternalClient.from(integration.toPlugin())
        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            task = "TEST-123",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = Instant.fromEpochMilliseconds(2000),
            durationMs = 1000
        )

        // Should not throw (Cli doesn't support tracking)
        client.trackSession(snapshot)
        client.close()
    }

    @Test
    fun `ExternalClient with custom adapter works correctly`() {
        val adapter = MockAdapter(
            taskResultProvider = { taskId ->
                when {
                    taskId.isBlank() -> TaskSearchResult.NotFound
                    taskId.startsWith("VALID") -> TaskSearchResult.Found("IN_PROGRESS")
                    taskId.startsWith("DONE") -> TaskSearchResult.Found("DONE")
                    else -> TaskSearchResult.Error("Unknown task")
                }
            }
        )

        val client = ExternalClient.wrap(adapter)

        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus(""))
        assertEquals(TaskSearchResult.Found("IN_PROGRESS"), client.getTaskStatus("VALID-123"))
        assertEquals(TaskSearchResult.Found("DONE"), client.getTaskStatus("DONE-456"))
        assertEquals(TaskSearchResult.Error("Unknown task"), client.getTaskStatus("OTHER-789"))

        assertEquals(4, adapter.calls.size)
        client.close()
    }

    @Test
    fun `ExternalClient with failing adapter handles errors`() {
        val adapter = FailingAdapter("Plugin temporarily unavailable")
        val client = ExternalClient.wrap(adapter)

        val result = client.getTaskStatus("TEST-123")

        assertTrue(result is TaskSearchResult.Error)
        assertEquals("Plugin temporarily unavailable", (result as TaskSearchResult.Error).message)

        client.close()
    }

    @Test
    fun `ExternalClient with stateful adapter maintains state`() {
        val adapter = StatefulAdapter()
        adapter.setTaskStatus("PROJ-1", "To Do")
        adapter.setTaskStatus("PROJ-2", "In Progress")
        adapter.setTaskStatus("PROJ-3", "Done")

        val client = ExternalClient.wrap(adapter)

        assertEquals(TaskSearchResult.Found("To Do"), client.getTaskStatus("PROJ-1"))
        assertEquals(TaskSearchResult.Found("In Progress"), client.getTaskStatus("PROJ-2"))
        assertEquals(TaskSearchResult.Found("Done"), client.getTaskStatus("PROJ-3"))
        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus("PROJ-4"))

        // Update state
        adapter.setTaskStatus("PROJ-1", "Done")
        adapter.removeTask("PROJ-2")

        assertEquals(TaskSearchResult.Found("Done"), client.getTaskStatus("PROJ-1"))
        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus("PROJ-2"))

        client.close()
    }

    @Test
    fun `multiple ExternalClient instances work independently`() {
        val adapter1 = MockAdapter(taskResult = TaskSearchResult.Found("Status1"))
        val adapter2 = MockAdapter(taskResult = TaskSearchResult.Found("Status2"))

        val client1 = ExternalClient.wrap(adapter1)
        val client2 = ExternalClient.wrap(adapter2)

        val result1 = client1.getTaskStatus("TEST-1")
        val result2 = client2.getTaskStatus("TEST-2")

        assertEquals(TaskSearchResult.Found("Status1"), result1)
        assertEquals(TaskSearchResult.Found("Status2"), result2)

        client1.close()
        client2.close()
    }

    @Test
    fun `ExternalClient handles various authentication configurations`() {
        val configs = listOf(
            Authentication.None,
            Authentication.Bearer("test-token"),
            Authentication.Basic("user", "pass"),
            Authentication.PersonalAccessToken("email@example.com", "token")
        )

        configs.forEach { auth ->
            val integration = Plugin.Http(
                baseUrl = "https://api.example.com",
                auth = auth
            )
            val client = ExternalClient.from(integration)

            // Verify basic operations work
            client.getTaskStatus("")
            client.trackSession(SessionSnapshot(
                branch = "main",
                task = null,
                startTime = Instant.fromEpochMilliseconds(0),
                endTime = Instant.fromEpochMilliseconds(1000),
                durationMs = 1000
            ))
            client.close()
        }
    }
}
