package io.amichne.kapture.core.model

import io.amichne.kapture.core.exec.Exec
import io.amichne.kapture.core.exec.ExecResult
import java.io.File

open class Invocation(
    val args: List<String>,
    private val realGitBinary: String,
    private val workDir: File,
    private val env: Map<String, String>
) {
    val command: String? = args.firstOrNull()?.lowercase()

    fun isCommand(vararg names: String): Boolean {
        val cmd = command ?: return false
        return names.any { it.equals(cmd, ignoreCase = true) }
    }

    fun hasFlag(vararg flags: String): Boolean {
        if (args.isEmpty()) return false
        val flagSet = flags.toSet()
        return args.any { it in flagSet }
    }

    open fun captureGit(vararg gitArgs: String): ExecResult = Exec.capture(
        cmd = listOf(realGitBinary) + gitArgs,
        workDir = workDir,
        env = env
    )

    open fun passthroughGit(vararg gitArgs: String): Int = Exec.passthrough(
        cmd = listOf(realGitBinary) + gitArgs,
        workDir = workDir,
        env = env
    )

    fun realGit(): String = realGitBinary
}
