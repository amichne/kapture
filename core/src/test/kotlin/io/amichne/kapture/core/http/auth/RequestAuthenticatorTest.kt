package io.amichne.kapture.core.http.auth

import io.amichne.kapture.core.authenticator.internal.BasicAuthenticator
import io.amichne.kapture.core.authenticator.internal.BearerAuthenticator
import io.amichne.kapture.core.authenticator.internal.JiraPersonalAccessToken
import io.amichne.kapture.core.authenticator.internal.NoOpAuthenticator
import io.amichne.kapture.core.authenticator.RequestAuthenticator
import io.amichne.kapture.core.model.config.Authentication
import io.ktor.client.request.*
import io.ktor.http.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class RequestAuthenticatorTest {

    @Test
    fun `from creates NoOpAuthenticator for None auth`() {
        val authenticator = RequestAuthenticator.from(Authentication.None)

        val builder = HttpRequestBuilder()
        authenticator.apply(builder)

        assertNull(builder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `from creates BearerAuthenticator for Bearer auth`() {
        val authenticator = RequestAuthenticator.from(Authentication.Bearer("test-token"))

        val builder = HttpRequestBuilder()
        authenticator.apply(builder)

        assertEquals("Bearer test-token", builder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `from creates BasicAuthenticator for Basic auth`() {
        val authenticator = RequestAuthenticator.from(Authentication.Basic("user", "pass"))

        val builder = HttpRequestBuilder()
        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("Basic "))

        // Verify credentials
        val credentials = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        assertEquals("user:pass", credentials)
    }

    @Test
    fun `from creates JiraPatAuthenticator for JiraPat auth`() {
        val authenticator = RequestAuthenticator.from(
            Authentication.PersonalAccessToken("user@example.com", "api-token")
        )

        val builder = HttpRequestBuilder()
        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("Basic "))

        // Verify credentials match Jira PAT format (email:token)
        val credentials = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        assertEquals("user@example.com:api-token", credentials)
    }

    @Test
    fun `NoOpAuthenticator does not add headers`() {
        val builder = HttpRequestBuilder()
        builder.headers.append("Custom", "Header")

        NoOpAuthenticator.apply(builder)

        assertEquals(1, builder.headers.entries().size)
        assertEquals("Header", builder.headers["Custom"])
    }

    @Test
    fun `BearerAuthenticator adds correct header`() {
        val authenticator = BearerAuthenticator("my-secret-token")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        assertEquals("Bearer my-secret-token", builder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `BasicAuthenticator encodes credentials correctly`() {
        val authenticator = BasicAuthenticator("admin", "password123")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNotNull(authHeader)

        val expectedCredentials = "admin:password123"
        val expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.toByteArray())
        assertEquals("Basic $expectedEncoded", authHeader)
    }

    @Test
    fun `BasicAuthenticator handles special characters in credentials`() {
        val authenticator = BasicAuthenticator("user@example.com", "p@ss:word!")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNotNull(authHeader)

        val credentials = String(Base64.getDecoder().decode(authHeader!!.removePrefix("Basic ")))
        assertEquals("user@example.com:p@ss:word!", credentials)
    }

    @Test
    fun `JiraPatAuthenticator uses Basic auth with email and token`() {
        val authenticator = JiraPersonalAccessToken("jira.user@company.com", "ATATT3xFfGF0...")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("Basic "))

        val credentials = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        assertEquals("jira.user@company.com:ATATT3xFfGF0...", credentials)
    }

    @Test
    fun `authenticators can be reused for multiple requests`() {
        val authenticator = BearerAuthenticator("reusable-token")

        val builder1 = HttpRequestBuilder()
        val builder2 = HttpRequestBuilder()
        val builder3 = HttpRequestBuilder()

        authenticator.apply(builder1)
        authenticator.apply(builder2)
        authenticator.apply(builder3)

        assertEquals("Bearer reusable-token", builder1.headers[HttpHeaders.Authorization])
        assertEquals("Bearer reusable-token", builder2.headers[HttpHeaders.Authorization])
        assertEquals("Bearer reusable-token", builder3.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `authenticators override existing Authorization header`() {
        val authenticator = BearerAuthenticator("new-token")
        val builder = HttpRequestBuilder()
        builder.headers.append(HttpHeaders.Authorization, "Bearer old-token")

        authenticator.apply(builder)

        // Should have replaced the old token
        val authValues = builder.headers.getAll(HttpHeaders.Authorization)
        assertTrue(authValues?.any { it == "Bearer new-token" } == true)
    }

    @Test
    fun `empty credentials do not add auth header`() {
        val authenticator = BasicAuthenticator("", "")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNull(authHeader)
    }

    @Test
    fun `blank token does not add bearer header`() {
        val authenticator = BearerAuthenticator("   ")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNull(authHeader)
    }

    @Test
    fun `JiraPatAuthenticator with blank email does not add header`() {
        val authenticator = JiraPersonalAccessToken("", "token")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNull(authHeader)
    }

    @Test
    fun `JiraPatAuthenticator with blank token does not add header`() {
        val authenticator = JiraPersonalAccessToken("email@example.com", "")
        val builder = HttpRequestBuilder()

        authenticator.apply(builder)

        val authHeader = builder.headers[HttpHeaders.Authorization]
        assertNull(authHeader)
    }
}
