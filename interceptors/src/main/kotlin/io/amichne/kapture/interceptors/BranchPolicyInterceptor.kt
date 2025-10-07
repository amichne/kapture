package io.amichne.kapture.interceptors

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Enforcement
import io.amichne.kapture.core.model.task.TaskSearchResult

class BranchPolicyInterceptor : GitInterceptor {
    /**
     * Ensures newly created branches conform to the configured naming policy
     * and optionally validates that the associated task exists before Git
     * proceeds.
     */
    override fun before(
        commandInvocation: CommandInvocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? {
        val mode = config.enforcement.branchPolicy
        if (mode == Enforcement.Mode.OFF) return null

        val newBranch = extractCreatedBranch(commandInvocation) ?: return null
        val task = BranchUtils.extractTask(newBranch, config.branchPattern)
        if (task == null) {
            return handleViolation(
                mode,
                "Branch '$newBranch' does not match pattern ${config.branchPattern}"
            )
        }

        return when (val result = client.getTaskStatus(task)) {
            is TaskSearchResult.Found -> null
            TaskSearchResult.NotFound -> handleViolation(
                mode,
                "No task found for $task; verify before creating branch"
            )

            is TaskSearchResult.Error -> handleViolation(
                mode,
                "Task lookup failed for $task (${result.message}); proceeding cautiously"
            )
        }
    }

    private fun handleViolation(
        mode: Enforcement.Mode,
        message: String
    ): Int? {
        when (mode) {
            Enforcement.Mode.WARN -> {
                System.err.println("[kapture] WARN: $message")
                return null
            }

            Enforcement.Mode.BLOCK -> {
                System.err.println("[kapture] ERROR: $message")
                return BRANCH_POLICY_EXIT_CODE
            }

            Enforcement.Mode.OFF -> return null
        }
    }

    private fun extractCreatedBranch(commandInvocation: CommandInvocation): String? {
        val args = commandInvocation.args
        return when (commandInvocation.command?.lowercase()) {
            "checkout" -> parseSingleFlagVariant(args, setOf("-b", "-B"))
            "switch" -> parseSingleFlagVariant(args, setOf("-c", "--create"))
            "branch" -> parseBranchCopy(args)
            else -> null
        }
    }

    private fun parseSingleFlagVariant(
        args: List<String>,
        flags: Set<String>
    ): String? {
        for (i in args.indices) {
            val token = args[i]
            if (token !in flags && flags.none { token.startsWith(it) }) continue
            val explicit = flags.firstNotNullOfOrNull { flag ->
                when {
                    token == flag -> args.getOrNull(i + 1)
                    token.startsWith("$flag=") -> token.substring(flag.length + 1)
                    token.startsWith(flag) && flag.length == 2 -> token.substring(flag.length)
                    else -> null
                }
            }
            if (!explicit.isNullOrBlank()) return explicit
            if (token in flags) {
                return args.getOrNull(i + 1)
            }
        }
        return null
    }

    private fun parseBranchCopy(args: List<String>): String? {
        val flagIndex = args.indexOfFirst { it == "-c" || it == "-C" }
        if (flagIndex == -1) return null
        return args.getOrNull(flagIndex + 2)
    }

    companion object {
        const val BRANCH_POLICY_EXIT_CODE = 2
    }
}
