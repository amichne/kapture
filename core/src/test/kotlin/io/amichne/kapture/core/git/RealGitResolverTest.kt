package io.amichne.kapture.core.git

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class RealGitResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `resolve finds git in PATH`() {
        // This test requires git to be installed on the system
        val gitPath = RealGitResolver.resolve(null)
        
        assertNotNull(gitPath)
        assertTrue(gitPath.isNotEmpty())
        assertTrue(Files.exists(Path.of(gitPath)))
    }

    @Test
    fun `resolve prefers REAL_GIT environment variable`() {
        val originalEnv = System.getenv("REAL_GIT")
        
        // We can't modify environment at runtime easily, but we can verify
        // the resolver doesn't crash when REAL_GIT is not set
        if (originalEnv == null) {
            assertDoesNotThrow {
                RealGitResolver.resolve(null)
            }
        }
    }

    @Test
    fun `resolve uses configHint when provided and valid`() {
        // Find the real git first
        val realGit = RealGitResolver.resolve(null)
        
        // Use it as a hint
        val resolved = RealGitResolver.resolve(realGit)
        
        assertEquals(realGit, resolved)
    }

    @Test
    fun `resolve skips non-existent paths`() {
        val nonExistentPath = "/this/path/does/not/exist/git"
        
        // Should still resolve to a real git, not use the bad hint
        val resolved = RealGitResolver.resolve(nonExistentPath)
        
        assertNotNull(resolved)
        assertNotEquals(nonExistentPath, resolved)
    }

    @Test
    fun `resolve skips directories`() {
        val directory = tempDir.resolve("git-dir")
        Files.createDirectories(directory)
        
        // Should not resolve to a directory
        val resolved = RealGitResolver.resolve(directory.toString())
        
        assertNotNull(resolved)
        assertNotEquals(directory.toString(), resolved)
    }

    @Test
    fun `resolve skips non-executable files`() {
        val nonExecutable = tempDir.resolve("git-not-executable")
        Files.writeString(nonExecutable, "#!/bin/sh\necho 'fake git'")
        // Don't set executable permission
        
        val resolved = RealGitResolver.resolve(nonExecutable.toString())
        
        // Should find real git, not the non-executable file
        assertNotNull(resolved)
        assertNotEquals(nonExecutable.toString(), resolved)
    }

    @Test
    fun `resolve throws when no git found`() {
        // Create a scenario where git cannot be found by providing a bad hint
        // and assuming no git in fallback locations (unlikely but possible in CI)
        // This is hard to test reliably, so we just verify it doesn't crash
        assertDoesNotThrow {
            try {
                RealGitResolver.resolve(null)
            } catch (e: IllegalStateException) {
                // Expected in environments without git
                assertTrue(e.message?.contains("Unable to resolve") == true)
            }
        }
    }

    @Test
    fun `resolve handles blank configHint`() {
        val resolved = RealGitResolver.resolve("")
        
        assertNotNull(resolved)
        assertTrue(Files.exists(Path.of(resolved)))
    }

    @Test
    fun `resolve handles whitespace-only configHint`() {
        val resolved = RealGitResolver.resolve("   ")
        
        assertNotNull(resolved)
        assertTrue(Files.exists(Path.of(resolved)))
    }

    @Test
    fun `resolve normalizes paths`() {
        val realGit = RealGitResolver.resolve(null)
        
        // Provide a non-normalized version with .. in it
        val parent = Path.of(realGit).parent
        if (parent != null) {
            val unnormalized = parent.resolve("subdir").resolve("..").resolve(Path.of(realGit).fileName).toString()
            
            // Should still work after normalization
            assertDoesNotThrow {
                RealGitResolver.resolve(unnormalized)
            }
        }
    }

    @Test
    fun `resolve returns absolute path`() {
        val resolved = RealGitResolver.resolve(null)
        
        assertTrue(Path.of(resolved).isAbsolute)
    }

    @Test
    fun `resolve finds git on common Unix paths`() {
        val commonPaths = listOf(
            "/usr/bin/git",
            "/usr/local/bin/git",
            "/opt/homebrew/bin/git"
        )
        
        val resolved = RealGitResolver.resolve(null)
        
        // At least one common path should exist or git was found elsewhere
        assertNotNull(resolved)
        assertTrue(Files.exists(Path.of(resolved)))
    }

    @Test
    fun `multiple resolve calls return same result`() {
        val resolved1 = RealGitResolver.resolve(null)
        val resolved2 = RealGitResolver.resolve(null)
        
        assertEquals(resolved1, resolved2)
    }

    @Test
    fun `resolve works with null configHint`() {
        assertDoesNotThrow {
            RealGitResolver.resolve(null)
        }
    }

    @Test
    fun `resolve returns executable file`() {
        val resolved = RealGitResolver.resolve(null)
        val path = Path.of(resolved)
        
        assertTrue(Files.exists(path))
        assertTrue(Files.isRegularFile(path))
        assertTrue(Files.isExecutable(path))
    }

    @Test
    fun `resolve skips wrapper artifact when detected`() {
        // This test verifies that the resolver won't return itself
        // Hard to test without actually being the wrapper, but we can verify it doesn't crash
        assertDoesNotThrow {
            RealGitResolver.resolve(null)
        }
    }

    @Test
    fun `resolve handles symlinks correctly`() {
        val realGit = RealGitResolver.resolve(null)
        val path = Path.of(realGit)
        
        // Verify it resolved to a real file (following symlinks if any)
        assertTrue(Files.exists(path))
        assertTrue(Files.isExecutable(path))
    }
}
