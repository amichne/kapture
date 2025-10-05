package io.gira.cli

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.*

/**
 * Native implementation of [Exec] using POSIX primitives.  The
 * passthrough path uses `fork` followed by `execvp` to run the
 * requested command with the current process's standard file
 * descriptors inherited.  The capture path uses pipes to collect
 * stdout and stderr.  This implementation is intentionally low
 * level to preserve correct TTY semantics【835536755365059†L61-L65】.
 */
actual object Exec {
    actual fun passthrough(cmd: List<String>, workDir: String?, env: Map<String, String>): Int {
        // Guard against empty command lists
        if (cmd.isEmpty()) return 127
        return memScoped {
            val pid = fork()
            if (pid < 0) {
                // fork failed
                return@memScoped 127
            }
            if (pid == 0) {
                // Child process
                // Change working directory if specified
                if (workDir != null) {
                    chdir(workDir)
                }
                // Construct argv array for execvp
                val argv: CValuesRef<CPointer<ByteVar>?> = allocArray(cmd.size + 1)
                for (i in cmd.indices) {
                    argv[i] = cmd[i].cstr.ptr
                }
                argv[cmd.size] = null
                // Build environment array if any overrides are provided.  If no
                // overrides are specified, inherit from parent via execvp.
                if (env.isNotEmpty()) {
                    // Build environ from existing environment and overrides
                    val currentEnv = environ
                    val envList = mutableListOf<String>()
                    var i = 0
                    while (currentEnv?.get(i) != null) {
                        val entry = currentEnv!![i]!!.toKString()
                        val key = entry.substringBefore('=')
                        if (env.containsKey(key)) {
                            // skip, will add override later
                        } else {
                            envList += entry
                        }
                        i++
                    }
                    for ((k, v) in env) {
                        envList += "$k=$v"
                    }
                    val envArr: CValuesRef<CPointer<ByteVar>?> = allocArray(envList.size + 1)
                    for (j in envList.indices) {
                        envArr[j] = envList[j].cstr.ptr
                    }
                    envArr[envList.size] = null
                    // Use execve to supply explicit environment
                    execve(cmd[0], argv, envArr)
                } else {
                    execvp(cmd[0], argv)
                }
                // If we reach here exec failed
                perror("execvp")
                exit(127)
            } else {
                // Parent: wait for child
                var status: Int = 0
                waitpid(pid, status.ptr, 0)
                return@memScoped WEXITSTATUS(status)
            }
        }
    }

    actual fun capture(cmd: List<String>, workDir: String?, env: Map<String, String>): ExecResult {
        if (cmd.isEmpty()) return ExecResult(127, "", "")
        return memScoped {
            // Create pipe for stdout and stderr.  We'll combine them
            // into a single pipe by dup2ing both stdout and stderr onto
            // the write end.
            val fds = IntArray(2)
            if (pipe(fds) != 0) {
                return@memScoped ExecResult(127, "", "")
            }
            val pid = fork()
            if (pid < 0) {
                close(fds[0])
                close(fds[1])
                return@memScoped ExecResult(127, "", "")
            }
            if (pid == 0) {
                // Child: redirect stdout and stderr to pipe write end
                // Close read end
                close(fds[0])
                dup2(fds[1], STDOUT_FILENO)
                dup2(fds[1], STDERR_FILENO)
                // Close original write end after dup2
                close(fds[1])
                // Change working directory if requested
                if (workDir != null) {
                    chdir(workDir)
                }
                // Build argv
                val argv: CValuesRef<CPointer<ByteVar>?> = allocArray(cmd.size + 1)
                for (i in cmd.indices) {
                    argv[i] = cmd[i].cstr.ptr
                }
                argv[cmd.size] = null
                // Build environment
                if (env.isNotEmpty()) {
                    val currentEnv = environ
                    val envList = mutableListOf<String>()
                    var i = 0
                    while (currentEnv?.get(i) != null) {
                        val entry = currentEnv!![i]!!.toKString()
                        val key = entry.substringBefore('=')
                        if (env.containsKey(key)) {
                            // skip override
                        } else {
                            envList += entry
                        }
                        i++
                    }
                    for ((k, v) in env) {
                        envList += "$k=$v"
                    }
                    val envArr: CValuesRef<CPointer<ByteVar>?> = allocArray(envList.size + 1)
                    for (j in envList.indices) {
                        envArr[j] = envList[j].cstr.ptr
                    }
                    envArr[envList.size] = null
                    execve(cmd[0], argv, envArr)
                } else {
                    execvp(cmd[0], argv)
                }
                perror("execvp")
                exit(127)
            } else {
                // Parent: close write end and read from read end
                close(fds[1])
                val buffer = StringBuilder()
                // Read until EOF
                val buf = ByteArray(4096)
                while (true) {
                    val rc = read(fds[0], buf.refTo(0), buf.size.convert())
                    if (rc == 0L) break
                    if (rc < 0L) break
                    val chunk = buf.decodeToString(startIndex = 0, endIndex = rc.toInt())
                    buffer.append(chunk)
                }
                close(fds[0])
                var status: Int = 0
                waitpid(pid, status.ptr, 0)
                val exitCode = WEXITSTATUS(status)
                val out = buffer.toString()
                // Since stdout and stderr are multiplexed, we can't separate
                // them easily without a second pipe.  Return both as stdout
                return@memScoped ExecResult(exitCode, out, "")
            }
        }
    }
}

/**
 * Native implementation of [Platform].  Provide simple defaults for
 * locating the real git binary and for constructing a shell command
 * for captured helper calls.  These hints are consulted after
 * environment and config when resolving git【835536755365059†L104-L107】.
 */
actual object Platform {
    actual fun osHintsRealGit(): List<String> {
        return listOf(
            "/usr/bin/git",
            "/opt/homebrew/bin/git",
            "/usr/local/bin/git"
        )
    }
    actual val shell: List<String> = listOf("/bin/sh", "-lc")
}