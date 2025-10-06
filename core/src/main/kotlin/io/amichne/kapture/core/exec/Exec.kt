package io.amichne.kapture.core.exec

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [ProcessBuilder] that keeps Git UX intact for passthrough commands
 * and provides a capture helper for internal probes (e.g., rev-parse).
 */
object Exec {
    private const val DEFAULT_TIMEOUT_SECONDS: Long = 60

    fun passthrough(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Int {
        require(cmd.isNotEmpty()) { "Command must not be empty" }
        val processBuilder = ProcessBuilder(cmd)
        workDir?.let(processBuilder::directory)
        processBuilder.inheritIO()
        applyEnvironment(processBuilder, env)
        val process = processBuilder.start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1
        }
        return process.exitValue()
    }

    fun capture(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        charset: Charset = Charsets.UTF_8,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): ExecResult {
        require(cmd.isNotEmpty()) { "Command must not be empty" }
        val processBuilder = ProcessBuilder(cmd)
        workDir?.let(processBuilder::directory)
        applyEnvironment(processBuilder, env)
        val process = processBuilder.start()

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        process.inputStream.use { input -> stdout.writeBytes(input.readAllBytes()) }
        process.errorStream.use { error -> stderr.writeBytes(error.readAllBytes()) }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ExecResult(-1, stdout.toString(charset), stderr.toString(charset))
        }

        return ExecResult(
            process.exitValue(),
            stdout.toString(charset).trimEnd(),
            stderr.toString(charset).trimEnd()
        )
    }

    private fun applyEnvironment(processBuilder: ProcessBuilder, env: Map<String, String>) {
        if (env.isEmpty()) return
        val target = processBuilder.environment()
        for ((key, value) in env) {
            target[key] = value
        }
    }
}

data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)
