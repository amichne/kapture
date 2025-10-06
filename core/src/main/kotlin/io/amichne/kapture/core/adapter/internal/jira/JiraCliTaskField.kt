package io.amichne.kapture.core.adapter.internal.jira

internal enum class JiraCliTaskField(val key: String) {
    FIELDS("fields"),
    STATUS("status"),
    NAME("name"),
    KEY("key"),
    SUMMARY("summary"),
    DESCRIPTION("description"),
    PARENT("parent")
}
