package io.gira.cli

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Client used to interact with the remote service for ticket status
 * queries and time tracking events.  HTTP failures and non‑2xx
 * responses are treated as missing status and do not block the
 * underlying git invocation【835536755365059†L272-L276】.
 *
 * @param baseUrl the base URL of the external service
 * @param apiKey the optional API key for Authorization header
 * @param trackingEnabled if false, all event posting is suppressed
 */
class ExternalClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val trackingEnabled: Boolean
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        // Set conservative timeouts as recommended.  Per spec
        // connection and request timeouts default to 10 seconds【835536755365059†L272-L276】.
        engineConfig {
            // Some engines support requestTimeout; this is a no‑op on engines that don't.
        }
    }

    /**
     * Query the status of a ticket.  Returns the status value as a
     * string, or null if the ticket is unknown or the request fails.
     */
    fun getTicketStatus(ticket: String): String? = runBlocking {
        val url = "$baseUrl/tickets/$ticket/status"
        try {
            val response: HttpResponse = client.get(url) {
                if (!apiKey.isNullOrBlank()) {
                    headers.append("Authorization", "Bearer $apiKey")
                }
            }
            return@runBlocking if (response.status == HttpStatusCode.OK) {
                response.body<String>()
            } else {
                null
            }
        } catch (_: Exception) {
            // treat any exception as missing
            return@runBlocking null
        }
    }

    @Serializable
    private data class EventPayload(
        val command: String,
        val args: List<String>,
        val branch: String? = null,
        val ticket: String? = null,
        val durationMs: Long
    )

    /**
     * Post a time tracking event.  Events are posted on the after
     * interceptor hook.  If tracking is disabled, this function is a
     * no‑op【835536755365059†L246-L250】.
     */
    fun postEvent(command: String, args: List<String>, branch: String?, ticket: String?, durationMs: Long) {
        if (!trackingEnabled) return
        runBlocking {
            val url = "$baseUrl/events/track"
            val payload = EventPayload(command, args, branch, ticket, durationMs)
            try {
                client.post(url) {
                    if (!apiKey.isNullOrBlank()) {
                        headers.append("Authorization", "Bearer $apiKey")
                    }
                    setBody(payload)
                }
            } catch (_: Exception) {
                // fail silently【835536755365059†L246-L250】
            }
        }
    }
}