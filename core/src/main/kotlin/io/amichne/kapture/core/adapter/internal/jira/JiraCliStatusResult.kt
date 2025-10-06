package io.amichne.kapture.core.adapter.internal.jira

internal sealed class JiraCliStatusResult {
    data class Success(val status: JiraStatus) : JiraCliStatusResult()
    data class Failure(
        val reason: JiraCliFailure,
        val detail: String
    ) : JiraCliStatusResult()
}
