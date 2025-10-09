package io.amichne.kapture.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val plugins: Map<String, Plugin> = emptyMap(),
    val timeoutMs: Int = 60_000,
    val branchPattern: String = "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val ticketMapping: TicketMapping? = null,
    val immediateRules: ImmediateRules = ImmediateRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null,
    val sessionTrackingIntervalMs: Long = 300_000,
    val root: String = System.getenv("KAPTURE_ROOT")
                       ?: "${System.getProperty("user.home")}/.kapture"
) {
    fun plugin(key: String): Plugin? = plugins[key]

    fun requirePlugin(key: String): Plugin =
        plugin(key) ?: error("No plugin configured for key '$key'")

    inline fun <reified T : Plugin> requirePluginOfType(key: String): T =
        when (val plugin = plugin(key)) {
            null -> error("No plugin configured for key '$key'")
            is T -> plugin
            else -> error(
                "Plugin '$key' is a ${plugin::class.simpleName}, expected ${T::class.simpleName}"
            )
        }
}
