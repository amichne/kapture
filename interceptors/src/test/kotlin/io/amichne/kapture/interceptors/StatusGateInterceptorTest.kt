package io.amichne.kapture.interceptors

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Enforcement
import io.amichne.kapture.core.model.config.Enforcement.Mode
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.interceptors.support.GitTestRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatusGateInterceptorTest {
    private lateinit var repo: GitTestRepository

    @BeforeEach
    fun setUp() {
        repo = GitTestRepository.create()
        repo.createFile("README.md", "docs")
        repo.commit("Initial commit")
        repo.checkoutBranch("PROJ-7/feature", create = true)
    }

    @AfterEach
    fun tearDown() {
        repo.close()
    }

    @Test
    fun `blocks commit when task status not allowed`() {
        val config = Config(
            enforcement = Enforcement(
                branchPolicy = Mode.OFF,
                statusCheck = Mode.BLOCK
            )
        )
        val interceptor = StatusGateInterceptor()
        val commandInvocation = CommandInvocation(
            listOf("commit"),
            repo.gitPath(),
            repo.root.toFile(),
            repo.environment()
        )
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTaskStatus(taskId: String): TaskSearchResult = TaskSearchResult.Found("BLOCKED")
            override fun trackSession(snapshot: SessionSnapshot) {}
            override fun createSubtask(
                parentId: String,
                title: String?
            ) = SubtaskCreationResult.Failure("Not implemented")

            override fun transitionTask(
                taskId: String,
                targetStatus: String
            ) = TaskTransitionResult.Failure("Not implemented")

            override fun getTaskDetails(taskId: String) = TaskDetailsResult.Failure("Not implemented")
            override fun close() {}
        })

        val exitCode = interceptor.before(commandInvocation, config, client)
        assertEquals(StatusGateInterceptor.COMMIT_EXIT, exitCode)
    }
}
