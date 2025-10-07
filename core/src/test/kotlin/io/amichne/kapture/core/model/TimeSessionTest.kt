package io.amichne.kapture.core.model

import io.amichne.kapture.core.model.session.SessionTimekeeper
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeSessionTest {
    @Test
    fun `duration until computes millis`() {
        val start = Instant.parse("2024-01-01T00:00:00Z")
        val end = Instant.parse("2024-01-01T00:05:00Z")
        val session =
            SessionTimekeeper(branch = "feature", task = "PROJ-1", startTime = start, lastActivityTime = start)
        assertEquals(300_000, session.durationUntil(end))
    }
}
