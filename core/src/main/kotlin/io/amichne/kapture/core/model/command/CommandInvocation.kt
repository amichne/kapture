package io.amichne.kapture.core.model.command

import io.amichne.kapture.core.command.CommandExecutor
import java.io.File

open class CommandInvocation(
    val args: List<String>,
    private val realGitBinary: String,
    private val workDir: File,
    private val env: Map<String, String>
) {
    val command: String? = args.firstOrNull()?.lowercase()

    /**
     * Checks whether the first Git argument matches any of the supplied names,
     * using case-insensitive comparison for ergonomic command detection.
     */
    fun isCommand(vararg names: String): Boolean {
        val cmd = command ?: return false
        return names.any { it.equals(cmd, ignoreCase = true) }
    }

    /**
     * Returns true when any of the raw arguments exactly matches one of the
     * requested flag tokens, guarding the interceptor from empty argument
     * lists.
     */
    fun hasFlag(vararg flags: String): Boolean {
        if (args.isEmpty()) return false
        val flagSet = flags.toSet()
        return args.any { it in flagSet }
    }

    /**
     * Runs the real Git binary with the given arguments and returns its
     * captured output while preserving the original working directory and
     * environment.
     */
    open fun captureGit(vararg gitArgs: String): CommandResult = CommandExecutor.capture(
        cmd = listOf(realGitBinary) + gitArgs,
        workDir = workDir,
        env = env
    )

    /**
     * Launches the real Git binary with inherited IO so the user sees native
     * output, returning the resulting exit code.
     */
    open fun passthroughGit(vararg gitArgs: String): Int = CommandExecutor.passthrough(
        cmd = listOf(realGitBinary) + gitArgs,
        workDir = workDir,
        env = env
    )

    /** Exposes the resolved path to the underlying Git executable. */
    fun realGit(): String = realGitBinary
}
