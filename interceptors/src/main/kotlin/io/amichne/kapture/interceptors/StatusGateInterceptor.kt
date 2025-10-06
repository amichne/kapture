package io.amichne.kapture.interceptors

import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Enforcement
import io.amichne.kapture.core.model.config.StatusRules

class StatusGateInterceptor : GitInterceptor {
    /**
     * Blocks commits and pushes when the current branch's task status fails
     * the configured allow list, emitting WARN or ERROR messages based on the
     * enforcement mode.
     */
    override fun before(
        commandInvocation: CommandInvocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? {
        val mode = config.enforcement.statusCheck
        if (mode == Enforcement.Mode.OFF) return null

        val command = commandInvocation.command?.lowercase() ?: return null
        if (command !in commands) return null

        val branch = currentBranch(commandInvocation) ?: return null
        val task = BranchUtils.extractTask(branch, config.branchPattern)
        if (task.isNullOrBlank()) {
            return handleViolation(mode, command, "Branch '$branch' does not contain a task id")
        }

        val result = client.getTaskStatus(task)
        val isAllowed = when (result) {
            is TaskSearchResult.Found -> isStatusAllowed(command, result.status, config.statusRules)
            TaskSearchResult.NotFound -> false
            is TaskSearchResult.Error -> false
        }

        if (isAllowed) {
            return null
        }

        val message = when (result) {
            is TaskSearchResult.Found -> "Task ${task} status ${result.status} blocks $command"
            TaskSearchResult.NotFound -> "Task $task not found; cannot $command"
            is TaskSearchResult.Error -> "Task lookup failed (${result.message}); cannot verify status"
        }
        return handleViolation(mode, command, message)
    }

    private fun handleViolation(
        mode: Enforcement.Mode,
        command: String,
        message: String
    ): Int? {
        val exitCode = if (command == "commit") COMMIT_EXIT else PUSH_EXIT
        return when (mode) {
            Enforcement.Mode.WARN -> {
                System.err.println("[kapture] WARN: $message")
                null
            }

            Enforcement.Mode.BLOCK -> {
                System.err.println("[kapture] ERROR: $message")
                exitCode
            }

            Enforcement.Mode.OFF -> null
        }
    }

    private fun currentBranch(commandInvocation: CommandInvocation): String? {
        val result = commandInvocation.captureGit("rev-parse", "--abbrev-ref", "HEAD")
        if (result.exitCode != 0) return null
        val branch = result.stdout.trim()
        return branch.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    private fun isStatusAllowed(
        command: String,
        status: String,
        rules: StatusRules
    ): Boolean {
        return when (command) {
            "commit" -> status in rules.allowCommitWhen
            "push" -> status in rules.allowPushWhen
            else -> true
        }
    }

    companion object {
        private val commands = setOf("commit", "push")
        const val COMMIT_EXIT = 3
        const val PUSH_EXIT = 4
    }
}
