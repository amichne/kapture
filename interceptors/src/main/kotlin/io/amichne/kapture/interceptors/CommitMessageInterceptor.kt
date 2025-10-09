package io.amichne.kapture.interceptors

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Config

class CommitMessageInterceptor : GitInterceptor {
    /**
     * Automatically adds the ticket key to commit messages if it's missing.
     * For example, transforms `git commit -m "Initial commit"` into
     * `git commit -m "KAP-123: Initial commit"`.
     */
    override fun before(
        commandInvocation: CommandInvocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? {
        val command = commandInvocation.command?.lowercase() ?: return null
        if (command != "commit") return null

        // Get current branch and extract ticket key
        val branch = getCurrentBranch(commandInvocation) ?: return null
        val ticketKey = BranchUtils.extractTask(branch, config.branchPattern)
        if (ticketKey.isNullOrBlank()) return null

        // Find commit message in arguments
        val args = commandInvocation.args
        val messageIndex = findMessageArgumentIndex(args) ?: return null

        // Check if message already contains ticket key
        val message = extractMessage(args, messageIndex) ?: return null
        if (message.contains(ticketKey, ignoreCase = true)) {
            return null // Already contains ticket key, let the command proceed
        }

        // Modify the message to include ticket key
        val modifiedMessage = "$ticketKey: $message"
        val modifiedArgs = modifyCommitMessage(args, messageIndex, modifiedMessage)

        // Execute the modified command using the CommandInvocation's passthrough
        val exitCode = commandInvocation.passthroughGit(*modifiedArgs.toTypedArray())

        // Return the exit code to block the original command
        return exitCode
    }

    private fun getCurrentBranch(commandInvocation: CommandInvocation): String? {
        val result = commandInvocation.captureGit("rev-parse", "--abbrev-ref", "HEAD")
        if (result.exitCode != 0) return null
        val branch = result.stdout.trim()
        return branch.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    private fun findMessageArgumentIndex(args: List<String>): Int? {
        for (i in args.indices) {
            val token = args[i]
            if (token == "-m" || token == "--message") {
                return i
            }
            if (token.startsWith("-m=") || token.startsWith("--message=")) {
                return i
            }
        }
        return null
    }

    private fun extractMessage(args: List<String>, messageIndex: Int): String? {
        val token = args[messageIndex]
        return when {
            token == "-m" || token == "--message" -> {
                args.getOrNull(messageIndex + 1)
            }
            token.startsWith("-m=") -> {
                token.substring(3)
            }
            token.startsWith("--message=") -> {
                token.substring(10)
            }
            else -> null
        }
    }

    private fun modifyCommitMessage(
        args: List<String>,
        messageIndex: Int,
        newMessage: String
    ): List<String> {
        val token = args[messageIndex]
        return when {
            token == "-m" || token == "--message" -> {
                args.toMutableList().apply {
                    set(messageIndex + 1, newMessage)
                }
            }
            token.startsWith("-m=") -> {
                args.toMutableList().apply {
                    set(messageIndex, "-m=$newMessage")
                }
            }
            token.startsWith("--message=") -> {
                args.toMutableList().apply {
                    set(messageIndex, "--message=$newMessage")
                }
            }
            else -> args
        }
    }
}
