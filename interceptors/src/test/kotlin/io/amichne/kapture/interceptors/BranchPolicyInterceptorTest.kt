package io.amichne.kapture.interceptors

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Enforcement
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BranchPolicyInterceptorTest {
    @Test
    fun `blocks branch creation when task missing and mode is BLOCK`() {
        val config = Config(
            enforcement = Enforcement(
                branchPolicy = Enforcement.Mode.BLOCK, statusCheck = Enforcement.Mode.OFF
            )
        )
        val interceptor = BranchPolicyInterceptor()
        val invocation = TestCommandInvocation(listOf("checkout", "-b", "feature/no-task"))
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTaskStatus(taskId: String): TaskSearchResult = TaskSearchResult.NotFound
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

        val exitCode = interceptor.before(invocation, config, client)
        assertEquals(BranchPolicyInterceptor.BRANCH_POLICY_EXIT_CODE, exitCode)
    }

    private class TestCommandInvocation(args: List<String>) : CommandInvocation(args, "git", File("."), emptyMap())
}
