package io.amichne.kapture.core.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object FileUtils {
    fun writeAtomically(path: Path, content: String) {
        val parent = path.parent ?: path.toAbsolutePath().parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val prefix = path.fileName?.toString()?.takeIf { it.length >= 3 } ?: "tmp"
        val tempFile = parent?.let { Files.createTempFile(it, prefix, ".tmp") }
            ?: Files.createTempFile(prefix, ".tmp")
        Files.writeString(tempFile, content)
        val options = arrayOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        runCatching {
            Files.move(tempFile, path, *options)
        }.onFailure {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
