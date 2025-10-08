package io.amichne.kapture.cli

import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.ImmediateRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImmediateRulesEvaluatorTest {

    @Test
    fun `strips opt-out flags from arguments`() {
        val config = Config()
        val env = emptyMap<String, String>()

        val evaluation = evaluateImmediateRules(listOf("-nk", "status"), config, env)

        assertEquals(listOf("status"), evaluation.args)
        assertTrue(evaluation.optedOut)
    }

    @Test
    fun `detects opt-out env vars`() {
        val config = Config()
        val env = mapOf("KAPTURE_OPTOUT" to "1")

        val evaluation = evaluateImmediateRules(listOf("status"), config, env)

        assertTrue(evaluation.optedOut)
    }

    @Test
    fun `detects bypass commands`() {
        val immediate = ImmediateRules(bypassCommands = setOf("help"))
        val config = Config(immediateRules = immediate)

        val evaluation = evaluateImmediateRules(listOf("help"), config, emptyMap())

        assertTrue(evaluation.bypass)
    }

    @Test
    fun `detects bypass arguments`() {
        val config = Config()

        val evaluation = evaluateImmediateRules(listOf("status", "--list-cmds=main"), config, emptyMap())

        assertTrue(evaluation.bypass)
    }
}
