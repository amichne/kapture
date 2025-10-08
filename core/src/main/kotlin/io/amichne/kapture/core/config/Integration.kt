package io.amichne.kapture.core.config

import io.amichne.kapture.core.adapter.internal.jira.JiraCliDefaults
import io.amichne.kapture.core.adapter.internal.jira.JiraCliExecutable
import io.amichne.kapture.core.model.config.Cli
import io.amichne.kapture.core.model.config.Plugin

sealed class Integration<T : Plugin>(val connection: T) {
    object Jira : Integration<Cli>(Cli.Jira(executable = JiraCliDefaults.EXECUTABLE, environment = System.getenv(), timeoutSeconds = 60L))
}
