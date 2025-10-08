package io.amichne.kapture.core.model.config

import kotlinx.serialization.Serializable

/**
 * Configuration controlling always-on enforcement around normal git commands.
 */
@Serializable
data class ImmediateRules(
    val enabled: Boolean = true,
    val optOutFlags: Set<String> = setOf("-nk", "--no-kapture"),
    val optOutEnvVars: Set<String> = setOf("KAPTURE_OPTOUT", "GIRA_NO_KAPTURE"),
    val bypassCommands: Set<String> = setOf("help", "--version", "--exec-path"),
    val bypassArgsContains: Set<String> = setOf("--list-cmds")
)
