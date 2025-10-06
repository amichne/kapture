package io.amichne.kapture.core.config

import io.amichne.kapture.core.adapter.internal.jira.JiraCliDefaults
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Enforcement
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.util.ConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

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
            val config = ConfigLoader.load(configFile)
            val external = config.external as Plugin.Cli
            assertEquals("jira-cli", external.executable)
            assertTrue(config.trackingEnabled)
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
            val config = ConfigLoader.load(tempDir.resolve("missing.json"))
            val external = config.external as Plugin.Cli
            assertEquals("jira-cli", external.executable)
            assertTrue(config.trackingEnabled)
        } finally {
            System.setProperty("user.home", originalHome)
        }
    }
}
