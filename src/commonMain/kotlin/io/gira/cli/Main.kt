package io.gira.cli

import kotlin.system.exitProcess

/**
 * Resolve the real git binary to invoke.  Resolution order:
 *  1. REAL_GIT environment variable takes precedence【835536755365059†L134-L137】.
 *  2. The realGitHint from configuration【835536755365059†L134-L139】.
 *  3. `command -v git` via capture【835536755365059†L134-L139】.
 *  4. Platform hints【835536755365059†L134-L139】.
 *
 * If no git can be found, the wrapper exits with code 127.
 */
private fun resolveRealGit(hint: String?): String {
    // Check REAL_GIT env
    val envReal = kotlin.system.getenv("REAL_GIT")
    if (!envReal.isNullOrBlank()) return envReal
    // Config hint
    if (!hint.isNullOrBlank()) return hint
    // command -v git
    val which = Exec.capture(listOf("sh", "-c", "command -v git"))
    if (which.exitCode == 0) {
        val path = which.stdout.trim()
        if (path.isNotEmpty()) return path
    }
    // platform hints
    for (candidate in Platform.osHintsRealGit()) {
        val result = Exec.capture(listOf("sh", "-c", "[ -x '$candidate' ] && echo '$candidate' || true"))
        if (result.exitCode == 0) {
            val path = result.stdout.trim()
            if (path.isNotEmpty()) return path
        }
    }
    println("Error: unable to locate real git binary")
    exitProcess(127)
}

/** Determine if this invocation should bypass all interceptors. */
private fun isCompletionOrHelpPath(args: List<String>): Boolean {
    if (args.any { it.startsWith("--list-cmds") }) return true
    val first = args.firstOrNull() ?: return false
    when (first.lowercase()) {
        "help" -> return true
        "--version" -> return true
        "--exec-path" -> return true
        "rev-parse" -> {
            // Completion often invokes `git rev-parse --git-path` etc.  When
            // invoked with only rev-parse we still allow passthrough.
            return args.size == 2
        }
    }
    return false
}

/** Handle custom `git gira` subcommands.  If the command exists, run it and exit. */
private fun runGiraCommand(args: List<String>, config: Config, ext: ExternalClient, realGit: String): Unit {
    val commandName = args.firstOrNull()
    val cmd = Registry.findCommand(commandName)
    if (cmd == null) {
        println("Unknown gira command: ${commandName ?: "<none>"}")
        exitProcess(1)
    } else {
        val ctx = Registry.Context(config, ext, realGit)
        val code = cmd.run(ctx, args.drop(1))
        exitProcess(code)
    }
}

/**
 * Entry point for the wrapper.  Delegates to the real git binary while
 * applying optional interceptors for branch policy, status gate and
 * time tracking【835536755365059†L146-L181】.
 */
fun main(argv: Array<String>) {
    val args = argv.toList()
    val config = Config.load()
    val realGit = resolveRealGit(config.realGitHint)
    val ext = ExternalClient(config.externalBaseUrl, config.apiKey, config.trackingEnabled)

    // Custom subcommands: `git gira <name>`【835536755365059†L155-L158】
    if (args.firstOrNull()?.equals("gira", ignoreCase = true) == true) {
        runGiraCommand(args.drop(1), config, ext, realGit)
        return
    }

    // Skip interceptors for completion and help【835536755365059†L161-L164】
    if (isCompletionOrHelpPath(args)) {
        val code = Exec.passthrough(listOf(realGit) + args)
        exitProcess(code)
    }

    val invocation = Invocation(args, args.firstOrNull()?.lowercase(), realGit)
    val interceptors = Registry.interceptors(config, ext)
    // Before hooks【835536755365059†L169-L171】
    for (i in interceptors) {
        if (i.matches(invocation)) {
            val rc = i.before(invocation)
            if (rc != null) {
                exitProcess(rc)
            }
        }
    }
    // Execute git with TTY inheritance【835536755365059†L30-L36】
    val childCode = Exec.passthrough(listOf(realGit) + args)
    var code = childCode
    // After hooks
    for (i in interceptors) {
        if (i.matches(invocation)) {
            code = i.after(invocation, code)
        }
    }
    exitProcess(code)
}