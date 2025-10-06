package io.amichne.kapture.interceptors.session

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.model.Invocation
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.model.TimeSession
import io.amichne.kapture.interceptors.support.GitTestRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class SessionTrackingInterceptorTest {
    private val json = Json { encodeDefaults = true }
    private lateinit var repo: GitTestRepository

    @BeforeEach
    fun setUpRepo() {
        repo = GitTestRepository.create()
        repo.createFile("README.md", "docs")
        repo.commit("bootstrap")
        repo.checkoutBranch("PROJ-9/feature", create = true)
    }

    @AfterEach
    fun tearDownRepo() {
        repo.close()
    }

    @Test
    fun `starts and rotates session when timeout elapses`() {
        val tempDir = Files.createTempDirectory("session-test").toFile()
        val config = Config(localStateRoot = tempDir.absolutePath)
        val clock = MutableClock(Instant.parse("2024-01-01T00:00:00Z"))
        val snapshots = mutableListOf<SessionSnapshot>()
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTicketStatus(ticketId: String): TicketLookupResult = TicketLookupResult.Found("IN_PROGRESS")
            override fun trackSession(snapshot: SessionSnapshot) {
                snapshots += snapshot
            }
            override fun close() {}
        })
        val invocation = Invocation(
            listOf("status"),
            repo.gitPath(),
            repo.root.toFile(),
            repo.environment()
        )
        val interceptor = SessionTrackingInterceptor(clock = clock, json = json)

        interceptor.after(invocation, 0, config, client)
        val sessionFile = File(tempDir, "session.json")
        assertTrue(sessionFile.exists())
        val firstSession = json.decodeFromString<TimeSession>(sessionFile.readText())
        assertEquals("PROJ-9/feature", firstSession.branch)

        clock.advance(1.minutes)
        interceptor.after(invocation, 0, config, client)
        val updatedSession = json.decodeFromString<TimeSession>(sessionFile.readText())
        assertEquals("PROJ-9/feature", updatedSession.branch)

        clock.advance(config.sessionTrackingIntervalMs.milliseconds + 60_000.milliseconds)
        interceptor.after(invocation, 0, config, client)

        assertEquals(1, snapshots.size)
        val snapshot = snapshots.first()
        assertEquals("PROJ-9/feature", snapshot.branch)
        assertTrue(snapshot.durationMs >= config.sessionTrackingIntervalMs)
        assertNotNull(json.decodeFromString<TimeSession>(sessionFile.readText()))
    }

    @Test
    fun `closes session when branch changes`() {
        val tempDir = Files.createTempDirectory("session-branch-test").toFile()
        val config = Config(localStateRoot = tempDir.absolutePath)
        val clock = MutableClock(Instant.parse("2024-01-02T00:00:00Z"))
        val snapshots = mutableListOf<SessionSnapshot>()
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTicketStatus(ticketId: String): TicketLookupResult = TicketLookupResult.Found("IN_PROGRESS")
            override fun trackSession(snapshot: SessionSnapshot) { snapshots += snapshot }
            override fun close() {}
        })
        val interceptor = SessionTrackingInterceptor(clock = clock, json = json)

        val firstInvocation = Invocation(listOf("status"), repo.gitPath(), repo.root.toFile(), repo.environment())
        interceptor.after(firstInvocation, 0, config, client)

        repo.checkoutBranch("PROJ-10/fix", create = true)
        val secondInvocation = Invocation(listOf("status"), repo.gitPath(), repo.root.toFile(), repo.environment())
        clock.advance(5.minutes)
        interceptor.after(secondInvocation, 0, config, client)

        val sessionFile = File(tempDir, "session.json")
        val activeSession = json.decodeFromString<TimeSession>(sessionFile.readText())
        assertEquals("PROJ-10/fix", activeSession.branch)
        assertEquals(1, snapshots.size)
        assertEquals("PROJ-9/feature", snapshots.first().branch)
    }

    private class MutableClock(var instant: Instant) : kotlinx.datetime.Clock {
        override fun now(): Instant = instant
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }
}
