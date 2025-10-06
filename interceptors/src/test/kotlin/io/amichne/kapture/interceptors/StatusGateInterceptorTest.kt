package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.config.Config.Enforcement.Mode
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.model.Invocation
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.interceptors.support.GitTestRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatusGateInterceptorTest {
    private lateinit var repo: GitTestRepository

    @BeforeEach
    fun setUp() {
        repo = GitTestRepository.create()
        repo.createFile("README.md", "docs")
        repo.commit("Initial commit")
        repo.checkoutBranch("PROJ-7/feature", create = true)
    }

    @AfterEach
    fun tearDown() {
        repo.close()
    }

    @Test
    fun `blocks commit when ticket status not allowed`() {
        val config = Config(
            enforcement = Config.Enforcement(
                branchPolicy = Mode.OFF,
                statusCheck = Mode.BLOCK
            )
        )
        val interceptor = StatusGateInterceptor()
        val invocation = Invocation(
            listOf("commit"),
            repo.gitPath(),
            repo.root.toFile(),
            repo.environment()
        )
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTicketStatus(ticketId: String): TicketLookupResult = TicketLookupResult.Found("BLOCKED")
            override fun trackSession(snapshot: SessionSnapshot) {}
            override fun close() {}
        })

        val exitCode = interceptor.before(invocation, config, client)
        assertEquals(StatusGateInterceptor.COMMIT_EXIT, exitCode)
    }
}
