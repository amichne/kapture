package io.amichne.kapture.core.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Delegates to the community Jira CLI tool. The executable is expected to
 * be available on the user's PATH unless overridden. Optional environment
 * variables can be supplied for authentication (PAT, email, site).
 */
@Serializable
@SerialName("cli")
sealed class Cli(
    open val executable: String,
    open val environment: Map<String, String>,
    open val timeoutSeconds: Long,
    open val provider: String
) : Plugin(Type.CLI) {
    companion object {
        fun Jira.toPlugin(
            executable: String = this.executable,
            environment: Map<String, String> = this.environment,
            timeoutSeconds: Long = this.timeoutSeconds
        ): Jira = Jira(
            executable = executable,
            environment = environment,
            timeoutSeconds = timeoutSeconds,
        )
    }

    data class Jira(
        override val executable: String = "jira-cli",
        override val environment: Map<String, String> = mapOf(),
        override val timeoutSeconds: Long = 60L,
    ) : Cli(executable, environment, timeoutSeconds, "jira")
}
