package io.amichne.kapture.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import io.amichne.kapture.core.config.ExternalIntegration

class ConfigTest {
    @Test
    fun `load uses explicit path when provided`() {
        val tempDir = Files.createTempDirectory("config-test")
        val configFile = tempDir.resolve("config.json")
        Files.writeString(
            configFile,
            """
                {
                  "external": {
                    "type": "rest",
                    "baseUrl": "https://api.example.com"
                  },
                  "enforcement": { "branchPolicy": "BLOCK", "statusCheck": "WARN" },
                  "sessionTrackingIntervalMs": 1000
                }
            """.trimIndent()
        )

        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
        try {
            val config = Config.load(configFile)
            val external = config.external as ExternalIntegration.Rest
            assertEquals("https://api.example.com", external.baseUrl)
            assertEquals(Config.Enforcement.Mode.BLOCK, config.enforcement.branchPolicy)
            assertEquals(1000, config.sessionTrackingIntervalMs)
            assertTrue(Files.exists(Path.of(config.localStateRoot)))
        } finally {
            System.setProperty("user.home", originalHome)
        }
    }

    @Test
    fun `load falls back to defaults when file missing`() {
        val tempDir = Files.createTempDirectory("config-defaults")
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
        try {
            val config = Config.load(tempDir.resolve("missing.json"))
            val external = config.external as ExternalIntegration.Rest
            assertEquals("http://localhost:8080", external.baseUrl)
            assertTrue(config.trackingEnabled)
        } finally {
            System.setProperty("user.home", originalHome)
        }
    }
}
