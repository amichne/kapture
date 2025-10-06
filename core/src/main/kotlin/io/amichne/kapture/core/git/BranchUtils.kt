package io.amichne.kapture.core.git

import java.util.regex.Pattern

object BranchUtils {
    fun extractTicket(branch: String, pattern: String): String? {
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
