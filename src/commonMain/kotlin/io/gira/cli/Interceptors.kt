package io.gira.cli

import kotlinx.serialization.Serializable
import kotlin.system.getTimeMillis

/**
 * Marker interface for an interceptor.  Interceptors can inspect a
 * [Invocation] before the underlying git command executes and after
 * it finishes.  The [before] method may return a non‑null exit code
 * to short‑circuit execution.  The [after] method can adjust the
 * exit code before it is returned to the caller【835536755365059†L148-L181】.
 */
interface Interceptor {
    /** Return true if this interceptor should consider this invocation. */
    fun matches(invocation: Invocation): Boolean
    /**
     * Invoked before the real git command executes.  Returning a
     * non‑null integer will prevent the underlying git from running and
     * cause the wrapper to exit immediately with that code【835536755365059†L169-L171】.
     */
    fun before(invocation: Invocation): Int?
    /**
     * Invoked after the real git command exits.  Receives the child's
     * exit code and may return an adjusted code.  Always returns an
     * integer.
     */
    fun after(invocation: Invocation, exitCode: Int): Int
}

/**
 * Branch naming policy enforcement.  This interceptor triggers on
 * branch creation commands (checkout, switch or branch with -b/-B/-c/-C)
 * and validates the branch name against a configured regex.  It may
 * optionally verify that the ticket portion of the branch exists
 * remotely.  Depending on the configured mode, violations can be
 * reported as warnings or block the command【835536755365059†L231-L238】.
 */
class BranchPolicy(private val config: Config, private val ext: ExternalClient) : Interceptor {
    private val pattern: Regex by lazy { Regex(config.branchPattern) }

    override fun matches(invocation: Invocation): Boolean {
        val cmd = invocation.command ?: return false
        if (cmd == "checkout" || cmd == "switch" || cmd == "branch") {
            // Trigger only when user is creating a new branch via flags
            val args = invocation.args
            val hasCreateFlag = args.any {
                it == "-b" || it == "-B" || it == "-c" || it == "-C" || it == "--create"
            }
            return hasCreateFlag
        }
        return false
    }

    override fun before(invocation: Invocation): Int? {
        // Extract the branch name: assume the last non‑flag argument is
        // the new branch.  This heuristic covers checkout -b foo and
        // branch -c foo patterns.  If no branch is found, skip.
        val branch = invocation.args.lastOrNull { !it.startsWith("-") }
        if (branch == null) return null
        val match = pattern.matchEntire(branch)
        val mode = config.enforcement.branchPolicy
        if (match == null) {
            when (mode) {
                Mode.WARN -> {
                    println("[WARN] Branch '$branch' does not match pattern ${config.branchPattern}")
                    return null
                }
                Mode.BLOCK -> {
                    println("[ERROR] Branch '$branch' does not match required pattern; aborting")
                    return 2
                }
                Mode.OFF -> return null
            }
        } else {
            // Optionally verify ticket existence
            val ticket = match.groups["ticket"]?.value
            if (!ticket.isNullOrBlank()) {
                val status = ext.getTicketStatus(ticket)
                if (status == null) {
                    when (mode) {
                        Mode.WARN -> {
                            println("[WARN] Ticket '$ticket' does not exist")
                            return null
                        }
                        Mode.BLOCK -> {
                            println("[ERROR] Ticket '$ticket' does not exist; aborting")
                            return 2
                        }
                        Mode.OFF -> return null
                    }
                }
            }
        }
        return null
    }

    override fun after(invocation: Invocation, exitCode: Int): Int {
        // Branch policy does not adjust exit code on the way out
        return exitCode
    }
}

/**
 * Commit and push status gate.  This interceptor triggers on `commit`
 * and `push` and consults the remote ticket status before allowing
 * the command to proceed【835536755365059†L239-L245】.  The gate may be disabled,
 * warn only, or block depending on configuration.  Different exit
 * codes are returned for commit and push failures【835536755365059†L283-L285】.
 */
class StatusGate(private val config: Config, private val ext: ExternalClient) : Interceptor {
    private val pattern: Regex by lazy { Regex(config.branchPattern) }

    override fun matches(invocation: Invocation): Boolean {
        val cmd = invocation.command ?: return false
        return cmd == "commit" || cmd == "push"
    }

    override fun before(invocation: Invocation): Int? {
        val mode = config.enforcement.statusCheck
        if (mode == Mode.OFF) return null
        val cmd = invocation.command ?: return null
        // Determine current branch using git rev-parse
        val result = Exec.capture(listOf(invocation.realGit, "rev-parse", "--abbrev-ref", "HEAD"))
        val branch = if (result.exitCode == 0) result.stdout.trim() else null
        if (branch.isNullOrBlank()) return null
        val match = pattern.matchEntire(branch)
        val ticket = match?.groups?.get("ticket")?.value
        if (ticket.isNullOrBlank()) {
            // Without a ticket in the branch name we cannot enforce
            return null
        }
        val status = ext.getTicketStatus(ticket)
        if (status == null) {
            // Unknown status; treat as allowed
            return null
        }
        val allowed = when (cmd) {
            "commit" -> config.statusRules.allowCommitWhen
            "push" -> config.statusRules.allowPushWhen
            else -> emptySet()
        }
        if (status !in allowed) {
            when (mode) {
                Mode.WARN -> {
                    println("[WARN] Ticket '$ticket' is in status '$status' which is not allowed for $cmd")
                    return null
                }
                Mode.BLOCK -> {
                    val code = if (cmd == "commit") 3 else 4
                    println("[ERROR] Ticket '$ticket' status '$status' does not permit $cmd; aborting")
                    return code
                }
                Mode.OFF -> return null
            }
        }
        return null
    }

