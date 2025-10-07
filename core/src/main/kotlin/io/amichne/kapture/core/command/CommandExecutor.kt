package io.amichne.kapture.core.command

import io.amichne.kapture.core.model.command.CommandResult
import io.amichne.kapture.core.util.Environment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [ProcessBuilder] that keeps Git UX intact for passthrough commands
 * and provides a capture helper for internal probes (e.g., rev-parse).
 */
object CommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS: Long = 60

    /**
     * Launches the provided command, inheriting the current stdin/stdout/stderr
     * so the child process behaves like a native Git invocation. Returns the
     * observed exit code or `-1` if the process exceeded the timeout.
     */
    fun passthrough(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Int = apply { require(cmd.isNotEmpty()) { "Command must not be empty" } }
        .run {
            ProcessBuilder(cmd).apply {
                workDir?.let(this::directory)
                inheritIO()
                applyEnvironment(this, env)
            }.let { processBuilder ->
                try {
                    processBuilder.start().let {
                        if (!it.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                            it.destroyForcibly()
                        } else {
                            it.exitValue()
                        }
                        -1
                    }
                } catch (ex: Exception) {
                    handleFailure(cmd, ex) { message ->
                        System.err.println("[kapture] ERROR: $message")
                        -1
                    }
                }
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
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): CommandResult = apply { require(cmd.isNotEmpty()) { "Command must not be empty" } }
        .run {
            ProcessBuilder(cmd).apply {
                workDir?.let(this::directory)
                applyEnvironment(this, env)
            }.let { processBuilder ->
                try {
                    processBuilder.start().let { process ->
                        val stdout = ByteArrayOutputStream()
                        val stderr = ByteArrayOutputStream()

                        process.inputStream.use { input -> stdout.writeBytes(input.readAllBytes()) }
                        process.errorStream.use { error -> stderr.writeBytes(error.readAllBytes()) }

                        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                            process.destroyForcibly()
                            CommandResult(-1, stdout.toString(charset), stderr.toString(charset))
                        } else {
                            CommandResult(
                                process.exitValue(),
                                stdout.toString(charset).trimEnd(),
                                stderr.toString(charset).trimEnd()
                            )
                        }
                    }
                } catch (ex: Exception) {
                    handleFailure(cmd, ex) { message ->
                        CommandResult(-1, "", message)
                    }
                }
            }
        }

    private fun <T> handleFailure(
        cmd: List<String>,
        throwable: Exception,
        onFailure: (String) -> T
    ): T {
        if (throwable is InterruptedException) {
            Thread.currentThread().interrupt()
        }

        val message = buildFailureMessage(cmd, throwable)
        Environment.debug {
            val commandDisplay = formatCommand(cmd)
            val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            "Command failure for '$commandDisplay': ${throwable::class.java.simpleName}$detail"
        }

        if (Environment.debugEnabled) {
            throwable.printStackTrace(System.err)
        }

        return onFailure(message)
    }

    private fun buildFailureMessage(
        cmd: List<String>,
        throwable: Exception
    ): String {
        val commandDisplay = formatCommand(cmd)
        val executable = cmd.firstOrNull()?.takeIf { it.isNotBlank() }

        return when (throwable) {
            is InterruptedException -> "Command '$commandDisplay' was interrupted"
            is IOException -> {
                val rawDetail = throwable.message?.takeIf { it.isNotBlank() }
                val detail = rawDetail?.let { sanitizeFailureDetail(it, executable) }
                val suggestion = executable?.let { "Ensure '$it' is installed and available on PATH" }
                val segments = mutableListOf("Unable to run '$commandDisplay'")
                detail?.let { segments += it.trim().trimEnd('.') }
                suggestion?.let { segments += it }
                segments.joinToString(". ")
            }

            else -> {
                val detail = throwable.message?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    "Command '$commandDisplay' failed: ${detail.trim()}"
                } else {
                    "Command '$commandDisplay' failed with ${throwable::class.java.simpleName}"
                }
            }
        }
    }

    private fun sanitizeFailureDetail(
        detail: String,
        executable: String?
    ): String {
        if (executable.isNullOrBlank()) return detail.trim()
        val pattern = Regex("Cannot run program \"${Regex.escape(executable)}\"(?: \\(.+?\\))?:\\s*")
        val sanitized = pattern.replace(detail, "").trim()
        return if (sanitized.isNotEmpty()) sanitized else detail.trim()
    }

    private fun formatCommand(cmd: List<String>): String = cmd.joinToString(" ").ifBlank { "<no command>" }

    private fun applyEnvironment(
        processBuilder: ProcessBuilder,
        env: Map<String, String>
    ) {
        if (env.isEmpty()) return
        val target = processBuilder.environment()
        for ((key, value) in env) {
            target[key] = value
        }
    }
}
