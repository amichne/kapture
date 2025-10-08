package io.amichne.kapture.core.model.config

import io.amichne.kapture.core.model.task.InternalStatus
import kotlinx.serialization.Serializable

/**
 * Defines how raw provider specific states map into the internal status space.
 */
@Serializable
data class TicketMapping(
    val default: InternalStatus? = InternalStatus.TODO,
    val providers: List<ProviderMapping> = emptyList()
) {
    @Serializable
    data class ProviderMapping(
        val provider: String,
        val rules: List<Rule> = emptyList()
    )

    @Serializable
    data class Rule(
        val to: InternalStatus,
        val match: List<String> = emptyList(),
        val regex: Boolean = false,
        val caseInsensitive: Boolean = true
    )
}
