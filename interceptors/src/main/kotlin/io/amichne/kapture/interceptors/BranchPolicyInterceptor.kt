package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.Invocation

class BranchPolicyInterceptor : GitInterceptor {
    /**
     * Ensures newly created branches conform to the configured naming policy
     * and optionally validates that the associated ticket exists before Git
     * proceeds.
     */
    override fun before(
        invocation: Invocation,
        config: Config,
        client: ExternalClient<*>
    ): Int? {
        val mode = config.enforcement.branchPolicy
        if (mode == Config.Enforcement.Mode.OFF) return null

        val newBranch = extractCreatedBranch(invocation) ?: return null
        val ticket = BranchUtils.extractTicket(newBranch, config.branchPattern)
        if (ticket == null) {
            return handleViolation(
                mode,
                "Branch '$newBranch' does not match pattern ${config.branchPattern}"
            )
        }

        return when (val result = client.getTicketStatus(ticket)) {
            is TicketLookupResult.Found -> null
            TicketLookupResult.NotFound -> handleViolation(
                mode,
                "No ticket found for $ticket; verify before creating branch"
            )

            is TicketLookupResult.Error -> handleViolation(
                mode,
                "Ticket lookup failed for $ticket (${result.message}); proceeding cautiously"
            )
        }
    }

    private fun handleViolation(
        mode: Config.Enforcement.Mode,
        message: String
    ): Int? {
        when (mode) {
            Config.Enforcement.Mode.WARN -> {
                System.err.println("[kapture] WARN: $message")
                return null
            }

            Config.Enforcement.Mode.BLOCK -> {
                System.err.println("[kapture] ERROR: $message")
                return BRANCH_POLICY_EXIT_CODE
            }

            Config.Enforcement.Mode.OFF -> return null
        }
    }

    private fun extractCreatedBranch(invocation: Invocation): String? {
        val args = invocation.args
        return when (invocation.command?.lowercase()) {
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
