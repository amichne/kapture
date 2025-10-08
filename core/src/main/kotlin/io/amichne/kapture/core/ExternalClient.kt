package io.amichne.kapture.core

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.adapter.internal.http.HttpAdapter
import io.amichne.kapture.core.adapter.internal.jira.JiraCliAdapter
import io.amichne.kapture.core.model.config.Cli
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.config.TicketMapping
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.normalization.StatusNormalizer
import io.amichne.kapture.core.util.JsonProvider
import kotlinx.serialization.json.Json

open class ExternalClient<A : Adapter> protected constructor(
    val adapter: A,
    private val statusNormalizer: StatusNormalizer = StatusNormalizer.identity()
) : AutoCloseable {
    override fun close() {
        adapter.close()
    }

    /**
     * Looks up the task status using the active integration backend. The
     * default implementation delegates to the configured adapter but can be
     * overridden in tests.
     */
    open fun getTaskStatus(taskId: String): TaskSearchResult = when (val result = adapter.getTaskStatus(taskId)) {
        is TaskSearchResult.Found -> TaskSearchResult.Found(statusNormalizer.toInternal(result.status))
        TaskSearchResult.NotFound -> result
        is TaskSearchResult.Error -> result
    }

    /**
     * Forwards session tracking to the backend, allowing individual adapters to
     * opt-in or no-op depending on available capabilities.
     */
    open fun trackSession(snapshot: SessionSnapshot) {
        adapter.trackSession(snapshot)
    }

    companion object {
        fun <A : Adapter> wrap(
            adapter: A,
            normalizer: StatusNormalizer = StatusNormalizer.identity()
        ): ExternalClient<A> = object : ExternalClient<A>(adapter, normalizer) {}

        fun from(
            config: Config,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> = from(
            integration = config.external,
            ticketMapping = config.ticketMapping,
            json = json
        )

        fun from(
            integration: Plugin,
            ticketMapping: TicketMapping? = null,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> {
            val normalizer = StatusNormalizer(ticketMapping)
            val adapter = when (integration) {
                is Plugin.Http -> HttpAdapter(integration, json)
                is Cli -> JiraCliAdapter(integration, json)
            }
            return wrap(adapter, normalizer)
        }
    }
}
