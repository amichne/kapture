package io.amichne.kapture.core.http

import io.amichne.kapture.core.CompositeAdapter
import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskStatus
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ExternalClientTest {

    @Test
    fun `wrap creates client with custom adapter`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)

        val result = client.getTaskStatus("TEST-123")

        result.assertFoundRaw("In Progress", InternalStatus.IN_PROGRESS)
        assertEquals(1, adapter.getTaskStatusCallCount)
    }

    @Test
    fun `getTaskStatus delegates to adapter`() {
        val adapter = TestAdapter(taskResult = TaskSearchResult.NotFound)
        val client = ExternalClient.wrap(adapter)

        val result = client.getTaskStatus("MISSING-999")

        assertEquals(TaskSearchResult.NotFound, result)
        assertEquals(1, adapter.getTaskStatusCallCount)
    }

    @Test
    fun `getTaskStatus handles error result from adapter`() {
        val adapter = TestAdapter(taskResult = TaskSearchResult.Error("timeout"))
        val client = ExternalClient.wrap(adapter)

        val result = client.getTaskStatus("TEST-456")

        assertEquals(TaskSearchResult.Error("timeout"), result)
    }

    @Test
    fun `trackSession delegates to adapter`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)
        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            task = "TEST-123",
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
        val integration = Plugin.Rest(
            baseUrl = "https://api.example.com",
            auth = Authentication.None
        )

        val client = ExternalClient.from(integration)

        // Verify it's wrapped properly by closing it
        client.close()
    }

    @Test
    fun `from creates JiraCliAdapter for CLI integration`() {
        val integration = Plugin.Cli(
            paths = listOf(existingJavaExecutable()),
            environment = mapOf("JIRA_API_TOKEN" to "test-token"),
            timeoutMs = 30_000
        )

        val client = ExternalClient.from(integration)

        // Verify it's wrapped properly by closing it
        client.close()
    }

    @Test
    fun `multiple operations work correctly`() {
        val adapter = TestAdapter()
        val client = ExternalClient.wrap(adapter)

        client.getTaskStatus("TASK-1")
        client.getTaskStatus("TASK-2")
        val snapshot = SessionSnapshot(
            branch = "main",
            task = null,
            startTime = Instant.fromEpochMilliseconds(0),
            endTime = Instant.fromEpochMilliseconds(1000),
            durationMs = 1000
        )
        client.trackSession(snapshot)
        client.close()

        assertEquals(2, adapter.getTaskStatusCallCount)
        assertEquals(1, adapter.trackSessionCallCount)
        assertEquals(1, adapter.closeCallCount)
    }

    @Test
    fun `composite adapter returns first found status`() {
        val notFoundAdapter = DelegatingAdapter(statusHandler = { TaskSearchResult.NotFound })
        val foundAdapter = DelegatingAdapter(
            statusHandler = {
                TaskSearchResult.Found(
                    TaskStatus(provider = "second", key = it, raw = "In Progress", internal = InternalStatus.IN_PROGRESS)
                )
            }
        )

        val composite = CompositeAdapter(listOf(notFoundAdapter, foundAdapter))
        val client = ExternalClient.wrap(composite)

        val result = client.getTaskStatus("TASK-999")

        result.assertFoundRaw("In Progress", InternalStatus.IN_PROGRESS)
    }

    @Test
    fun `composite adapter returns error when only errors produced`() {
        val errorAdapter = DelegatingAdapter(statusHandler = { TaskSearchResult.Error("boom") })
        val composite = CompositeAdapter(listOf(errorAdapter))
        val client = ExternalClient.wrap(composite)

        val result = client.getTaskStatus("TASK-ERR")

        assertEquals(TaskSearchResult.Error("boom"), result)
    }

    @Test
    fun `composite adapter attempts createSubtask until success`() {
        val failure = DelegatingAdapter(createHandler = { _, _ -> SubtaskCreationResult.Failure("nope") })
        val success = DelegatingAdapter(createHandler = { _, _ -> SubtaskCreationResult.Success("TASK-123") })

        val composite = CompositeAdapter(listOf(failure, success))

        val result = composite.createSubtask("PARENT-1", "title")

        assertEquals(SubtaskCreationResult.Success("TASK-123"), result)
    }

    @Test
    fun `composite adapter broadcasts session tracking`() {
        val calls = mutableListOf<SessionSnapshot>()
        val first = DelegatingAdapter(trackHandler = { calls.add(it) })
        val second = DelegatingAdapter(trackHandler = { calls.add(it) })
        val composite = CompositeAdapter(listOf(first, second))
        val snapshot = SessionSnapshot(
            branch = "main",
            task = null,
            startTime = Instant.fromEpochMilliseconds(0),
            endTime = Instant.fromEpochMilliseconds(1000),
            durationMs = 1000
        )

        composite.trackSession(snapshot)

        assertEquals(2, calls.size)
        assertTrue(calls.all { it == snapshot })
    }

    private class TestAdapter(
        private val taskResult: TaskSearchResult = TaskSearchResult.Found(
            TaskStatus(
                provider = "test",
                key = "TEST-DEFAULT",
                raw = "In Progress",
                internal = InternalStatus.IN_PROGRESS
            )
        )
    ) : Adapter {
        var getTaskStatusCallCount = 0
        var trackSessionCallCount = 0
        var closeCallCount = 0
        var lastSessionSnapshot: SessionSnapshot? = null

        override fun getTaskStatus(taskId: String): TaskSearchResult {
            getTaskStatusCallCount++
            return taskResult
        }

        override fun trackSession(snapshot: SessionSnapshot) {
            trackSessionCallCount++
            lastSessionSnapshot = snapshot
        }

        override fun createSubtask(
            parentId: String,
            title: String?
        ) =
            SubtaskCreationResult.Failure("Not implemented")

        override fun transitionTask(
            taskId: String,
            targetStatus: String
        ) =
            TaskTransitionResult.Failure("Not implemented")

        override fun getTaskDetails(taskId: String) =
            TaskDetailsResult.Failure("Not implemented")

        override fun close() {
            closeCallCount++
        }
    }

    private class DelegatingAdapter(
        private val statusHandler: (String) -> TaskSearchResult = { TaskSearchResult.NotFound },
        private val createHandler: (String, String?) -> SubtaskCreationResult = { _, _ -> SubtaskCreationResult.Failure("unsupported") },
        private val transitionHandler: (String, String) -> TaskTransitionResult = { _, _ -> TaskTransitionResult.Failure("unsupported") },
        private val detailsHandler: (String) -> TaskDetailsResult = { TaskDetailsResult.Failure("unsupported") },
        private val trackHandler: (SessionSnapshot) -> Unit = {}
    ) : Adapter {
        override fun getTaskStatus(taskId: String): TaskSearchResult = statusHandler(taskId)

        override fun trackSession(snapshot: SessionSnapshot) {
            trackHandler(snapshot)
        }

        override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult =
            createHandler(parentId, title)

        override fun transitionTask(taskId: String, targetStatus: String): TaskTransitionResult =
            transitionHandler(taskId, targetStatus)

        override fun getTaskDetails(taskId: String): TaskDetailsResult =
            detailsHandler(taskId)

        override fun close() {
            // No-op
        }
    }
}

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
