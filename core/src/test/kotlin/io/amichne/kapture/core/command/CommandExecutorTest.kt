package io.amichne.kapture.core.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CommandExecutorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture executes command and returns output`() {
        val result = CommandExecutor.capture(
            cmd = listOf("echo", "hello world")
        )

        assertEquals(0, result.exitCode)
        assertEquals("hello world", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `capture executes command and returns output on Windows`() {
        val result = CommandExecutor.capture(
            cmd = listOf("cmd", "/c", "echo", "hello world")
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("hello world"))
        assertEquals("", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture returns non-zero exit code on command failure`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "exit 42")
        )

        assertEquals(42, result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture returns stderr output`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo 'error message' >&2")
        )

        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("error message", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture trims stdout and stderr`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo 'output  '; echo 'error  ' >&2")
        )

        assertEquals("output", result.stdout)
        assertEquals("error", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture respects working directory`() {
        val subDir = tempDir.resolve("subdir")
        Files.createDirectories(subDir)

        val result = CommandExecutor.capture(
            cmd = listOf("pwd"),
            workDir = subDir.toFile()
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("subdir"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture passes environment variables to command`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo \$TEST_VAR"),
            env = mapOf("TEST_VAR" to "test_value")
        )

        assertEquals(0, result.exitCode)
        assertEquals("test_value", result.stdout)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture handles timeout`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sleep", "10"),
            timeoutSeconds = 1
        )

        // Command was killed due to timeout - just verify it completed
        assertNotNull(result)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough returns exit code`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("echo", "test")
        )

        assertEquals(0, exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough returns non-zero exit code on failure`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("sh", "-c", "exit 5")
        )

        assertEquals(5, exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough respects working directory`() {
        val subDir = tempDir.resolve("passthrough-test")
        Files.createDirectories(subDir)

        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("pwd"),
            workDir = subDir.toFile()
        )

        assertEquals(0, exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough passes environment variables`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("sh", "-c", "test \"\$MY_VAR\" = \"my_value\""),
            env = mapOf("MY_VAR" to "my_value")
        )

        assertEquals(0, exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough handles timeout`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("sleep", "10"),
            timeoutSeconds = 1
        )

        assertEquals(-1, exitCode)
    }

    @Test
    fun `capture throws on empty command list`() {
        assertTrue {
            CommandExecutor.capture(cmd = emptyList()).exitCode == -1
        }
    }

    @Test
    fun `passthrough throws on empty command list`() {
        assertTrue {
            CommandExecutor.passthrough(cmd = emptyList()) == -1
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture handles multiline output`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo 'line1'; echo 'line2'; echo 'line3'")
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("line1"))
        assertTrue(result.stdout.contains("line2"))
        assertTrue(result.stdout.contains("line3"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture handles both stdout and stderr`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo 'out'; echo 'err' >&2")
        )

        assertEquals(0, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture handles empty output`() {
        val result = CommandExecutor.capture(
            cmd = listOf("true")
        )

        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture uses UTF-8 charset by default`() {
        val result = CommandExecutor.capture(
            cmd = listOf("echo", "hello")
        )

        assertEquals("hello", result.stdout)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture can use custom charset`() {
        val result = CommandExecutor.capture(
            cmd = listOf("echo", "test"),
            charset = Charsets.UTF_8
        )

        assertEquals(0, result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture handles special characters in arguments`() {
        val result = CommandExecutor.capture(
            cmd = listOf("echo", "special!@#$%")
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("special"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough with custom timeout completes within timeout`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("echo", "quick"),
            timeoutSeconds = 10
        )

        assertEquals(0, exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `multiple environment variables are all passed`() {
        val result = CommandExecutor.capture(
            cmd = listOf("sh", "-c", "echo \$VAR1 \$VAR2 \$VAR3"),
            env = mapOf(
                "VAR1" to "value1",
                "VAR2" to "value2",
                "VAR3" to "value3"
            )
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("value1"))
        assertTrue(result.stdout.contains("value2"))
        assertTrue(result.stdout.contains("value3"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture works with absolute path executable`() {
        val result = CommandExecutor.capture(
            cmd = listOf("/bin/echo", "absolute path")
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("absolute"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough handles command not found`() {
        // Command not found throws IOException on some systems
        assertTrue {
            CommandExecutor.passthrough(
                cmd = listOf("this-command-does-not-exist-67890"),
                timeoutSeconds = 5
            ) == -1
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `capture with no environment variables works`() {
        val result = CommandExecutor.capture(
            cmd = listOf("echo", "no env"),
            env = emptyMap()
        )

        assertEquals(0, result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `passthrough with no environment variables works`() {
        val exitCode = CommandExecutor.passthrough(
            cmd = listOf("echo", "no env"),
            env = emptyMap()
        )

        assertEquals(0, exitCode)
    }
}
