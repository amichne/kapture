package io.amichne.kapture.core

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.adapter.internal.http.HttpAdapter
import io.amichne.kapture.core.adapter.internal.jira.JiraCliAdapter
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.config.TicketMapping
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.model.task.SubtaskCreationResult
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
            pluginKey: String,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> =
            from(config, listOf(pluginKey), json)

        fun from(
            config: Config,
            pluginKeys: Collection<String>,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> {
            require(pluginKeys.isNotEmpty()) { "At least one plugin key must be provided" }
            val entries = pluginKeys.map { key -> key to config.requirePlugin(key) }
            return fromPlugins(entries, config.timeoutMs, config.ticketMapping, json)
        }

        fun from(
            config: Config,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> {
            require(config.plugins.isNotEmpty()) { "No plugins configured" }
            return fromPlugins(config.plugins.toList(), config.timeoutMs, config.ticketMapping, json)
        }

        fun from(
            plugin: Plugin,
            defaultTimeoutMs: Int = 60_000,
            ticketMapping: TicketMapping? = null,
            json: Json = JsonProvider.defaultJson
        ): ExternalClient<out Adapter> =
            fromPlugins(listOf(null to plugin), defaultTimeoutMs, ticketMapping, json)

        private fun fromPlugins(
            pluginEntries: List<Pair<String?, Plugin>>,
            defaultTimeoutMs: Int,
            ticketMapping: TicketMapping?,
            json: Json
        ): ExternalClient<out Adapter> {
            require(pluginEntries.isNotEmpty()) { "No plugins provided" }
            val adapters = pluginEntries.map { (_, plugin) -> adapterFor(plugin, defaultTimeoutMs, json) }
            val aggregateAdapter = when (adapters.size) {
                1 -> adapters.first()
                else -> CompositeAdapter(adapters)
            }
            val normalizer = StatusNormalizer(ticketMapping)
            return wrap(aggregateAdapter, normalizer)
        }

        private fun adapterFor(
            plugin: Plugin,
            defaultTimeoutMs: Int,
            json: Json
        ): Adapter = when (plugin) {
            is Plugin.Rest -> HttpAdapter(plugin, json, defaultTimeoutMs)
            is Plugin.Cli -> JiraCliAdapter(plugin, json, defaultTimeoutMs)
        }
    }
}

internal class CompositeAdapter(
    private val delegates: List<Adapter>
) : Adapter {
    init {
        require(delegates.isNotEmpty()) { "Delegates must not be empty" }
    }

    override fun close() {
        delegates.forEach { runCatching { it.close() } }
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        var lastError: TaskSearchResult.Error? = null
        for (delegate in delegates) {
            when (val result = delegate.getTaskStatus(taskId)) {
                is TaskSearchResult.Found -> return result
                TaskSearchResult.NotFound -> continue
                is TaskSearchResult.Error -> lastError = result
            }
        }
        return lastError ?: TaskSearchResult.NotFound
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        delegates.forEach { delegate ->
            runCatching { delegate.trackSession(snapshot) }
        }
    }

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult {
        var lastFailure: SubtaskCreationResult.Failure? = null
        for (delegate in delegates) {
            when (val result = delegate.createSubtask(parentId, title)) {
                is SubtaskCreationResult.Success -> return result
                is SubtaskCreationResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: SubtaskCreationResult.Failure("All plugins failed to create subtask")
    }

    override fun transitionTask(taskId: String, targetStatus: String): TaskTransitionResult {
        var lastFailure: TaskTransitionResult.Failure? = null
        for (delegate in delegates) {
            when (val result = delegate.transitionTask(taskId, targetStatus)) {
                is TaskTransitionResult.Success -> return result
                is TaskTransitionResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: TaskTransitionResult.Failure("All plugins failed to transition task")
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult {
        var lastFailure: TaskDetailsResult.Failure? = null
        for (delegate in delegates) {
            when (val result = delegate.getTaskDetails(taskId)) {
                is TaskDetailsResult.Success -> return result
                is TaskDetailsResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: TaskDetailsResult.Failure("All plugins failed to fetch task details")
    }
}
