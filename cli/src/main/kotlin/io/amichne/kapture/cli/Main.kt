package io.amichne.kapture.cli

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.git.RealGitResolver
import io.amichne.kapture.core.model.command.CommandInvocation
import io.amichne.kapture.core.model.config.Cli
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.ImmediateRules
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.util.ConfigLoader
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.interceptors.InterceptorRegistry
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Entry point invoked by the wrapper shim. It resolves configuration, loads the
 * real Git binary, runs each interceptor, and finally delegates the original
 * arguments to Git while preserving the user's environment and exit code.
 */
fun main(rawArgs: Array<String>) {
    // Extract config path flags before processing
    val (configPath, remainingArgs) = extractConfigPath(rawArgs.toList())

    val config = ConfigLoader.load(configPath)
    val realGit = try {
        RealGitResolver.resolve(config.realGitHint)
    } catch (ex: IllegalStateException) {
        System.err.println("[kapture] ERROR: ${ex.message}")
        exitProcess(127)
    }

    val workDir = File(System.getProperty("user.dir"))
    val env = Environment.full()
    val evaluation = evaluateImmediateRules(remainingArgs, config, env)
    val args = evaluation.args

    ExternalClient.from(config).use { client ->
        // Check for custom git commands (subtask, review, merge, work, start)
        val customCommand = args.firstOrNull()?.lowercase()
        if (customCommand != null && customCommand in setOf("subtask", "review", "merge", "work", "start")) {
            runCustomGitCommand(customCommand, args.drop(1), config, workDir, env, client)
            return
        }

        // Handle status and help specially - append Kapture info to git's output
        if (customCommand == "status") {
            val gitExitCode = CommandExecutor.passthrough(listOf(realGit) + args, workDir = workDir, env = env)
            appendKaptureStatus(config, realGit)
            exitProcess(gitExitCode)
        }

        if (customCommand == "help" || customCommand == "--help" || args.contains("--help")) {
            val gitExitCode = CommandExecutor.passthrough(listOf(realGit) + args, workDir = workDir, env = env)
            appendKaptureHelp()
            exitProcess(gitExitCode)
        }

        if (evaluation.bypass) {
            val exitCode = CommandExecutor.passthrough(listOf(realGit) + args, workDir = workDir, env = env)
            exitProcess(exitCode)
        }

        val commandInvocation = CommandInvocation(args, realGit, workDir, env)

        val shouldRunInterceptors = config.immediateRules.enabled && !evaluation.optedOut
        if (shouldRunInterceptors) {
            for (interceptor in InterceptorRegistry.interceptors) {
                val exitCode = interceptor.before(commandInvocation, config, client)
                if (exitCode != null) {
                    exitProcess(exitCode)
                }
            }
        }

        val exitCode = CommandExecutor.passthrough(listOf(realGit) + args, workDir = workDir, env = env)

        if (shouldRunInterceptors) {
            for (interceptor in InterceptorRegistry.interceptors) {
                interceptor.after(commandInvocation, exitCode, config, client)
            }
        }

        exitProcess(exitCode)
    }
}

/**
 * Determines whether the supplied Git arguments represent a help/completion
 * command that should bypass the interceptor pipeline and be forwarded
 * directly to the real Git executable.
 */
private fun isBypassCommand(
    args: List<String>,
    immediateRules: ImmediateRules
): Boolean {
    if (args.isEmpty()) return false
    val bypassArgMatch = immediateRules.bypassArgsContains.any { needle ->
        args.any { it.contains(needle) }
    }
    if (bypassArgMatch) return true
    val first = args.first().lowercase()
    return immediateRules.bypassCommands.any { it.equals(first, ignoreCase = true) }
}

