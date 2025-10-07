package io.amichne.kapture.core

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.adapter.internal.http.HttpAdapter
import io.amichne.kapture.core.adapter.internal.jira.JiraCliAdapter
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.util.JsonProvider
import kotlinx.serialization.json.Json

open class ExternalClient<A : Adapter> protected constructor(
    val adapter: A
) : AutoCloseable {
    override fun close() {
        adapter.close()
    }

    /**
     * Looks up the task status using the active integration backend. The
     * default implementation delegates to the configured adapter but can be
     * overridden in tests.
     */
    open fun getTaskStatus(taskId: String): TaskSearchResult = adapter.getTaskStatus(taskId)

    /**
     * Forwards session tracking to the backend, allowing individual adapters to
     * opt-in or no-op depending on available capabilities.
     */
    open fun trackSession(snapshot: SessionSnapshot) {
        adapter.trackSession(snapshot)
    }

    companion object {
        fun <A : Adapter> wrap(adapter: A): ExternalClient<A> = object : ExternalClient<A>(adapter) {}

        fun from(
            integration: Plugin,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> = when (integration) {
            is Plugin.Http -> wrap(HttpAdapter(integration, json))
            is Plugin.Cli -> wrap(JiraCliAdapter(integration, json))
        }
    }
}
