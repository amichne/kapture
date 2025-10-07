package io.amichne.kapture.core.adapter.internal.jira

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.config.Integration
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.config.Plugin.Companion.toPlugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.core.util.JsonProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class JiraCliAdapter(
    private val integration: Plugin.Cli = Integration.Jira.toPlugin(),
    private val json: Json = JsonProvider.defaultJson
) : Adapter {
    override fun close() {
        // No persistent resources to release for CLI integration.
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskSearchResult.NotFound

        val command = buildCommand(taskKey)
        val result = CommandExecutor.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${integration.executable} returned ${result.exitCode} for ${taskKey.value}: ${result.stderr}"
            }
            return TaskSearchResult.Error("${integration.executable} exit ${result.exitCode}")
        }

        return when (val parsed = parseStatus(JiraCliOutput(result.stdout))) {
            is JiraCliStatusResult.Success -> TaskSearchResult.Found(parsed.status.value)
            is JiraCliStatusResult.Failure -> {
                Environment.debug {
                    "${integration.executable} status resolution failed (${parsed.reason}) for ${taskKey.value}: ${parsed.detail}"
                }
                when (parsed.reason) {
                    JiraCliFailure.FIELDS_MISSING,
                    JiraCliFailure.STATUS_MISSING,
                    JiraCliFailure.NAME_MISSING,
                    JiraCliFailure.EMPTY_STATUS -> TaskSearchResult.NotFound

                    JiraCliFailure.PARSE_ERROR -> TaskSearchResult.Error(parsed.detail.ifBlank { "parse error" })
                }
            }
        }
    }

    override fun trackSession(snapshot: SessionSnapshot) {
        Environment.debug { "${integration.executable} integration does not support session tracking; skipping." }
    }

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult {
        val parentKey = JiraTaskKey(parentId)
        if (parentKey.isBlank) return SubtaskCreationResult.Failure("Parent ID cannot be blank")

        // First, validate parent status
        val parentStatus = getTaskStatus(parentId)
        val allowedStatuses = setOf("Ready for Dev", "In Progress", "READY_FOR_DEV", "IN_PROGRESS")

        when (parentStatus) {
            is TaskSearchResult.Found -> {
                val normalizedStatus = parentStatus.status.replace(" ", "_").uppercase()
                if (!allowedStatuses.any { it.replace(" ", "_").uppercase() == normalizedStatus }) {
                    return SubtaskCreationResult.Failure(
                        "Parent task ${parentId} must be in 'Ready for Dev' or 'In Progress' status, currently: ${parentStatus.status}"
                    )
                }

                // Auto-transition to "In Progress" if currently "Ready for Dev"
                if (normalizedStatus.contains("READY")) {
                    val transitionResult = transitionTask(parentId, "In Progress")
                    if (transitionResult is TaskTransitionResult.Failure) {
                        Environment.debug { "Failed to auto-transition parent to In Progress: ${transitionResult.message}" }
                    }
                }
            }

            TaskSearchResult.NotFound -> return SubtaskCreationResult.Failure("Parent task ${parentId} not found")
            is TaskSearchResult.Error -> return SubtaskCreationResult.Failure(
                "Failed to check parent status: ${parentStatus.message}"
            )
        }

        val command = buildCreateSubtaskCommand(parentKey, title)
        val result = CommandExecutor.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${integration.executable} subtask creation failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return SubtaskCreationResult.Failure("${integration.executable} exit ${result.exitCode}: ${result.stderr}")
        }

        return parseSubtaskCreationResponse(JiraCliOutput(result.stdout))
    }

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskTransitionResult.Failure("Task ID cannot be blank")

        val command = buildTransitionCommand(taskKey, targetStatus)
        val result = CommandExecutor.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${integration.executable} transition failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return TaskTransitionResult.Failure("${integration.executable} exit ${result.exitCode}: ${result.stderr}")
        }

        return TaskTransitionResult.Success
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskDetailsResult.Failure("Task ID cannot be blank")

        val command = buildCommand(taskKey)
        val result = CommandExecutor.capture(
            cmd = command,
            env = integration.environment,
            timeoutSeconds = integration.timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${integration.executable} returned ${result.exitCode} for ${taskKey.value}: ${result.stderr}"
            }
            return TaskDetailsResult.Failure("${integration.executable} exit ${result.exitCode}")
        }

        return parseTaskDetails(JiraCliOutput(result.stdout))
    }

    private fun buildCreateSubtaskCommand(
        parentKey: JiraTaskKey,
        title: String?
    ): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        val baseCommand = mutableListOf(
            executable.value,
            JiraCliCommand.TASK.token,
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

    private fun buildTransitionCommand(
        taskKey: JiraTaskKey,
        targetStatus: String
    ): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        return listOf(
            executable.value,
            JiraCliCommand.TASK.token,
            JiraCliCommand.MOVE.token,
            taskKey.value,
            targetStatus,
            "--no-input"
        )
    }

    private fun parseSubtaskCreationResponse(output: JiraCliOutput): SubtaskCreationResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val key = root.jsonObject[JiraCliTaskField.KEY.key]?.jsonPrimitive?.content
                  ?: return SubtaskCreationResult.Failure("No key field in response")
        SubtaskCreationResult.Success(key)
    } catch (throwable: Exception) {
        SubtaskCreationResult.Failure(throwable.message ?: "Failed to parse response")
    }

    private fun parseTaskDetails(output: JiraCliOutput): TaskDetailsResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val key = root.jsonObject[JiraCliTaskField.KEY.key]?.jsonPrimitive?.content
                  ?: return TaskDetailsResult.Failure("No key field in response")

        val fields = root.jsonObject[JiraCliTaskField.FIELDS.key]?.jsonObject
                     ?: return TaskDetailsResult.Failure("No fields in response")

        val summary = fields[JiraCliTaskField.SUMMARY.key]?.jsonPrimitive?.content ?: ""
        val description = fields[JiraCliTaskField.DESCRIPTION.key]?.jsonPrimitive?.content ?: ""
        val parentKey = fields[JiraCliTaskField.PARENT.key]?.jsonObject
            ?.get(JiraCliTaskField.KEY.key)?.jsonPrimitive?.content

        TaskDetailsResult.Success(key, summary, description, parentKey)
    } catch (throwable: Exception) {
        TaskDetailsResult.Failure(throwable.message ?: "Failed to parse task details")
    }

    private fun buildCommand(taskKey: JiraTaskKey): List<String> {
        val executable = JiraCliExecutable(integration.executable.ifBlank { JiraCliDefaults.EXECUTABLE })
        return listOf(
            executable.value,
            JiraCliCommand.TASK.token,
            JiraCliCommand.VIEW.token,
            taskKey.value,
            JiraCliOption.OUTPUT.flag,
            JiraCliOutputFormat.JSON.token
        )
    }

    private fun parseStatus(output: JiraCliOutput): JiraCliStatusResult = try {
        val root: JsonElement = json.parseToJsonElement(output.value)
        val fields = root.jsonObject[JiraCliTaskField.FIELDS.key]?.jsonObject
                     ?: return JiraCliStatusResult.Failure(JiraCliFailure.FIELDS_MISSING, output.value)
        val statusObj = fields[JiraCliTaskField.STATUS.key]?.jsonObject
                        ?: return JiraCliStatusResult.Failure(JiraCliFailure.STATUS_MISSING, output.value)
        val name = statusObj[JiraCliTaskField.NAME.key]?.jsonPrimitive?.content
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
