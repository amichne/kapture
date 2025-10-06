package io.amichne.kapture.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class Enforcement(
    val branchPolicy: Mode = Mode.WARN,
    val statusCheck: Mode = Mode.WARN
) {
    @Serializable
    enum class Mode { WARN, BLOCK, OFF }
}
