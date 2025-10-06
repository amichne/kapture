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

    override fun close() {
        if (ownsClient) {
            httpClient.close()
        }
    }

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
