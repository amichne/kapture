package io.amichne.kapture.core.adapter.internal.http

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.authenticator.RequestAuthenticator
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskStatus
import io.amichne.kapture.core.model.task.TaskStatusResponse
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.core.util.JsonProvider
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

internal class HttpAdapter(
    private val integration: Plugin.Http,
    private val json: Json = JsonProvider.defaultJson,
) : Adapter {
    private val authenticator = RequestAuthenticator.Companion.from(integration.auth)
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation.Plugin) { json(json) }
        install(HttpTimeout.Plugin) {
            requestTimeoutMillis = integration.timeoutMs
            connectTimeoutMillis = integration.timeoutMs
        }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
    }

    override fun close() {
        httpClient.close()
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        if (taskId.isBlank()) return TaskSearchResult.NotFound
        return runBlocking {
            try {
                val response = httpClient.get(url("/tasks/$taskId/status")) {
                    authenticator.apply(this)
                }
                if (response.status.value !in 200..299) {
                    return@runBlocking TaskSearchResult.NotFound
                }
                val body = response.body<TaskStatusResponse>()
                TaskSearchResult.Found(
                    TaskStatus(
                        provider = integration.provider,
                        key = taskId,
                        raw = body.status
                    )
                )
            } catch (ex: ClientRequestException) {
                if (ex.response.status == HttpStatusCode.Companion.NotFound) {
                    TaskSearchResult.NotFound
                } else {
                    Environment.debug { "Task lookup failed with ${ex.response.status}" }
                    TaskSearchResult.Error("${ex.response.status}")
                }
            } catch (_: HttpRequestTimeoutException) {
                Environment.debug { "Task lookup timed out for $taskId" }
                TaskSearchResult.Error("timeout")
            } catch (ex: ServerResponseException) {
                Environment.debug { "Task lookup error ${ex.response.status}" }
                TaskSearchResult.Error("${ex.response.status}")
            } catch (ex: Exception) {
                Environment.debug { "Task lookup exception: ${ex.message}" }
                TaskSearchResult.Error(ex.message ?: "unknown")
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

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult {
        Environment.debug { "REST adapter does not fully support subtask creation; use jira-cli adapter" }
        return SubtaskCreationResult.Failure("Not implemented for REST adapter")
    }

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult {
        Environment.debug { "REST adapter does not fully support task transitions; use jira-cli adapter" }
        return TaskTransitionResult.Failure("Not implemented for REST adapter")
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult {
        Environment.debug { "REST adapter does not fully support task details retrieval; use jira-cli adapter" }
        return TaskDetailsResult.Failure("Not implemented for REST adapter")
    }

    private fun url(path: String): String {
        val normalizedBase = integration.baseUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedBase + normalizedPath
    }
}
