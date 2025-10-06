package io.amichne.kapture.core.git

import io.amichne.kapture.core.util.Environment
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RealGitResolver {
    private val fallbackPaths = listOf(
        "/usr/bin/git",
        "/usr/local/bin/git",
        "/opt/homebrew/bin/git",
        "C:/Program Files/Git/bin/git.exe",
        "C:/Program Files/Git/cmd/git.exe"
    )

    /**
     * Resolves the path to the real Git executable, honouring the `REAL_GIT`
     * environment variable, config hint, PATH resolution, and OS-specific
     * fallbacks while ensuring the wrapper does not recurse onto itself.
     */
    fun resolve(configHint: String?): String {
        val candidates = LinkedHashSet<Path>()
        candidateFromEnv()?.let(candidates::add)
        configHint?.takeUnless { it.isBlank() }?.let { candidates.add(Paths.get(it)) }
        findInPath("git")?.let(candidates::add)
        fallbackPaths.forEach { candidates.add(Paths.get(it)) }

        val currentArtifact = currentArtifactPath()

        for (candidate in candidates) {
            val normalized = candidate.toAbsolutePath().normalize()
            if (!Files.exists(normalized) || !Files.isRegularFile(normalized) || !Files.isExecutable(normalized)) {
                continue
            }
            if (currentArtifact != null && runCatching { Files.isSameFile(normalized, currentArtifact) }.getOrDefault(
                    false
                )) {
                Environment.debug { "Skipping $normalized (points to wrapper artifact)" }
                continue
            }
            Environment.debug { "Resolved git binary at $normalized" }
            return normalized.toString()
        }

        throw IllegalStateException("Unable to resolve real git binary")
    }

    private fun candidateFromEnv(): Path? = System.getenv("REAL_GIT")
        ?.takeUnless { it.isBlank() }
        ?.let { Paths.get(it) }

    private fun findInPath(binary: String): Path? {
        val pathEnv = System.getenv("PATH") ?: return null
        return pathEnv.split(File.pathSeparatorChar)
            .asSequence()
            .map { dir -> Paths.get(dir).resolve(binary) }
            .firstOrNull { Files.exists(it) && Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    private fun currentArtifactPath(): Path? = runCatching {
        val uri = RealGitResolver::class.java.protectionDomain.codeSource.location.toURI()
        val path = Paths.get(uri)
        if (Files.isDirectory(path)) null else path.toAbsolutePath().normalize()
    }.getOrNull()
}