private fun runCustomGitCommand(
    command: String,
    args: List<String>,
    config: Config,
    workDir: File,
    env: Map<String, String>,
    client: ExternalClient<*>
) {
    when (command) {
        "subtask" -> WorkflowCommands.executeSubtask(args, config, workDir, env, client)
        "start" -> WorkflowCommands.executeStart(args, config, workDir, env, client)
        "review" -> WorkflowCommands.executeReview(args, config, workDir, env, client)
        "merge" -> WorkflowCommands.executeMerge(args, config, workDir, env, client)
        "work" -> WorkflowCommands.executeWork(args, config, workDir, env, client)
        else -> {
            System.err.println("Unknown custom git command: $command")
            exitProcess(1)
        }
    }
}

private fun appendKaptureStatus(
    config: Config,
    realGit: String
) {
    println()
    println("Kapture:")
    println("  real git: $realGit")
    val externalDescription = when (val ext = config.external) {
        is Plugin.Http -> "REST API (${ext.baseUrl})"
        is Cli -> "jira-cli (${ext.executable})"
    }
    println("  external integration: $externalDescription")
    println("  tracking enabled: ${config.trackingEnabled}")
}

private fun appendKaptureHelp() {
    println()
    println("Kapture workflow commands:")
    println("  git subtask <PARENT> <title>    Create a subtask and transition to In Progress")
    println("  git subtask <SUBTASK-ID>        Transition subtask to In Progress")
    println("  git start <TASK-ID>             Create a branch and transition task to In Progress")
    println("  git review [<title>]            Create PR and transition to Code Review")
    println("  git merge [<id>] [--close-parent]  Merge PR and close subtask")
    println("  git work                        Show work log and activity")
}

internal fun evaluateImmediateRules(
    rawArgs: List<String>,
    config: Config,
    env: Map<String, String>
): ImmediateRulesEvaluation {
    val immediate = config.immediateRules
    val (cleanArgs, optOutByFlag) = stripOptOutFlags(rawArgs, immediate.optOutFlags)
    val optOutByEnv = immediate.optOutEnvVars.any { key ->
        env[key]?.isNotBlank() == true
    }
    val bypass = isBypassCommand(cleanArgs, immediate)
    return ImmediateRulesEvaluation(
        args = cleanArgs,
        bypass = bypass,
        optedOut = optOutByFlag || optOutByEnv
    )
}

private fun stripOptOutFlags(
    args: List<String>,
    flags: Set<String>
): Pair<List<String>, Boolean> {
    if (flags.isEmpty()) return args to false
    var optedOut = false
    val filtered = args.filter { token ->
        if (flags.contains(token)) {
            optedOut = true
            false
        } else true
    }
    return filtered to optedOut
}

internal data class ImmediateRulesEvaluation(
    val args: List<String>,
    val bypass: Boolean,
    val optedOut: Boolean
)

/**
 * Extracts the config path from -k or --konfig flags and returns it along with
 * the remaining arguments with these flags removed.
 */
private fun extractConfigPath(args: List<String>): Pair<Path?, List<String>> =
//    val remaining = mutableListOf<String>()
//    var configPath: Path? = null
//    var i = 0
    (args.indexOf("-k").takeUnless { it == -1 } ?: args.indexOf("--konfig").takeUnless { it == -1 })?.let { index ->
//        args.slice(index..index + 1)
//            .also { consumed -> with(args) { minus(args.slice(index..index + 1)) } }
        Paths.get(args[index + 1]) to with(args) { minus(args.slice(index..index + 1)) }
    } ?: (null to args)

//    while (i < args.size) {
//        val arg = args[i]
//        when {
//            arg == "-k" || arg == "--konfig" -> {
//                // Next argument is the path
//                if (i + 1 < args.size) {
//                    configPath = Paths.get(args[i + 1])
//                    i += 2 // Skip both the flag and the value
//                } else {
//                    System.err.println("[kapture] ERROR: $arg requires a path argument")
//                    exitProcess(1)
//                }
//            }
//
//            arg.startsWith("--konfig=") -> {
//                configPath = Paths.get(arg.substring(9))
//                i++
//            }
//
//            arg.startsWith("-k=") -> {
//                configPath = Paths.get(arg.substring(3))
//                i++
//            }
//
//            else -> {
//                remaining.add(arg)
//                i++
//            }
//        }
//    }

//    return configPath to remaining
