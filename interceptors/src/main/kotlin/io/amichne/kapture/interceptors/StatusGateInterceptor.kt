package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.Invocation

class StatusGateInterceptor : GitInterceptor {
    /**
     * Blocks commits and pushes when the current branch's ticket status fails
     * the configured allow list, emitting WARN or ERROR messages based on the
     * enforcement mode.
     */
    override fun before(
        invocation: Invocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? {
        val mode = config.enforcement.statusCheck
        if (mode == Config.Enforcement.Mode.OFF) return null

        val command = invocation.command?.lowercase() ?: return null
        if (command !in commands) return null

        val branch = currentBranch(invocation) ?: return null
        val ticket = BranchUtils.extractTicket(branch, config.branchPattern)
        if (ticket.isNullOrBlank()) {
            return handleViolation(mode, command, "Branch '$branch' does not contain ticket id")
        }

        val result = client.getTicketStatus(ticket)
        val isAllowed = when (result) {
            is TicketLookupResult.Found -> isStatusAllowed(command, result.status, config.statusRules)
            TicketLookupResult.NotFound -> false
            is TicketLookupResult.Error -> false
        }

        if (isAllowed) {
            return null
        }

        val message = when (result) {
            is TicketLookupResult.Found -> "Ticket ${ticket} status ${result.status} blocks $command"
            TicketLookupResult.NotFound -> "Ticket $ticket not found; cannot $command"
            is TicketLookupResult.Error -> "Ticket lookup failed (${result.message}); cannot verify status"
        }
        return handleViolation(mode, command, message)
    }

    private fun handleViolation(
        mode: Config.Enforcement.Mode,
        command: String,
        message: String
    ): Int? {
        val exitCode = if (command == "commit") COMMIT_EXIT else PUSH_EXIT
        return when (mode) {
            Config.Enforcement.Mode.WARN -> {
                System.err.println("[kapture] WARN: $message")
                null
            }

            Config.Enforcement.Mode.BLOCK -> {
                System.err.println("[kapture] ERROR: $message")
                exitCode
            }

            Config.Enforcement.Mode.OFF -> null
        }
    }

    private fun currentBranch(invocation: Invocation): String? {
        val result = invocation.captureGit("rev-parse", "--abbrev-ref", "HEAD")
        if (result.exitCode != 0) return null
        val branch = result.stdout.trim()
        return branch.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    private fun isStatusAllowed(
        command: String,
        status: String,
        rules: Config.StatusRules
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
