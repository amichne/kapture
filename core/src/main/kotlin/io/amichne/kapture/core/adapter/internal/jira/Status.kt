package io.amichne.kapture.core.adapter.internal.jira

@JvmInline
internal value class JiraStatus(val value: String) {
    val isBlank: Boolean get() = value.isBlank()
}
