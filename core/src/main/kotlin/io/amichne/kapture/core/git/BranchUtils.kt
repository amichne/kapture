package io.amichne.kapture.core.git

import java.util.regex.Pattern

object BranchUtils {
    /**
     * Attempts to pull a task identifier from the provided branch name using
     * the supplied regex. Named group `task` is preferred, with the first
     * capture group used as a fallback when the named group is absent.
     */
    fun extractTask(
        branch: String,
        pattern: String
    ): String? {
        val compiled = Pattern.compile(pattern)
        val matcher = compiled.matcher(branch)
        if (!matcher.find()) return null
        return when {
            runCatching { matcher.group("task") }.getOrNull() != null -> matcher.group("task")
            matcher.groupCount() >= 1 -> matcher.group(1)
            else -> null
        }
    }
}
