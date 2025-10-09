package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.adapter.internal.jira.JiraCliAdapter
import io.amichne.kapture.core.adapter.internal.jira.JiraCliExecutable
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.model.command.CommandResult
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraCliAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val resolver: (Plugin.Cli) -> JiraCliExecutable = { plugin ->
        JiraCliExecutable(plugin.paths.first())
    }

    private fun cliPlugin(
        paths: List<String> = listOf("jira-cli"),
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Int? = 60_000
    ): Plugin.Cli = Plugin.Cli(
        paths = paths,
        environment = environment,
        timeoutMs = timeoutMs
    )

    private fun createAdapter(
        plugin: Plugin.Cli = cliPlugin(),
        resolverOverride: (Plugin.Cli) -> JiraCliExecutable = resolver
    ): JiraCliAdapter =
        JiraCliAdapter(
            plugin = plugin,
            json = json,
            executableResolver = resolverOverride
        )

    @BeforeEach
    fun setUp() {
        mockkObject(CommandExecutor)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CommandExecutor)
    }

    @Test
    fun `getTaskStatus returns Found when CLI returns valid JSON`() {
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
            CommandExecutor.capture(
                cmd = listOf("jira-cli", "task", "view", "TEST-123", "--output", "json"),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        result.assertFoundRaw("In Progress")
    }

    @Test
    fun `getTaskStatus returns NotFound for blank task ID`() {
        val adapter = createAdapter()

        val result = adapter.getTaskStatus("")

        assertEquals(TaskSearchResult.NotFound, result)
        verify(exactly = 0) { CommandExecutor.capture(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getTaskStatus returns NotFound for whitespace task ID`() {
        val adapter = createAdapter()

        val result = adapter.getTaskStatus("   ")

        assertEquals(TaskSearchResult.NotFound, result)
        verify(exactly = 0) { CommandExecutor.capture(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getTaskStatus returns Error when CLI exits with non-zero code`() {
        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 1, stdout = "", stderr = "Error: task not found")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("MISSING-999")

        assertTrue(result is TaskSearchResult.Error)
        assertTrue((result as TaskSearchResult.Error).message.contains("exit 1"))
    }

    @Test
    fun `getTaskStatus returns NotFound when fields are missing`() {
        val invalidResponse = """
            {
                "key": "TEST-123"
            }
        """.trimIndent()

        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        assertEquals(TaskSearchResult.NotFound, result)
    }

    @Test
    fun `getTaskStatus returns NotFound when status is missing`() {
        val invalidResponse = """
            {
                "fields": {
                    "summary": "Test task"
                }
            }
        """.trimIndent()

        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        assertEquals(TaskSearchResult.NotFound, result)
    }

    @Test
    fun `getTaskStatus returns NotFound when name is missing`() {
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
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        assertEquals(TaskSearchResult.NotFound, result)
    }

    @Test
    fun `getTaskStatus returns NotFound when status name is empty`() {
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
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = invalidResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        assertEquals(TaskSearchResult.NotFound, result)
    }

    @Test
    fun `getTaskStatus returns Error when JSON is malformed`() {
        val malformedResponse = "{invalid json"

        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = malformedResponse, stderr = "")

        val adapter = createAdapter()

        val result = adapter.getTaskStatus("TEST-123")

        assertTrue(result is TaskSearchResult.Error)
    }

    @Test
    fun `getTaskStatus uses custom executable when configured`() {
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
            CommandExecutor.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val plugin = cliPlugin(paths = listOf("/usr/local/bin/jira"))
        val adapter = createAdapter(plugin)

        adapter.getTaskStatus("PROJ-456")

        assertEquals("/usr/local/bin/jira", commandSlot.captured[0])
    }

    @Test
    fun `getTaskStatus uses default executable when not configured`() {
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
            CommandExecutor.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val plugin = cliPlugin(paths = listOf("", "jira-cli"))
        val adapter = createAdapter(plugin) { JiraCliExecutable("jira-cli") }

        adapter.getTaskStatus("PROJ-456")

        assertEquals("jira-cli", commandSlot.captured[0])
    }

    @Test
    fun `getTaskStatus passes environment variables to CLI`() {
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
            CommandExecutor.capture(
                cmd = any(),
                env = capture(envSlot),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val environment = mapOf(
            "JIRA_API_TOKEN" to "test-token",
            "JIRA_SITE" to "https://example.atlassian.net"
        )
        val adapter = createAdapter(cliPlugin(environment = environment))

        adapter.getTaskStatus("TEST-123")

        assertEquals(environment, envSlot.captured)
    }

    @Test
    fun `getTaskStatus uses custom timeout when configured`() {
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
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = capture(timeoutSlot)
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val adapter = createAdapter(cliPlugin(timeoutMs = 30_000))

        adapter.getTaskStatus("TEST-123")

        assertEquals(30L, timeoutSlot.captured)
    }

    @Test
    fun `getTaskStatus constructs correct command`() {
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
            CommandExecutor.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val adapter = createAdapter()

        adapter.getTaskStatus("MYPROJ-789")

        val command = commandSlot.captured
        assertEquals(listOf("jira-cli", "task", "view", "MYPROJ-789", "--output", "json"), command)
    }

    @Test
    fun `trackSession does nothing and logs debug message`() {
        val adapter = createAdapter()

        val snapshot = SessionSnapshot(
            branch = "TEST-123/feature",
            task = "TEST-123",
            startTime = Instant.fromEpochMilliseconds(1000),
            endTime = Instant.fromEpochMilliseconds(2000),
            durationMs = 1000
        )

        // Should not throw and should not invoke CommandExecutor
        adapter.trackSession(snapshot)

        verify(exactly = 0) { CommandExecutor.capture(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { CommandExecutor.passthrough(any(), any(), any(), any()) }
    }

    @Test
    fun `close does not throw`() {
        val adapter = createAdapter()

        adapter.close()

        // Should complete without errors
    }

    @Test
    fun `multiple getTaskStatus calls work correctly`() {
        val responses = listOf(
            """{"fields": {"status": {"name": "To Do"}}}""",
            """{"fields": {"status": {"name": "In Progress"}}}""",
            """{"fields": {"status": {"name": "Done"}}}"""
        )

        var callCount = 0
        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } answers {
            CommandResult(exitCode = 0, stdout = responses[callCount++], stderr = "")
        }

        val adapter = createAdapter()

        val result1 = adapter.getTaskStatus("TEST-1")
        val result2 = adapter.getTaskStatus("TEST-2")
        val result3 = adapter.getTaskStatus("TEST-3")

        result1.assertFoundRaw("To Do")
        result2.assertFoundRaw("In Progress")
        result3.assertFoundRaw("Done")
        assertEquals(3, callCount)
    }

    @Test
    fun `getTaskStatus handles various Jira task ID formats`() {
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
            CommandExecutor.capture(
                cmd = capture(commandSlot),
                env = any(),
                timeoutSeconds = any()
            )
        } returns CommandResult(exitCode = 0, stdout = validResponse, stderr = "")

        val adapter = createAdapter()

        // Test various task formats
        adapter.getTaskStatus("PROJ-1")
        adapter.getTaskStatus("ABC-12345")
        adapter.getTaskStatus("X-1")

        assertEquals("PROJ-1", commandSlot[0][3])
        assertEquals("ABC-12345", commandSlot[1][3])
        assertEquals("X-1", commandSlot[2][3])
    }

    @Test
    fun `getTaskStatus handles status names with spaces and special characters`() {
        val statuses = listOf(
            "In Progress",
            "Ready for Review",
            "Done / Closed",
            "Waiting-For-Feedback"
        )

        var callCount = 0
        every {
            CommandExecutor.capture(
                cmd = any(),
                env = any(),
                timeoutSeconds = any()
            )
        } answers {
            val status = statuses[callCount++]
            val response = """{"fields": {"status": {"name": "$status"}}}"""
            CommandResult(exitCode = 0, stdout = response, stderr = "")
        }

        val adapter = createAdapter()

        statuses.forEach { expectedStatus ->
            val result = adapter.getTaskStatus("TEST-123")
            result.assertFoundRaw(expectedStatus)
        }
    }

    private fun TaskSearchResult.assertFoundRaw(expectedRaw: String) {
        assertTrue(this is TaskSearchResult.Found) { "Expected Found result but was $this" }
        assertEquals(expectedRaw, (this as TaskSearchResult.Found).status.raw)
    }
}
