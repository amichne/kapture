package io.amichne.kapture.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileUtilsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writeAtomically creates file with content`() {
        val targetFile = tempDir.resolve("test.txt")
        val content = "Hello, World!"

        FileUtils.writeAtomically(targetFile, content)

        assertTrue(Files.exists(targetFile))
        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically replaces existing file`() {
        val targetFile = tempDir.resolve("existing.txt")
        Files.writeString(targetFile, "old content")

        val newContent = "new content"
        FileUtils.writeAtomically(targetFile, newContent)

        assertEquals(newContent, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically creates parent directories if missing`() {
        val targetFile = tempDir.resolve("nested/deep/file.txt")
        val content = "content"

        FileUtils.writeAtomically(targetFile, content)

        assertTrue(Files.exists(targetFile))
        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles empty content`() {
        val targetFile = tempDir.resolve("empty.txt")

        FileUtils.writeAtomically(targetFile, "")

        assertTrue(Files.exists(targetFile))
        assertEquals("", Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles multi-line content`() {
        val targetFile = tempDir.resolve("multiline.txt")
        val content = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()

        FileUtils.writeAtomically(targetFile, content)

        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles special characters`() {
        val targetFile = tempDir.resolve("special.txt")
        val content = "Special: !@#$%^&*()_+-=[]{}|;':\",./<>?"

        FileUtils.writeAtomically(targetFile, content)

        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles unicode content`() {
        val targetFile = tempDir.resolve("unicode.txt")
        val content = "Unicode: 你好世界 🚀 émojis"

        FileUtils.writeAtomically(targetFile, content)

        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles large content`() {
        val targetFile = tempDir.resolve("large.txt")
        val content = "x".repeat(10_000)

        FileUtils.writeAtomically(targetFile, content)

        assertEquals(content, Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically can write to same file multiple times`() {
        val targetFile = tempDir.resolve("multiple.txt")

        FileUtils.writeAtomically(targetFile, "first")
        assertEquals("first", Files.readString(targetFile))

        FileUtils.writeAtomically(targetFile, "second")
        assertEquals("second", Files.readString(targetFile))

        FileUtils.writeAtomically(targetFile, "third")
        assertEquals("third", Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically creates parent directory when parent is null initially`() {
        // Create a file at the root of tempDir (which already exists)
        val targetFile = tempDir.resolve("root-file.txt")

        FileUtils.writeAtomically(targetFile, "content")

        assertTrue(Files.exists(targetFile))
        assertEquals("content", Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles short filename for temp file prefix`() {
        val targetFile = tempDir.resolve("ab.txt")

        FileUtils.writeAtomically(targetFile, "short name")

        assertTrue(Files.exists(targetFile))
        assertEquals("short name", Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically handles very short filename`() {
        val targetFile = tempDir.resolve("a")

        FileUtils.writeAtomically(targetFile, "single char name")

        assertTrue(Files.exists(targetFile))
        assertEquals("single char name", Files.readString(targetFile))
    }

    @Test
    fun `writeAtomically preserves file after multiple rapid writes`() {
        val targetFile = tempDir.resolve("rapid.txt")

        // Simulate rapid successive writes
        repeat(10) { i ->
            FileUtils.writeAtomically(targetFile, "write-$i")
            assertEquals("write-$i", Files.readString(targetFile))
        }
    }
}
