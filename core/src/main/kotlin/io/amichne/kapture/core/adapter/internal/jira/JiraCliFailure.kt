package io.amichne.kapture.core.adapter.internal.jira

internal enum class JiraCliFailure {
    FIELDS_MISSING,
    STATUS_MISSING,
    NAME_MISSING,
    EMPTY_STATUS,
    PARSE_ERROR
}
