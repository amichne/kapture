package io.amichne.kapture.core.http

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskStatus
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Integration tests that verify ExternalClient works correctly with
 * actual adapter implementations (not mocks).
 */
class ExternalClientIntegrationTest {

    @Test
    fun `ExternalClient from Rest integration creates working client`() {
        val integration = Plugin.Rest(
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
            paths = listOf(existingJavaExecutable()),
            environment = mapOf("JIRA_API_TOKEN" to "test"),
            timeoutMs = 30_000
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
        val integration = Plugin.Rest(
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
        val integration = Plugin.Cli(
            paths = listOf(existingJavaExecutable()),
            environment = emptyMap(),
            timeoutMs = 30_000
        )

        val client = ExternalClient.from(integration)
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
                    taskId.startsWith("VALID") -> foundStatus(taskId, "In Progress", InternalStatus.IN_PROGRESS)
                    taskId.startsWith("DONE") -> foundStatus(taskId, "Done", InternalStatus.DONE)
                    else -> TaskSearchResult.Error("Unknown task")
                }
            }
        )

        val client = ExternalClient.wrap(adapter)

        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus(""))
        client.getTaskStatus("VALID-123").assertFoundRaw("In Progress", InternalStatus.IN_PROGRESS)
        client.getTaskStatus("DONE-456").assertFoundRaw("Done", InternalStatus.DONE)
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

        client.getTaskStatus("PROJ-1").assertFoundRaw("To Do")
        client.getTaskStatus("PROJ-2").assertFoundRaw("In Progress")
        client.getTaskStatus("PROJ-3").assertFoundRaw("Done")
        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus("PROJ-4"))

        // Update state
        adapter.setTaskStatus("PROJ-1", "Done")
        adapter.removeTask("PROJ-2")

        client.getTaskStatus("PROJ-1").assertFoundRaw("Done")
        assertEquals(TaskSearchResult.NotFound, client.getTaskStatus("PROJ-2"))

        client.close()
    }

    @Test
    fun `multiple ExternalClient instances work independently`() {
        val adapter1 = MockAdapter(taskResult = foundStatus("TEST-1", "Status1"))
        val adapter2 = MockAdapter(taskResult = foundStatus("TEST-2", "Status2"))

        val client1 = ExternalClient.wrap(adapter1)
        val client2 = ExternalClient.wrap(adapter2)

        val result1 = client1.getTaskStatus("TEST-1")
        val result2 = client2.getTaskStatus("TEST-2")

        result1.assertFoundRaw("Status1")
        result2.assertFoundRaw("Status2")

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
            val integration = Plugin.Rest(
                baseUrl = "https://api.example.com",
                auth = auth
            )
            val client = ExternalClient.from(integration)

            // Verify basic operations work
            client.getTaskStatus("")
            client.trackSession(
                SessionSnapshot(
                    branch = "main",
                    task = null,
                    startTime = Instant.fromEpochMilliseconds(0),
                    endTime = Instant.fromEpochMilliseconds(1000),
                    durationMs = 1000
                )
            )
            client.close()
        }
    }
}

private fun foundStatus(
    key: String,
    raw: String,
    internal: InternalStatus? = null,
    provider: String = "test"
): TaskSearchResult = TaskSearchResult.Found(
    TaskStatus(
        provider = provider,
        key = key,
        raw = raw,
        internal = internal
    )
)

private fun TaskSearchResult.assertFoundRaw(expectedRaw: String, expectedInternal: InternalStatus? = null) {
    val found = this as? TaskSearchResult.Found
        ?: throw AssertionError("Expected TaskSearchResult.Found but was $this")
    assertEquals(expectedRaw, found.status.raw)
    expectedInternal?.let { assertEquals(it, found.status.internal) }
}

private fun existingJavaExecutable(): String {
    val javaHome = Paths.get(System.getProperty("java.home"))
    val execName = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
    val candidate = javaHome.resolve("bin").resolve(execName)
    require(Files.exists(candidate)) { "Could not locate Java executable at $candidate" }
    return candidate.toAbsolutePath().toString()
}
