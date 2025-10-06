package io.amichne.kapture.core.git

import java.util.regex.Pattern

object BranchUtils {
    /**
     * Attempts to pull a ticket identifier from the provided branch name using
     * the supplied regex. Named group `ticket` is preferred, with the first
     * capture group used as a fallback when the named group is absent.
     */
    fun extractTicket(
        branch: String,
        pattern: String
    ): String? {
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(branch)
        if (!matcher.find()) return null
        return when {
            runCatching { matcher.group("ticket") }.getOrNull() != null -> matcher.group("ticket")
            matcher.groupCount() >= 1 -> matcher.group(1)
            else -> null
        }
    }
}
