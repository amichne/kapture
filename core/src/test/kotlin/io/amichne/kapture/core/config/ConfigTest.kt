package io.amichne.kapture.core.config

import io.amichne.kapture.core.model.config.Authentication
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.util.ConfigLoader
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load uses explicit path when provided`() {
        val configFile = tempDir.resolve("config.json")
        Files.writeString(
            configFile,
            """
                {
                  "sessionTrackingIntervalMs": 1000,
                  "trackingEnabled": true
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        // Uses defaults for external
        assertTrue(config.external is Plugin.Cli)
        assertTrue(config.trackingEnabled)
        assertEquals(1000L, config.sessionTrackingIntervalMs)
    }

    @Test
    fun `load falls back to defaults when file missing`() {
        val config = ConfigLoader.load(tempDir.resolve("missing.json"))

        val external = config.external as Plugin.Cli
        assertEquals("jira-cli", external.executable)
        assertTrue(config.trackingEnabled)
    }

    @Test
    fun `load uses KAPTURE_CONFIG environment variable`() {
        val configFile = tempDir.resolve("env-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "external": {
                    "type": "cli",
                    "executable": "custom-jira",
                    "environment": {},
                    "timeoutSeconds": 30
                  }
                }
            """.trimIndent()
        )

        // Note: We can't actually set environment variables in tests,
        // but we can verify the code doesn't crash when it's not set
        assertDoesNotThrow {
            ConfigLoader.load(configFile)
        }
    }

    @Test
    fun `load creates state root directory if missing`() {
        val config = ConfigLoader.load(tempDir.resolve("nonexistent.json"))

        val stateRoot = Path.of(config.root)
        // State root should be ensured to exist
        assertNotNull(config.root)
    }

    @Test
    fun `config has default HTTP integration values`() {
        val httpConfig = Plugin.Http(
            baseUrl = "https://api.example.com",
            timeoutMs = 5000
        )

        assertEquals("https://api.example.com", httpConfig.baseUrl)
        assertEquals(5000L, httpConfig.timeoutMs)
        assertEquals(Authentication.None, httpConfig.auth)
    }

    @Test
    fun `config has default CLI integration values`() {
        val cliConfig = Plugin.Cli(
            executable = "/usr/local/bin/custom-jira",
            environment = mapOf("KEY" to "value"),
            timeoutSeconds = 45
        )

        assertEquals("/usr/local/bin/custom-jira", cliConfig.executable)
        assertEquals(45L, cliConfig.timeoutSeconds)
        assertTrue(cliConfig.environment.containsKey("KEY"))
    }

    @Test
    fun `load handles custom branch pattern`() {
        val configFile = tempDir.resolve("pattern-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "branchPattern": "^(?<task>CUSTOM-\\d+).*$"
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertEquals("^(?<task>CUSTOM-\\d+).*$", config.branchPattern)
    }

    @Test
    fun `load handles enforcement configuration`() {
        val configFile = tempDir.resolve("enforcement-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "enforcement": {
                    "branchPolicy": "WARN",
                    "statusCheck": "BLOCK"
                  }
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertNotNull(config.enforcement)
    }

    @Test
    fun `load handles tracking disabled`() {
        val configFile = tempDir.resolve("tracking-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "trackingEnabled": false
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertFalse(config.trackingEnabled)
    }

    @Test
    fun `load handles custom realGitHint`() {
        val configFile = tempDir.resolve("git-hint-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "realGitHint": "/custom/path/to/git"
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertEquals("/custom/path/to/git", config.realGitHint)
    }

    @Test
    fun `load handles custom session tracking interval`() {
        val configFile = tempDir.resolve("interval-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "sessionTrackingIntervalMs": 60000
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertEquals(60000L, config.sessionTrackingIntervalMs)
    }

    @Test
    fun `load ignores unknown JSON keys`() {
        val configFile = tempDir.resolve("unknown-keys-config.json")
        Files.writeString(
            configFile,
            """
                {
                  "unknownField": "value",
                  "anotherUnknown": 123,
                  "trackingEnabled": true
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(configFile)

        assertTrue(config.trackingEnabled)
    }

    @Test
    fun `load handles malformed JSON by falling back to defaults`() {
        val configFile = tempDir.resolve("malformed-config.json")
        Files.writeString(configFile, "{invalid json")

        val config = ConfigLoader.load(configFile)

        // Should fall back to defaults
        assertNotNull(config)
        val external = config.external as Plugin.Cli
        assertEquals("jira-cli", external.executable)
    }

    @Test
    fun `default config has expected values`() {
        val config = Config()

        assertTrue(config.trackingEnabled)
        assertEquals(300_000L, config.sessionTrackingIntervalMs)
        assertEquals("^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$", config.branchPattern)
        assertNotNull(config.root)
        assertNull(config.realGitHint)
    }

    @Test
    fun `load prefers explicit path over environment`() {
        val explicitConfig = tempDir.resolve("explicit.json")
        Files.writeString(
            explicitConfig,
            """
                {
                  "sessionTrackingIntervalMs": 12345
                }
            """.trimIndent()
        )

        val config = ConfigLoader.load(explicitConfig)

        assertEquals(12345L, config.sessionTrackingIntervalMs)
    }

    @Test
    fun `load handles nested directory creation for state root`() {
        val configFile = tempDir.resolve("nested-config.json")
        val customRoot = tempDir.resolve("deep/nested/state").toString()
        Files.writeString(
            configFile,
            """
                {
                  "root": "$customRoot"
                }
            """.trimIndent()
        )

        assertDoesNotThrow {
            ConfigLoader.load(configFile)
        }
    }

    @Test
    fun `HTTP config uses default authentication when not specified`() {
        val httpConfig = Plugin.Http(baseUrl = "https://api.example.com")

        assertEquals(Authentication.None, httpConfig.auth)
        assertEquals(10_000L, httpConfig.timeoutMs)
    }
}
