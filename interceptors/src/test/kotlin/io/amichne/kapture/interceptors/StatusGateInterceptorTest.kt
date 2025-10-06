package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.exec.ExecResult
import io.amichne.kapture.core.http.adapter.Adapter
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.Invocation
import io.amichne.kapture.core.model.SessionSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class StatusGateInterceptorTest {
    @Test
    fun `blocks commit when ticket status not allowed`() {
        val config = Config(
            enforcement = Config.Enforcement(
                branchPolicy = Config.Enforcement.Mode.OFF, statusCheck = Config.Enforcement.Mode.BLOCK
            )
        )
        val interceptor = StatusGateInterceptor()
        val invocation = object : Invocation(listOf("commit"), "git", File("."), emptyMap()) {
            override fun captureGit(vararg gitArgs: String): ExecResult = ExecResult(0, "PROJ-7/feature", "")
        }
        val client = ExternalClient.wrap(object : Adapter {
            override fun getTicketStatus(ticketId: String): TicketLookupResult = TicketLookupResult.Found("BLOCKED")
            override fun trackSession(snapshot: SessionSnapshot) {}
            override fun close() {}
        })

        val exitCode = interceptor.before(invocation, config, client)
        assertEquals(StatusGateInterceptor.COMMIT_EXIT, exitCode)
    }
}
