package io.amichne.kapture.interceptors.support

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class GitTestRepository private constructor(
    val root: Path,
    private val gitExecutable: String
) : Closeable {

    companion object {
        fun create(): GitTestRepository {
            val git = System.getenv("REAL_GIT")?.takeIf { it.isNotBlank() } ?: "git"
            val repoDir = Files.createTempDirectory("kapture-repo-")
            val repository = GitTestRepository(repoDir, git)
            repository.init()
            return repository
        }
    }

    private fun init() {
        run("init")
        // Configure default user for commits to avoid interactive prompts.
        run("config", "user.name", "Kapture Test")
        run("config", "user.email", "kapture@example.test")
    }

    fun checkoutBranch(
        name: String,
        create: Boolean = false
    ) {
        if (create) {
            run("checkout", "-b", name)
        } else {
            run("checkout", name)
        }
    }

    fun createFile(
        relative: String,
        content: String = "content"
    ) {
        val path = root.resolve(relative)
        path.parent?.createDirectories()
        path.writeText(content)
        run("add", relative)
    }

    fun commit(message: String = "test commit") {
        run("commit", "-m", message)
    }

    fun gitPath(): String = gitExecutable

    fun environment(): Map<String, String> = mapOf("GIT_TERMINAL_PROMPT" to "0")

    private fun run(vararg args: String): String {
        val process = ProcessBuilder(listOf(gitExecutable) + args)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw IllegalStateException("git ${args.joinToString(" ")} failed with $exit: $output")
        }
        return output
    }

    override fun close() {
        root.toFile().deleteRecursively()
    }
}
