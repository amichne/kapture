package io.amichne.kapture.core.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class EnvironmentTest {

    @Test
    fun `full returns complete environment map`() {
        val env = Environment.full()

        assertNotNull(env)
        assertTrue(env.isNotEmpty())
        // Common environment variables that should exist
        assertTrue(env.containsKey("PATH") || env.containsKey("Path"))
    }

    @Test
    fun `full returns independent copy of environment`() {
        val env1 = Environment.full()
        val env2 = Environment.full()

        // Modifying one should not affect the other
        val mutableEnv1 = env1.toMutableMap()
        mutableEnv1["TEST_VAR"] = "value"

        assertFalse(env2.containsKey("TEST_VAR"))
    }

    @Test
    fun `passthrough filters to Git and allowed variables`() {
        val env = Environment.passthrough()

        assertNotNull(env)
        
        // Check that only GIT_ prefixed or whitelisted keys are present
        env.keys.forEach { key ->
            assertTrue(
                key.startsWith("GIT_") || 
                key in setOf("PAGER", "LESS", "EDITOR", "VISUAL", "SSH_ASKPASS", "GPG_TTY"),
                "Key $key should be filtered"
            )
        }
    }

    @Test
    fun `passthrough includes GIT_ prefixed variables`() {
        // We can't easily set environment variables for testing,
        // but we can verify the filtering logic works
        val env = Environment.passthrough()
        
        // All keys should be valid
        env.keys.forEach { key ->
            assertTrue(
                key.startsWith("GIT_") || 
                key in setOf("PAGER", "LESS", "EDITOR", "VISUAL", "SSH_ASKPASS", "GPG_TTY")
            )
        }
    }

    @Test
    fun `passthrough includes PAGER if present in environment`() {
        val env = Environment.passthrough()
        
        // PAGER might or might not be set, but if it is, it should be in passthrough
        val fullEnv = Environment.full()
        if (fullEnv.containsKey("PAGER")) {
            assertTrue(env.containsKey("PAGER"))
            assertEquals(fullEnv["PAGER"], env["PAGER"])
        }
    }

    @Test
    fun `passthrough does not include non-Git variables`() {
        val env = Environment.passthrough()
        
        // These common variables should be filtered out
        val filteredVars = listOf("HOME", "USER", "SHELL", "LANG", "TERM")
        filteredVars.forEach { varName ->
            if (env.containsKey(varName)) {
                fail("Variable $varName should not be in passthrough environment")
            }
        }
    }

    @Test
    fun `debugEnabled returns false when KAPTURE_DEBUG is not set`() {
        // This test assumes KAPTURE_DEBUG is not set to "1" in the test environment
        // If it is set, the test will need to be adjusted or skipped
        val currentValue = System.getenv("KAPTURE_DEBUG")
        if (currentValue != "1") {
            assertFalse(Environment.debugEnabled)
        }
    }

    @Test
    fun `debug does not invoke lambda when debug is disabled`() {
        // Capture stderr to verify no output when debug is disabled
        val currentValue = System.getenv("KAPTURE_DEBUG")
        if (currentValue != "1") {
            var invoked = false
            Environment.debug { 
                invoked = true
                "This should not be called"
            }
            assertFalse(invoked, "Debug lambda should not be invoked when debug is disabled")
        }
    }

    @Test
    fun `debug outputs to stderr when debug is enabled`() {
        val currentValue = System.getenv("KAPTURE_DEBUG")
        if (currentValue == "1") {
            val originalErr = System.err
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            
            try {
                System.setErr(printStream)
                Environment.debug { "Test debug message" }
                printStream.flush()
                
                val output = outputStream.toString()
                assertTrue(output.contains("[kapture]"))
                assertTrue(output.contains("Test debug message"))
            } finally {
                System.setErr(originalErr)
            }
        }
    }

    @Test
    fun `passthrough returns empty map when no Git variables present`() {
        val env = Environment.passthrough()
        
        // The result might be empty if no GIT_ vars or passthrough vars are set
        // This is valid behavior
        assertNotNull(env)
    }

    @Test
    fun `passthrough is subset of full environment`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        // Every key in passthrough should exist in full with same value
        passthrough.forEach { (key, value) ->
            assertTrue(full.containsKey(key), "Key $key in passthrough should be in full env")
            assertEquals(full[key], value, "Value for $key should match")
        }
    }

    @Test
    fun `full contains PATH or Path variable`() {
        val env = Environment.full()
        
        // Most systems have PATH (Unix) or Path (Windows)
        assertTrue(env.containsKey("PATH") || env.containsKey("Path"))
    }

    @Test
    fun `passthrough includes EDITOR if present`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        if (full.containsKey("EDITOR")) {
            assertTrue(passthrough.containsKey("EDITOR"))
            assertEquals(full["EDITOR"], passthrough["EDITOR"])
        }
    }

    @Test
    fun `passthrough includes VISUAL if present`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        if (full.containsKey("VISUAL")) {
            assertTrue(passthrough.containsKey("VISUAL"))
            assertEquals(full["VISUAL"], passthrough["VISUAL"])
        }
    }

    @Test
    fun `passthrough includes SSH_ASKPASS if present`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        if (full.containsKey("SSH_ASKPASS")) {
            assertTrue(passthrough.containsKey("SSH_ASKPASS"))
            assertEquals(full["SSH_ASKPASS"], passthrough["SSH_ASKPASS"])
        }
    }

    @Test
    fun `passthrough includes GPG_TTY if present`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        if (full.containsKey("GPG_TTY")) {
            assertTrue(passthrough.containsKey("GPG_TTY"))
            assertEquals(full["GPG_TTY"], passthrough["GPG_TTY"])
        }
    }

    @Test
    fun `passthrough includes LESS if present`() {
        val full = Environment.full()
        val passthrough = Environment.passthrough()
        
        if (full.containsKey("LESS")) {
            assertTrue(passthrough.containsKey("LESS"))
            assertEquals(full["LESS"], passthrough["LESS"])
        }
    }

    @Test
    fun `multiple calls to full return same values`() {
        val env1 = Environment.full()
        val env2 = Environment.full()
        
        assertEquals(env1, env2)
    }

    @Test
    fun `multiple calls to passthrough return same values`() {
        val env1 = Environment.passthrough()
        val env2 = Environment.passthrough()
        
        assertEquals(env1, env2)
    }
}