    override fun after(invocation: Invocation, exitCode: Int): Int = exitCode
}

/**
 * Time tracking interceptor.  Records the wall clock time before
 * execution and posts a duration to the external service after
 * execution.  This interceptor never blocks the command【835536755365059†L246-L250】.
 */
class TimeTrack(private val config: Config, private val ext: ExternalClient) : Interceptor {
    /**
     * Always applies: we want to track time spent on a branch for
     * every invocation.  The duration is derived from how long the
     * developer remained on a branch rather than the execution time of
     * a single command.
     */
    override fun matches(invocation: Invocation): Boolean = true

    override fun before(invocation: Invocation): Int? {
        // Nothing to do before command execution; we track branch
        // transitions in the after hook.
        return null
    }

    override fun after(invocation: Invocation, exitCode: Int): Int {
        // Determine the branch currently checked out
        val res = Exec.capture(listOf(invocation.realGit, "rev-parse", "--abbrev-ref", "HEAD"))
        val currentBranch = if (res.exitCode == 0) res.stdout.trim() else null
        val now = getTimeMillis()
        if (!currentBranch.isNullOrBlank()) {
            val previous = BranchTimeStore.readState()
            if (previous != null && previous.first != currentBranch) {
                val (prevBranch, start) = previous
                val duration = now - start
                // Extract ticket from previous branch
                val ticket = pattern.find(prevBranch)?.groups?.get("ticket")?.value
                // Record the time spent on the previous branch as an event
                ext.postEvent(
                    command = "branch",
                    args = invocation.args,
                    branch = prevBranch,
                    ticket = ticket,
                    durationMs = duration
                )
            }
            // Update the state to the current branch with the current timestamp
            BranchTimeStore.writeState(currentBranch, now)
        }
        return exitCode
    }

    private val pattern: Regex by lazy { Regex(config.branchPattern) }

    /**
     * Simple file‑based store for tracking the current branch and start
     * timestamp across invocations.  It uses the HOME environment
     * variable to locate a state file at ~/.git-wrapper/branch-time.
     * The file format is a single line: "<branch> <startEpochMs>".
     * A change of branch triggers the duration calculation for the
     * previous branch.
     */
    private object BranchTimeStore {
        private val home: String? = kotlin.system.getenv("HOME")
        private val stateFile: String? = home?.let { "$it/.git-wrapper/branch-time" }
        fun readState(): Pair<String, Long>? {
            val path = stateFile ?: return null
            // Use Exec to read the file to avoid platform‑specific file APIs.
            val res = Exec.capture(listOf("sh", "-c", "[ -f '$path' ] && cat '$path' || true"))
            if (res.exitCode != 0) return null
            val line = res.stdout.trim()
            if (line.isBlank()) return null
            val parts = line.split(" ")
            if (parts.size != 2) return null
            return try {
                parts[0] to parts[1].toLong()
            } catch (_: Exception) {
                null
            }
        }
        fun writeState(branch: String, startMs: Long) {
            val path = stateFile ?: return
            // Ensure the directory exists.  Use mkdir -p for portability.
            Exec.passthrough(listOf("sh", "-c", "mkdir -p '${path.substringBeforeLast('/')}'") )
            // Write the branch and timestamp to the file.  Use a single
            // redirect to avoid partial writes.
            Exec.passthrough(listOf("sh", "-c", "printf '%s %d\n' '$branch' $startMs > '$path'"))
        }
    }
}

/**
 * Registry of interceptors and custom commands.  The wrapper instantiates
 * each interceptor with the current configuration and external
 * client.  Additional commands can be added to the [commands] list
 * without using ServiceLoader【835536755365059†L259-L266】.
 */
object Registry {
    /** Build a list of interceptors for this invocation. */
    fun interceptors(config: Config, ext: ExternalClient): List<Interceptor> = listOf(
        BranchPolicy(config, ext),
        StatusGate(config, ext),
        TimeTrack(config, ext)
    )

    /** Interface for custom `git gira <cmd>` commands. */
    interface Command {
        val name: String
        fun run(ctx: Context, args: List<String>): Int
    }

    /** Simple context passed to commands.  Provides access to config and external client. */
    data class Context(val config: Config, val ext: ExternalClient, val realGit: String)

    /** Example command `hello` that prints a greeting.  Additional commands may be registered here. */
    private class Hello : Command {
        override val name: String = "hello"
        override fun run(ctx: Context, args: List<String>): Int {
            println("Hello from git gira!")
            return 0
        }
    }

    /** List of available commands. */
    val commands: List<Command> = listOf(Hello())

    /** Find a command by name. */
    fun findCommand(name: String?): Command? = commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}