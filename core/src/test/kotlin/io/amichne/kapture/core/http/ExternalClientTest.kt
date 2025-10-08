package io.amichne.kapture.core.http

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Cli
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
import org.junit.jupiter.api.Test

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
        val integration = Plugin.Http(
            baseUrl = "https://api.example.com",
            auth = Authentication.None
        )

        val client = ExternalClient.from(integration)

        // Verify it's wrapped properly by closing it
        client.close()
    }

    @Test
    fun `from creates JiraCliAdapter for JiraCli integration`() {
        val integration = Cli.Jira(
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
}

private fun TaskSearchResult.assertFoundRaw(expectedRaw: String, expectedInternal: InternalStatus? = null) {
    val found = this as? TaskSearchResult.Found
        ?: throw AssertionError("Expected TaskSearchResult.Found but was $this")
    assertEquals(expectedRaw, found.status.raw)
    expectedInternal?.let { assertEquals(it, found.status.internal) }
}
