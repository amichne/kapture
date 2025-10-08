package io.amichne.kapture.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class StatusRules(
    val allowCommitWhen: Set<String> = setOf("TODO", "IN_PROGRESS"),
    val allowPushWhen: Set<String> = setOf("REVIEW", "DONE")
)
