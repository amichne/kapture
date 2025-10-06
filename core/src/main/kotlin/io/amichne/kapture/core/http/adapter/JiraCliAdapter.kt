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

    override fun createSubtask(parentId: String, title: String?): SubtaskCreationResult {
        val parentKey = JiraIssueKey(parentId)
        if (parentKey.isBlank) return SubtaskCreationResult.Failure("Parent ID cannot be blank")

        // First, validate parent status
        val parentStatus = getTicketStatus(parentId)
        val allowedStatuses = setOf("Ready for Dev", "In Progress", "READY_FOR_DEV", "IN_PROGRESS")

        when (parentStatus) {
            is TicketLookupResult.Found -> {
                val normalizedStatus = parentStatus.status.replace(" ", "_").uppercase()
                if (!allowedStatuses.any { it.replace(" ", "_").uppercase() == normalizedStatus }) {
                    return SubtaskCreationResult.Failure(
                        "Parent issue ${parentId} must be in 'Ready for Dev' or 'In Progress' status, currently: ${parentStatus.status}"
                    )
                }

                // Auto-transition to "In Progress" if currently "Ready for Dev"
                if (normalizedStatus.contains("READY")) {
                    val transitionResult = transitionIssue(parentId, "In Progress")
                    if (transitionResult is TransitionResult.Failure) {
                        Environment.debug { "Failed to auto-transition parent to In Progress: ${transitionResult.message}" }
                    }
                }
            }
            TicketLookupResult.NotFound -> return SubtaskCreationResult.Failure("Parent issue ${parentId} not found")
            is TicketLookupResult.Error -> return SubtaskCreationResult.Failure("Failed to check parent status: ${parentStatus.message}")
        }

        val command = buildCreateSubtaskCommand(parentKey, title)
        val result = Exec.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "jira-cli subtask creation failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return SubtaskCreationResult.Failure("jira-cli exit ${result.exitCode}: ${result.stderr}")
        }

        return parseSubtaskCreationResponse(JiraCliOutput(result.stdout))
    }

    override fun transitionIssue(issueId: String, targetStatus: String): TransitionResult {
        val issueKey = JiraIssueKey(issueId)
        if (issueKey.isBlank) return TransitionResult.Failure("Issue ID cannot be blank")

        val command = buildTransitionCommand(issueKey, targetStatus)
        val result = Exec.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "jira-cli transition failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return TransitionResult.Failure("jira-cli exit ${result.exitCode}: ${result.stderr}")
        }

        return TransitionResult.Success
    }

    override fun getIssueDetails(issueId: String): IssueDetailsResult {
        val issueKey = JiraIssueKey(issueId)
        if (issueKey.isBlank) return IssueDetailsResult.Failure("Issue ID cannot be blank")

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
            return IssueDetailsResult.Failure("jira-cli exit ${result.exitCode}")
        }

        return parseIssueDetails(JiraCliOutput(result.stdout))
    }

    private fun buildCreateSubtaskCommand(parentKey: JiraIssueKey, title: String?): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        val baseCommand = mutableListOf(
            executable.value,
            JiraCliCommand.ISSUE.token,
            JiraCliCommand.CREATE.token,
            "--type", "Sub-task",
            "--parent", parentKey.value
        )

        title?.let {
            baseCommand.add("--summary")
            baseCommand.add(it)
            baseCommand.add("--no-input")
        }

        return baseCommand
    }

    private fun buildTransitionCommand(issueKey: JiraIssueKey, targetStatus: String): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        return listOf(
            executable.value,
            JiraCliCommand.ISSUE.token,
            JiraCliCommand.MOVE.token,
            issueKey.value,
            targetStatus,
            "--no-input"
        )
    }

    private fun parseSubtaskCreationResponse(output: JiraCliOutput): SubtaskCreationResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val key = root.jsonObject[JiraCliIssueField.KEY.key]?.jsonPrimitive?.content
            ?: return SubtaskCreationResult.Failure("No key field in response")
        SubtaskCreationResult.Success(key)
    } catch (throwable: Exception) {
        SubtaskCreationResult.Failure(throwable.message ?: "Failed to parse response")
    }

    private fun parseIssueDetails(output: JiraCliOutput): IssueDetailsResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val key = root.jsonObject[JiraCliIssueField.KEY.key]?.jsonPrimitive?.content
            ?: return IssueDetailsResult.Failure("No key field in response")

        val fields = root.jsonObject[JiraCliIssueField.FIELDS.key]?.jsonObject
            ?: return IssueDetailsResult.Failure("No fields in response")

        val summary = fields[JiraCliIssueField.SUMMARY.key]?.jsonPrimitive?.content ?: ""
        val description = fields[JiraCliIssueField.DESCRIPTION.key]?.jsonPrimitive?.content ?: ""
        val parentKey = fields[JiraCliIssueField.PARENT.key]?.jsonObject
            ?.get(JiraCliIssueField.KEY.key)?.jsonPrimitive?.content

        IssueDetailsResult.Success(key, summary, description, parentKey)
    } catch (throwable: Exception) {
        IssueDetailsResult.Failure(throwable.message ?: "Failed to parse issue details")
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
    VIEW("view"),
    CREATE("create"),
    MOVE("move")
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
    NAME("name"),
    KEY("key"),
    SUMMARY("summary"),
    DESCRIPTION("description"),
    PARENT("parent")
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
