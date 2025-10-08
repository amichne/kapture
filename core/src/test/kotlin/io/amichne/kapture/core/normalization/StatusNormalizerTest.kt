package io.amichne.kapture.core.normalization

import io.amichne.kapture.core.model.config.TicketMapping
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StatusNormalizerTest {

    @Test
    fun `returns existing internal status when present`() {
        val status = TaskStatus(
            provider = "jira",
            key = "PROJ-1",
            raw = "In Progress",
            internal = InternalStatus.IN_PROGRESS
        )
        val normalizer = StatusNormalizer(null)

        val normalized = normalizer.toInternal(status)

        assertEquals(InternalStatus.IN_PROGRESS, normalized.internal)
    }

    @Test
    fun `uses provider mapping rule`() {
        val mapping = TicketMapping(
            providers = listOf(
                TicketMapping.ProviderMapping(
                    provider = "jira",
                    rules = listOf(
                        TicketMapping.Rule(
                            to = InternalStatus.REVIEW,
                            match = listOf("In Review")
                        )
                    )
                )
            )
        )
        val normalizer = StatusNormalizer(mapping)
        val status = TaskStatus(
            provider = "jira",
            key = "PROJ-2",
            raw = "In Review"
        )

        val normalized = normalizer.toInternal(status)

        assertEquals(InternalStatus.REVIEW, normalized.internal)
    }

    @Test
    fun `handles regex and case sensitivity`() {
        val mapping = TicketMapping(
            providers = listOf(
                TicketMapping.ProviderMapping(
                    provider = "github",
                    rules = listOf(
                        TicketMapping.Rule(
                            to = InternalStatus.DONE,
                            match = listOf("closed|merged"),
                            regex = true,
                            caseInsensitive = false
                        )
                    )
                )
            )
        )
        val normalizer = StatusNormalizer(mapping)
        val status = TaskStatus(
            provider = "GitHub",
            key = "123",
            raw = "closed"
        )

        val normalized = normalizer.toInternal(status)

        assertEquals(InternalStatus.DONE, normalized.internal)
    }

    @Test
    fun `falls back to default when no rules match`() {
        val mapping = TicketMapping(
            default = InternalStatus.TODO,
            providers = listOf(
                TicketMapping.ProviderMapping(
                    provider = "jira",
                    rules = emptyList()
                )
            )
        )
        val normalizer = StatusNormalizer(mapping)
        val status = TaskStatus(
            provider = "jira",
            key = "PROJ-3",
            raw = "Unknown"
        )

        val normalized = normalizer.toInternal(status)

        assertEquals(InternalStatus.TODO, normalized.internal)
    }

    @Test
    fun `infers internal status from raw string when mapping absent`() {
        val normalizer = StatusNormalizer(null)
        val status = TaskStatus(
            provider = "jira",
            key = "PROJ-4",
            raw = "Code Review"
        )

        val normalized = normalizer.toInternal(status)

        assertEquals(InternalStatus.REVIEW, normalized.internal)
    }

    @Test
    fun `returns null internal when nothing matches`() {
        val mapping = TicketMapping(default = null, providers = emptyList())
        val normalizer = StatusNormalizer(mapping)
        val status = TaskStatus(
            provider = "linear",
            key = "PROJ-5",
            raw = "mystery"
        )

        val normalized = normalizer.toInternal(status)

        assertNull(normalized.internal)
    }
}
