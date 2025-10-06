package io.amichne.kapture.cli

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.exec.Exec
import io.amichne.kapture.core.git.RealGitResolver
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.model.Invocation
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.interceptors.InterceptorRegistry
import java.io.File
import kotlin.system.exitProcess

/**
 * Entry point invoked by the wrapper shim. It resolves configuration, loads the
 * real Git binary, runs each interceptor, and finally delegates the original
 * arguments to Git while preserving the user's environment and exit code.
 */
fun main(rawArgs: Array<String>) {
    val args = rawArgs.toList()
    val config = Config.load()
    val realGit = try {
        RealGitResolver.resolve(config.realGitHint)
    } catch (ex: IllegalStateException) {
        System.err.println("[kapture] ERROR: ${ex.message}")
        exitProcess(127)
    }

    val workDir = File(System.getProperty("user.dir"))
    val env = Environment.full()


    ExternalClient.from(config.external).use { client ->
        if (args.firstOrNull() == "kapture") {
            runKaptureCommand(args.drop(1), config, realGit)
            return
        }

        if (isCompletionOrHelp(args)) {
            val exitCode = Exec.passthrough(listOf(realGit) + args, workDir = workDir, env = env)
            exitProcess(exitCode)
        }

        val invocation = Invocation(args, realGit, workDir, env)

        for (interceptor in InterceptorRegistry.interceptors) {
            val exitCode = interceptor.before(invocation, config, client)
            if (exitCode != null) {
                exitProcess(exitCode)
            }
        }

        val exitCode = Exec.passthrough(listOf(realGit) + args, workDir = workDir, env = env)

        for (interceptor in InterceptorRegistry.interceptors) {
            interceptor.after(invocation, exitCode, config, client)
        }

        exitProcess(exitCode)
    }
}

/**
 * Determines whether the supplied Git arguments represent a help/completion
 * command that should bypass the interceptor pipeline and be forwarded
 * directly to the real Git executable.
 */
fun isCompletionOrHelp(args: List<String>): Boolean {
    if (args.isEmpty()) return false
    if (args.any { it.startsWith("--list-cmds") }) return true
    val first = args.first().lowercase()
    return first in setOf("help", "--version", "--exec-path", "config", "rev-parse", "for-each-ref")
}

private fun runKaptureCommand(
    args: List<String>,
    config: Config,
    realGit: String
) {
    when (args.firstOrNull()) {
        "status" -> {
            println("Kapture:")
            println("  real git: $realGit")
            val externalDescription = when (val ext = config.external) {
                is ExternalIntegration.Rest ->
                    "REST API (${ext.baseUrl})"
                is ExternalIntegration.JiraCli ->
                    "jira-cli (${ext.executable})"
            }
            println("  external integration: $externalDescription")
            println("  tracking enabled: ${config.trackingEnabled}")
        }

        "help", null -> {
            println("Available kapture commands:")
            println("  status    Show resolved git binary and configuration summary")
        }

        else -> {
            System.err.println("Unknown kapture command: ${args.first()}\nTry 'git kapture help'.")
            exitProcess(1)
        }
    }
}
