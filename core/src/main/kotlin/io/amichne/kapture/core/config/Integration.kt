package io.amichne.kapture.core.config

import io.amichne.kapture.core.adapter.internal.jira.JiraCliDefaults
import io.amichne.kapture.core.model.config.Plugin

sealed class Integration<T : Plugin>(val connection: T) {
    object Jira : Integration<Plugin.Cli>(Plugin.Cli(JiraCliDefaults.EXECUTABLE, System.getenv(), 60L))
}
