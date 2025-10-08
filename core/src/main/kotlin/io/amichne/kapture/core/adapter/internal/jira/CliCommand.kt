package io.amichne.kapture.core.adapter.internal.jira

internal enum class JiraCliCommand(val token: String) {
    TASK("task"),
    VIEW("view"),
    CREATE("create"),
    MOVE("move")
}
