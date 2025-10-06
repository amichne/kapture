package io.amichne.kapture.core.http

import io.amichne.kapture.core.config.TicketStatusResponse
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.util.Environment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

open class ExternalClient(
    private val baseUrl: String,
    apiKey: String?,
    client: HttpClient? = null,
    private val json: Json = defaultJson
) : AutoCloseable {
    private val ownsClient: Boolean
    protected val httpClient: HttpClient

    init {
        if (client != null) {
            httpClient = client
            ownsClient = false
        } else {
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) { json(json) }
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 10_000
                }
                defaultRequest {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    apiKey?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
            }
            ownsClient = true
        }
    }

    /**
     * Closes the underlying HTTP client when this instance created it, freeing
     * connection resources while remaining a no-op for injected clients.
     */
    override fun close() {
        if (ownsClient) {
            httpClient.close()
        }
    }

    /**
     * Looks up the remote ticket status for the supplied identifier and
     * returns a `TicketLookupResult` indicating success, absence, or error.
     * Any networking failures are logged via `Environment.debug` and
     * surfaced as `Error` to avoid crashing the CLI.
     */
    open fun getTicketStatus(ticketId: String): TicketLookupResult {
        if (ticketId.isBlank()) return TicketLookupResult.NotFound
        return runBlocking {
            try {
                val response = httpClient.get(url("/tickets/$ticketId/status"))
                if (response.status.value !in 200..299) {
                    return@runBlocking TicketLookupResult.NotFound
                }
                val body = response.body<TicketStatusResponse>()
                TicketLookupResult.Found(body.status)
            } catch (ex: ClientRequestException) {
                if (ex.response.status == HttpStatusCode.NotFound) {
                    TicketLookupResult.NotFound
                } else {
                    Environment.debug { "Ticket lookup failed with ${ex.response.status}" }
                    TicketLookupResult.Error("${ex.response.status}")
                }
            } catch (_: HttpRequestTimeoutException) {
                Environment.debug { "Ticket lookup timed out for $ticketId" }
                TicketLookupResult.Error("timeout")
            } catch (ex: ServerResponseException) {
                Environment.debug { "Ticket lookup error ${ex.response.status}" }
                TicketLookupResult.Error("${ex.response.status}")
            } catch (ex: Exception) {
                Environment.debug { "Ticket lookup exception: ${ex.message}" }
                TicketLookupResult.Error(ex.message ?: "unknown")
            }
        }
    }

    /**
     * Submits the provided session snapshot to the remote service, swallowing
     * and logging transient failures so that Git invocations are not blocked
     * by telemetry outages.
     */
    open fun trackSession(snapshot: SessionSnapshot) {
        runBlocking {
            try {
                httpClient.post(url("/sessions/track")) {
                    contentType(ContentType.Application.Json)
                    setBody(snapshot)
                }
            } catch (ex: Exception) {
                Environment.debug { "Session tracking failed: ${ex.message}" }
            }
        }
    }

    /**
     * Combines the configured base URL with a relative path while normalising
     * leading and trailing slashes so callers can compose endpoint routes
     * without worrying about separators.
     */
    protected fun url(path: String): String {
        val normalizedBase = baseUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedBase + normalizedPath
    }

    companion object {
        private val defaultJson = Json { ignoreUnknownKeys = true }
    }
}

sealed class TicketLookupResult {
    data class Found(val status: String) : TicketLookupResult()
    object NotFound : TicketLookupResult()
    data class Error(val message: String) : TicketLookupResult()
}
