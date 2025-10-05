package io.gira.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Enforcement modes for branch policy and status checks.  "WARN" will
 * emit a message on stderr but allow the git command to proceed,
 * "BLOCK" will exit with the defined exit code, and "OFF" will
 * disable the interceptor entirely【835536755365059†L204-L207】.
 */
@Serializable
enum class Mode { WARN, BLOCK, OFF }

/**
 * Configuration block controlling branch policy and status gate
 * enforcement.  The default values follow the specification: both
 * branchPolicy and statusCheck default to WARN【835536755365059†L204-L205】.
 */
@Serializable
data class Enforcement(
    val branchPolicy: Mode = Mode.WARN,
    val statusCheck: Mode = Mode.WARN
)

/**
 * Allowed ticket statuses for commit and push operations【835536755365059†L241-L245】.
 */
@Serializable
data class StatusRules(
    val allowCommitWhen: Set<String> = setOf("IN_PROGRESS", "READY"),
    val allowPushWhen: Set<String> = setOf("READY", "IN_REVIEW")
)

/**
 * Top level configuration for the wrapper.  All fields are optional
 * and have sensible defaults.  When loading configuration the file
 * specified by the GITWRAP_CONFIG environment variable takes
 * precedence over the home directory (~/.git-wrapper/config.json),
 * followed by defaults【835536755365059†L193-L199】.
 */
@Serializable
data class Config(
    val externalBaseUrl: String = "http://localhost:8080",
    val apiKey: String? = null,
    val branchPattern: String = "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null
) {
    companion object {
        /**
         * Load configuration from the environment or disk.  The lookup
         * order is specified by the design: first consult the
         * GITWRAP_CONFIG environment variable, then look in
         * ~/.git-wrapper/config.json, then fall back to defaults【835536755365059†L193-L199】.
         */
        fun load(): Config {
            // TODO: implement environment and file loading.  This
            // simplified implementation always returns the default
            // configuration.  A full implementation would consult the
            // GITWRAP_CONFIG environment variable and ~/.git-wrapper
            // according to the specification【835536755365059†L193-L199】.
            return Config()
        }
    }
}