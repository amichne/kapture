package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.config.TicketStatusResponse
import io.amichne.kapture.core.http.JsonProvider.defaultJson
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.http.auth.RequestAuthenticator
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

class RestAdapter(
    private val integration: ExternalIntegration.Rest,
    private val json: Json = defaultJson,
) : Adapter {
    private val authenticator = RequestAuthenticator.from(integration.auth)
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
    }

    override fun close() {
        httpClient.close()
    }

    override fun getTicketStatus(ticketId: String): TicketLookupResult {
        if (ticketId.isBlank()) return TicketLookupResult.NotFound
        return runBlocking {
            try {
                val response = httpClient.get(url("/tickets/$ticketId/status")) {
                    authenticator.apply(this)
                }
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

    override fun trackSession(snapshot: SessionSnapshot) {
        runBlocking {
            try {
                httpClient.post(url("/sessions/track")) {
                    authenticator.apply(this)
                    contentType(ContentType.Application.Json)
                    setBody(snapshot)
                }
            } catch (ex: Exception) {
                Environment.debug { "Session tracking failed: ${ex.message}" }
            }
        }
    }

    private fun url(path: String): String {
        val normalizedBase = integration.baseUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedBase + normalizedPath
    }
}
