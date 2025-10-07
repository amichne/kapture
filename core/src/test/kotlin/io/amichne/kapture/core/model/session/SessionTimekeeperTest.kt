package io.amichne.kapture.core.model.session

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionTimekeeperTest {

    @Test
    fun `withActivity updates lastActivityTime`() {
        val start = Instant.fromEpochMilliseconds(1000)
        val lastActivity = Instant.fromEpochMilliseconds(2000)
        val session = SessionTimekeeper(
            branch = "main",
            task = "PROJ-123",
            startTime = start,
            lastActivityTime = lastActivity
        )

        val newActivity = Instant.fromEpochMilliseconds(3000)
        val updated = session.withActivity(newActivity)

        assertEquals(start, updated.startTime)
        assertEquals(newActivity, updated.lastActivityTime)
        assertEquals("main", updated.branch)
        assertEquals("PROJ-123", updated.task)
    }

    @Test
    fun `withActivity preserves branch and task`() {
        val session = SessionTimekeeper(
            branch = "feature/test",
            task = "ABC-456",
            startTime = Instant.fromEpochMilliseconds(0),
            lastActivityTime = Instant.fromEpochMilliseconds(100)
        )

        val updated = session.withActivity(Instant.fromEpochMilliseconds(200))

        assertEquals("feature/test", updated.branch)
        assertEquals("ABC-456", updated.task)
    }

    @Test
    fun `withActivity handles null task`() {
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = Instant.fromEpochMilliseconds(0),
            lastActivityTime = Instant.fromEpochMilliseconds(100)
        )

        val updated = session.withActivity(Instant.fromEpochMilliseconds(200))

        assertNull(updated.task)
        assertEquals("main", updated.branch)
    }

    @Test
    fun `durationUntil calculates positive duration`() {
        val start = Instant.fromEpochMilliseconds(1000)
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = start,
            lastActivityTime = start
        )

        val end = Instant.fromEpochMilliseconds(5000)
        val duration = session.durationUntil(end)

        assertEquals(4000L, duration)
    }

    @Test
    fun `durationUntil returns zero when end equals start`() {
        val instant = Instant.fromEpochMilliseconds(1000)
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = instant,
            lastActivityTime = instant
        )

        val duration = session.durationUntil(instant)

        assertEquals(0L, duration)
    }

    @Test
    fun `durationUntil returns negative value for clock skew`() {
        val start = Instant.fromEpochMilliseconds(5000)
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = start,
            lastActivityTime = start
        )

        val end = Instant.fromEpochMilliseconds(3000)
        val duration = session.durationUntil(end)

        assertEquals(-2000L, duration)
    }

    @Test
    fun `durationUntil handles large time spans`() {
        val start = Instant.fromEpochMilliseconds(0)
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = start,
            lastActivityTime = start
        )

        val end = Instant.fromEpochMilliseconds(86_400_000L) // 24 hours
        val duration = session.durationUntil(end)

        assertEquals(86_400_000L, duration)
    }

    @Test
    fun `multiple withActivity calls create independent instances`() {
        val original = SessionTimekeeper(
            branch = "main",
            task = "PROJ-1",
            startTime = Instant.fromEpochMilliseconds(0),
            lastActivityTime = Instant.fromEpochMilliseconds(100)
        )

        val updated1 = original.withActivity(Instant.fromEpochMilliseconds(200))
        val updated2 = original.withActivity(Instant.fromEpochMilliseconds(300))

        // Original should be unchanged
        assertEquals(Instant.fromEpochMilliseconds(100), original.lastActivityTime)

        // Each update should be independent
        assertEquals(Instant.fromEpochMilliseconds(200), updated1.lastActivityTime)
        assertEquals(Instant.fromEpochMilliseconds(300), updated2.lastActivityTime)
    }

    @Test
    fun `withActivity can be chained`() {
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = Instant.fromEpochMilliseconds(0),
            lastActivityTime = Instant.fromEpochMilliseconds(100)
        )

        val final = session
            .withActivity(Instant.fromEpochMilliseconds(200))
            .withActivity(Instant.fromEpochMilliseconds(300))
            .withActivity(Instant.fromEpochMilliseconds(400))

        assertEquals(Instant.fromEpochMilliseconds(400), final.lastActivityTime)
        assertEquals(Instant.fromEpochMilliseconds(0), final.startTime)
    }

    @Test
    fun `session preserves all fields correctly`() {
        val branch = "feature/PROJ-999/add-feature"
        val task = "PROJ-999"
        val start = Instant.fromEpochMilliseconds(12345)
        val lastActivity = Instant.fromEpochMilliseconds(67890)

        val session = SessionTimekeeper(
            branch = branch,
            task = task,
            startTime = start,
            lastActivityTime = lastActivity
        )

        assertEquals(branch, session.branch)
        assertEquals(task, session.task)
        assertEquals(start, session.startTime)
        assertEquals(lastActivity, session.lastActivityTime)
    }

    @Test
    fun `durationUntil is independent of lastActivityTime`() {
        val start = Instant.fromEpochMilliseconds(1000)
        val session1 = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = start,
            lastActivityTime = Instant.fromEpochMilliseconds(2000)
        )
        val session2 = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = start,
            lastActivityTime = Instant.fromEpochMilliseconds(9000)
        )

        val end = Instant.fromEpochMilliseconds(5000)

        assertEquals(session1.durationUntil(end), session2.durationUntil(end))
    }

    @Test
    fun `withActivity accepts time before current lastActivityTime`() {
        val session = SessionTimekeeper(
            branch = "main",
            task = null,
            startTime = Instant.fromEpochMilliseconds(0),
            lastActivityTime = Instant.fromEpochMilliseconds(1000)
        )

        val updated = session.withActivity(Instant.fromEpochMilliseconds(500))

        assertEquals(Instant.fromEpochMilliseconds(500), updated.lastActivityTime)
    }
}
