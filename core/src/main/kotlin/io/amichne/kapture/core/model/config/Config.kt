package io.amichne.kapture.core.model.config

import io.amichne.kapture.core.config.Integration
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val external: Plugin = Integration.Jira.connection,
    val branchPattern: String = "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null,
    val sessionTrackingIntervalMs: Long = 300_000,
    val root: String = System.getenv("KAPTURE_ROOT")
                       ?: "${System.getProperty("user.home")}/.kapture"
) {

}
