package io.amichne.kapture.core.http.adapter

import io.amichne.kapture.core.config.ExternalIntegration
import io.amichne.kapture.core.exec.Exec
import io.amichne.kapture.core.http.TicketLookupResult
import io.amichne.kapture.core.model.SessionSnapshot
import io.amichne.kapture.core.util.Environment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class JiraCliAdapter(
    private val integration: ExternalIntegration.JiraCli,
    private val json: Json
) : Adapter {
    override fun close() {
        // No persistent resources to release for CLI integration.
    }

    override fun getTicketStatus(ticketId: String): TicketLookupResult {
        val issueKey = JiraIssueKey(ticketId)
        if (issueKey.isBlank) return TicketLookupResult.NotFound

        val command = buildCommand(issueKey)
        val result = Exec.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "jira-cli returned ${result.exitCode} for ${issueKey.value}: ${result.stderr}"
            }
            return TicketLookupResult.Error("jira-cli exit ${result.exitCode}")
        }

        return when (val parsed = parseStatus(JiraCliOutput(result.stdout))) {
            is JiraCliStatusResult.Success -> TicketLookupResult.Found(parsed.status.value)
            is JiraCliStatusResult.Failure -> {
                Environment.debug {
                    "jira-cli status resolution failed (${parsed.reason}) for ${issueKey.value}: ${parsed.detail}"
                }
                when (parsed.reason) {
                    JiraCliFailure.FIELDS_MISSING,
                    JiraCliFailure.STATUS_MISSING,
                    JiraCliFailure.NAME_MISSING,
                    JiraCliFailure.EMPTY_STATUS -> TicketLookupResult.NotFound
                    JiraCliFailure.PARSE_ERROR -> TicketLookupResult.Error(parsed.detail.ifBlank { "parse error" })
                }
            }
        }
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        Environment.debug { "jira-cli integration does not support session tracking; skipping." }
    }

    private fun buildCommand(issueKey: JiraIssueKey): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        return listOf(
            executable.value,
            JiraCliCommand.ISSUE.token,
            JiraCliCommand.VIEW.token,
            issueKey.value,
            JiraCliOption.OUTPUT.flag,
            JiraCliOutputFormat.JSON.token
        )
    }

    private fun parseStatus(output: JiraCliOutput): JiraCliStatusResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val fields = root.jsonObject[JiraCliIssueField.FIELDS.key]?.jsonObject
            ?: return JiraCliStatusResult.Failure(JiraCliFailure.FIELDS_MISSING, output.value)
        val statusObj = fields[JiraCliIssueField.STATUS.key]?.jsonObject
            ?: return JiraCliStatusResult.Failure(JiraCliFailure.STATUS_MISSING, output.value)
        val name = statusObj[JiraCliIssueField.NAME.key]?.jsonPrimitive?.content
            ?: return JiraCliStatusResult.Failure(JiraCliFailure.NAME_MISSING, output.value)
        val status = JiraStatus(name)
        if (status.isBlank) {
            JiraCliStatusResult.Failure(JiraCliFailure.EMPTY_STATUS, output.value)
        } else {
            JiraCliStatusResult.Success(status)
        }
    } catch (throwable: Exception) {
        JiraCliStatusResult.Failure(JiraCliFailure.PARSE_ERROR, throwable.message ?: "")
    }
}

private enum class JiraCliCommand(val token: String) {
    ISSUE("issue"),
    VIEW("view")
}

private enum class JiraCliOption(val flag: String) {
    OUTPUT("--output")
}

private enum class JiraCliOutputFormat(val token: String) {
    JSON("json")
}

private enum class JiraCliIssueField(val key: String) {
    FIELDS("fields"),
    STATUS("status"),
    NAME("name")
}

private enum class JiraCliFailure {
    FIELDS_MISSING,
    STATUS_MISSING,
    NAME_MISSING,
    EMPTY_STATUS,
    PARSE_ERROR
}

private object JiraCliDefaults {
    const val EXECUTABLE: String = "jira"
}

@JvmInline
private value class JiraCliExecutable(val value: String)

@JvmInline
private value class JiraCliOutput(val value: String)

@JvmInline
private value class JiraIssueKey(val value: String) {
    val isBlank: Boolean get() = value.isBlank()
}

@JvmInline
private value class JiraStatus(val value: String) {
    val isBlank: Boolean get() = value.isBlank()
}

private sealed class JiraCliStatusResult {
    data class Success(val status: JiraStatus) : JiraCliStatusResult()
    data class Failure(val reason: JiraCliFailure, val detail: String) : JiraCliStatusResult()
}
