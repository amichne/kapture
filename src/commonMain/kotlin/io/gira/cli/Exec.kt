package io.gira.cli

import kotlin.native.concurrent.ThreadLocal

/**
 * Data class capturing the result of a captured process.  When the
 * wrapper executes tiny helper commands (such as `git rev-parse`) it
 * uses the capture path to collect stdout/stderr for inspection【835536755365059†L86-L100】.
 *
 * @param exitCode the return code of the child process
 * @param stdout the full stdout of the process
 * @param stderr the full stderr of the process
 */
data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * Platform‑agnostic process execution.  In common code we declare
 * expectations for two kinds of process invocation:
 *  - passthrough: spawn a child inheriting this process's stdin,
 *    stdout and stderr.  This preserves TTY semantics which are
 *    essential for git's interactive and completion flows【835536755365059†L61-L64】.
 *  - capture: spawn a child with its stdout and stderr piped back
 *    to the caller.  Capture is used only for short helper commands
 *    that we need to examine (for example, resolving the current
 *    branch via `git rev-parse`)【835536755365059†L146-L174】.
 */
expect object Exec {
    /**
     * Execute a command by inheriting the parent's standard streams.
     * This call blocks until the child process terminates and returns
     * the child's exit code.  Use this path for all interactive
     * invocations of the underlying git binary【835536755365059†L30-L35】.
     *
     * @param cmd the command and its arguments; the first element must
     *        be the executable to run.
     * @param workDir optional working directory; if null the current
     *        working directory is used.
     * @param env a map of environment variables to inject into the
     *        child's environment.  Variables not specified here are
     *        inherited unchanged from the parent.  Do not mutate or
     *        remove GIT_* variables by default【835536755365059†L61-L65】.
     */
    fun passthrough(cmd: List<String>, workDir: String? = null, env: Map<String, String> = emptyMap()): Int

    /**
     * Execute a command and return its captured output.  This path
     * should be used only for non‑interactive helper calls, such as
     * resolving the current branch name via `git rev-parse`【835536755365059†L30-L36】.
     *
     * The process will not inherit the parent's TTY; instead its
     * stdout and stderr will be collected into memory.  This function
     * returns an [ExecResult] containing the exit code and the full
     * stdout/stderr content.
     *
     * @param cmd the command and its arguments; the first element must
     *        be the executable to run.
     * @param workDir optional working directory; if null the current
     *        working directory is used.
     * @param env a map of environment variables to inject into the
     *        child's environment.  Variables not specified here are
     *        inherited unchanged from the parent.
     */
    fun capture(cmd: List<String>, workDir: String? = null, env: Map<String, String> = emptyMap()): ExecResult
}

/**
 * Platform specific hints and configuration.  The wrapper may need
 * information about the host environment to resolve the real git
 * binary.  On macOS for example, Homebrew and Xcode may install git
 * in different locations; the plan suggests consulting a list of
 * hints returned by [Platform.osHintsRealGit]【835536755365059†L104-L107】.
 */
expect object Platform {
    /**
     * Return a list of candidate paths to the real git binary on this
     * platform.  These hints are consulted after the REAL_GIT
     * environment variable, the config hint, and the `command -v git`
     * result when resolving the underlying git to invoke【835536755365059†L134-L139】.
     */
    fun osHintsRealGit(): List<String>

    /**
     * Return the shell to use for captured helper commands.  Native
     * implementations will typically return ["/bin/sh", "-lc"] to
     * execute a command string.  Passthrough invocations avoid using
     * a shell to preserve signal handling【835536755365059†L116-L124】.
     */
    val shell: List<String>
}