package io.amichne.kapture.core.command

import io.amichne.kapture.core.model.command.CommandResult
import io.amichne.kapture.core.util.Environment
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [ProcessBuilder] that keeps Git UX intact for passthrough commands
 * and provides a capture helper for internal probes (e.g., rev-parse).
 */
object CommandExecutor {
    private fun defaultTimeoutSeconds(): Long = 60L

    /**
     * Launches the provided command, inheriting the current stdin/stdout/stderr
     * so the child process behaves like a native Git invocation. Returns the
     * observed exit code or `-1` if the process exceeded the timeout.
     */
    fun passthrough(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = defaultTimeoutSeconds()
    ): Int = runCatching {
        require(cmd.isNotEmpty()) { "Command must not be empty" }
        ProcessBuilder(cmd)
            .configure(workDir, env, inheritIo = true)
            .start()
            .waitForOrTimeout(timeoutSeconds)
    }.getOrElse { ex ->
        handleFailure(cmd, ex) { message ->
            System.err.println("[kapture] ERROR: $message")
            -1
        }
    }

    /**
     * Executes the command while capturing stdout and stderr into memory,
     * returning the trimmed output alongside the exit code. The process is
     * forcibly terminated when it exceeds the supplied timeout.
     */
    fun capture(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        charset: Charset = Charsets.UTF_8,
        timeoutSeconds: Long = defaultTimeoutSeconds()
    ): CommandResult = runCatching {
        require(cmd.isNotEmpty()) { "Command must not be empty" }
        ProcessBuilder(cmd)
            .configure(workDir, env)
            .start()
            .toCommandResult(timeoutSeconds, charset)
    }.getOrElse { ex -> handleFailure(cmd, ex) { message -> CommandResult(-1, "", message) } }

    private fun <T> handleFailure(
        cmd: List<String>,
        throwable: Throwable,
        onFailure: (String) -> T
    ): T = buildFailureMessage(cmd, throwable).let { message ->
        if (throwable is InterruptedException) {
            Thread.currentThread().interrupt()
        }

        Environment.debug {
            formatCommand(cmd).let { commandDisplay ->
                throwable.message?.takeIf { it.isNotBlank() }
                    ?.let { detail -> ": $detail" }
                    .orEmpty()
                    .let { detail ->
                        "Command failure for '$commandDisplay': ${throwable::class.java.simpleName}$detail"
                    }
            }
        }

        if (Environment.debugEnabled) {
            throwable.printStackTrace(System.err)
        }

        onFailure(message)
    }

    private fun buildFailureMessage(
        cmd: List<String>,
        throwable: Throwable
    ): String = when (throwable) {
        is InterruptedException -> "Command '${formatCommand(cmd)}' was interrupted"
        is IOException -> buildIoFailureMessage(cmd, throwable)
        else -> throwable.message?.takeIf { it.isNotBlank() }
                    ?.let { detail -> "Command '${formatCommand(cmd)}' failed: ${detail.trim()}" }
                ?: "Command '${formatCommand(cmd)}' failed with ${throwable::class.java.simpleName}"
    }

    private fun buildIoFailureMessage(
        cmd: List<String>,
        throwable: IOException
    ): String = formatCommand(cmd).let { commandDisplay ->
        cmd.firstOrNull()?.takeIf { it.isNotBlank() }.let { executable ->
            listOfNotNull(
                "Unable to run '$commandDisplay'",
                throwable.message?.takeIf { it.isNotBlank() }
                    ?.let { detail -> sanitizeFailureDetail(detail, executable).trim().trimEnd('.') }
                    ?.takeIf { it.isNotEmpty() },
                executable?.let { "Ensure '$it' is installed and available on PATH" }
            ).joinToString(". ")
        }
    }

    private fun sanitizeFailureDetail(
        detail: String,
        executable: String?
    ): String = executable
                    ?.takeIf { it.isNotBlank() }
                    ?.let { exec ->
                        Regex("Cannot run program \"${Regex.escape(exec)}\"(?: \\(.+?\\))?:\\s*")
                            .replace(detail, "")
                            .trim()
                            .takeIf { it.isNotEmpty() }
                        ?: detail.trim()
                    }
                ?: detail.trim()

    private fun formatCommand(cmd: List<String>): String = cmd.joinToString(" ").ifBlank { "<no command>" }

    private fun applyEnvironment(
        processBuilder: ProcessBuilder,
        env: Map<String, String>
    ) {
        env.takeIf { it.isNotEmpty() }?.let { overrides ->
            processBuilder.environment().apply {
                overrides.forEach { (key, value) -> this[key] = value }
            }
        }
    }

    private fun ProcessBuilder.configure(
        workDir: File?,
        env: Map<String, String>,
        inheritIo: Boolean = false
    ): ProcessBuilder = apply {
        workDir?.let { directory(it) }
        if (inheritIo) inheritIO()
        applyEnvironment(this, env)
    }

    private fun Process.waitForOrTimeout(timeoutSeconds: Long): Int =
        if (waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            exitValue()
        } else {
            destroyForcibly()
            -1
        }

    private fun Process.collectOutputs(charset: Charset): Pair<String, String> =
        inputStream.use { input -> String(input.readAllBytes(), charset) }
            .let { stdout ->
                errorStream.use { error -> String(error.readAllBytes(), charset) }
                    .let { stderr -> stdout to stderr }
            }

    private fun Process.toCommandResult(
        timeoutSeconds: Long,
        charset: Charset
    ): CommandResult =
        collectOutputs(charset).let { (stdout, stderr) ->
            if (waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                CommandResult(exitValue(), stdout.trimEnd(), stderr.trimEnd())
            } else {
                destroyForcibly()
                CommandResult(-1, stdout, stderr)
            }
        }
}
