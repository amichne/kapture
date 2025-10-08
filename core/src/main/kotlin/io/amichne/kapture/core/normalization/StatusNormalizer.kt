package io.amichne.kapture.core.normalization

import io.amichne.kapture.core.model.config.TicketMapping
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskStatus

class StatusNormalizer(
    private val mapping: TicketMapping?
) {
    fun toInternal(status: TaskStatus): TaskStatus {
        val ticketMapping = mapping
        val providerRules = ticketMapping?.providers?.firstOrNull {
            it.provider.equals(status.provider, ignoreCase = true)
        }?.rules.orEmpty()

        val normalized = providerRules.firstNotNullOfOrNull { rule ->
            val matches = rule.match.any { candidate ->
                matches(candidate, status.raw.orEmpty(), rule.regex, rule.caseInsensitive)
            }
            if (matches) rule.to else null
        }

        val inferred = normalized
            ?: status.internal
            ?: ticketMapping?.default
            ?: inferFromRaw(status.raw)

        return status.copy(internal = inferred)
    }

    private fun matches(
        candidate: String,
        raw: String,
        regex: Boolean,
        caseInsensitive: Boolean
    ): Boolean {
        if (!regex) {
            return if (caseInsensitive) {
                candidate.equals(raw, ignoreCase = true)
            } else {
                candidate == raw
            }
        }

        val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(candidate, options).matchEntire(raw)?.let { true } ?: false
    }

    companion object {
        fun identity(): StatusNormalizer = StatusNormalizer(null)
    }

    private fun inferFromRaw(rawStatus: String?): InternalStatus? {
        if (rawStatus.isNullOrBlank()) return null
        val canonical = rawStatus.trim().replace("\\s+".toRegex(), "_").uppercase()
        return when {
            canonical.contains("BLOCK") -> InternalStatus.BLOCKED
            canonical.contains("REVIEW") -> InternalStatus.REVIEW
            canonical.contains("PROGRESS") -> InternalStatus.IN_PROGRESS
            canonical.contains("READY") || canonical.contains("TODO") || canonical.contains("OPEN") -> InternalStatus.TODO
            canonical.contains("DONE") || canonical.contains("CLOSE") || canonical.contains("RESOLVED") -> InternalStatus.DONE
            else -> null
        }
    }
}
