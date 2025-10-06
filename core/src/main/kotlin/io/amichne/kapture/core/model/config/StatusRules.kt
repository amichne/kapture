package io.amichne.kapture.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class StatusRules(
    val allowCommitWhen: Set<String> = setOf("IN_PROGRESS", "READY"),
    val allowPushWhen: Set<String> = setOf("READY", "IN_REVIEW")
)
