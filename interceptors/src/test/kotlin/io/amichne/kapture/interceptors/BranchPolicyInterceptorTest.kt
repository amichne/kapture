package io.amichne.kapture.interceptors

import io.amichne.kapture.core.config.Config
import io.amichne.kapture.core.http.ExternalClient
import io.amichne.kapture.core.model.Invocation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BranchPolicyInterceptorTest {
    @Test
    fun `blocks branch creation when ticket missing and mode is BLOCK`() {
        val config = Config(
            enforcement = Config.Enforcement(
                branchPolicy = Config.Enforcement.Mode.BLOCK, statusCheck = Config.Enforcement.Mode.OFF
            )
        )
        val interceptor = BranchPolicyInterceptor()
        val invocation = TestInvocation(listOf("checkout", "-b", "feature/no-ticket"))
        val client = ExternalClient(
            baseUrl = "https://example.test",
            apiKey = null,
            client = HttpClient(MockEngine) {
                engine {
                    addHandler { respond("", HttpStatusCode.NotFound) }
                }
            }
        )

        val exitCode = interceptor.before(invocation, config, client)
        assertEquals(BranchPolicyInterceptor.BRANCH_POLICY_EXIT_CODE, exitCode)
    }

    private class TestInvocation(args: List<String>) : Invocation(args, "git", File("."), emptyMap())
}
