package io.amichne.kapture.core.adapter.internal.jira

import io.amichne.kapture.core.adapter.Adapter
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.model.config.Plugin
import io.amichne.kapture.core.model.session.SessionSnapshot
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskStatus
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.util.Environment
import io.amichne.kapture.core.util.JsonProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class JiraCliAdapter(
    private val plugin: Plugin.Cli,
    private val json: Json = JsonProvider.defaultJson,
    defaultTimeoutMs: Int = 60_000,
    executableResolver: (Plugin.Cli) -> JiraCliExecutable = Companion::resolveExecutableFromPaths
) : Adapter {

    private val executable: JiraCliExecutable = executableResolver(plugin)
    private val environment: Map<String, String> = plugin.environment
    private val timeoutSeconds: Long = toSeconds(plugin.timeoutMs ?: defaultTimeoutMs)

    override fun close() {
        // No persistent resources to release for CLI plugins.
    }

    override fun getTaskStatus(taskId: String): TaskSearchResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskSearchResult.NotFound

        val result = CommandExecutor.capture(
            cmd = buildCommand(taskKey),
            env = environment,
            timeoutSeconds = timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${executable.value} returned ${result.exitCode} for ${taskKey.value}: ${result.stderr}"
            }
            return TaskSearchResult.Error("${executable.value} exit ${result.exitCode}")
        }

        return when (val parsed = parseStatus(JiraCliOutput(result.stdout))) {
            is JiraCliStatusResult.Success -> TaskSearchResult.Found(
                TaskStatus(
                    provider = plugin.type.name,
                    key = taskKey.value,
                    raw = parsed.status.value
                )
            )

            is JiraCliStatusResult.Failure -> {
                Environment.debug {
                    "${executable.value} status resolution failed (${parsed.reason}) for ${taskKey.value}: ${parsed.detail}"
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
        Environment.debug { "${executable.value} plugin does not support session tracking; skipping." }
    }

    override fun createSubtask(
        parentId: String,
        title: String?
    ): SubtaskCreationResult {
        val parentKey = JiraTaskKey(parentId)
        if (parentKey.isBlank) return SubtaskCreationResult.Failure("Parent ID cannot be blank")

        val parentStatus = getTaskStatus(parentId)
        val allowedStatuses = setOf("READY_FOR_DEV", "IN_PROGRESS")
        val allowedInternalStatuses = setOf(InternalStatus.TODO, InternalStatus.IN_PROGRESS)

        when (parentStatus) {
            is TaskSearchResult.Found -> {
                val status = parentStatus.status
                val normalizedStatus = status.raw?.replace(" ", "_")?.uppercase()
                val isAllowed = status.internal?.let { it in allowedInternalStatuses } == true ||
                    (normalizedStatus != null && normalizedStatus in allowedStatuses)
                if (!isAllowed) {
                    return SubtaskCreationResult.Failure(
                        "Parent task $parentId must be in 'Ready for Dev' or 'In Progress' status, currently: ${status.raw ?: status.internal?.name ?: "UNKNOWN"}"
                    )
                }

                val shouldAutoTransition =
                    status.internal == InternalStatus.TODO ||
                        (normalizedStatus?.contains("READY") == true)
                if (shouldAutoTransition) {
                    val transitionResult = transitionTask(parentId, "In Progress")
                    if (transitionResult is TaskTransitionResult.Failure) {
                        Environment.debug {
                            "Failed to auto-transition parent to In Progress: ${transitionResult.message}"
                        }
                    }
                }
            }

            TaskSearchResult.NotFound -> return SubtaskCreationResult.Failure("Parent task $parentId not found")
            is TaskSearchResult.Error -> return SubtaskCreationResult.Failure(
                "Failed to check parent status: ${parentStatus.message}"
            )
        }

        val result = CommandExecutor.capture(
            cmd = buildCreateSubtaskCommand(parentKey, title),
            env = environment,
            timeoutSeconds = timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${executable.value} subtask creation failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return SubtaskCreationResult.Failure("${executable.value} exit ${result.exitCode}: ${result.stderr}")
        }

        return parseSubtaskCreationResponse(JiraCliOutput(result.stdout))
    }

    override fun transitionTask(
        taskId: String,
        targetStatus: String
    ): TaskTransitionResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskTransitionResult.Failure("Task ID cannot be blank")

        val result = CommandExecutor.capture(
            cmd = buildTransitionCommand(taskKey, targetStatus),
            env = environment,
            timeoutSeconds = timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${executable.value} transition failed with exit code ${result.exitCode}: ${result.stderr}"
            }
            return TaskTransitionResult.Failure("${executable.value} exit ${result.exitCode}: ${result.stderr}")
        }

        return TaskTransitionResult.Success
    }

    override fun getTaskDetails(taskId: String): TaskDetailsResult {
        val taskKey = JiraTaskKey(taskId)
        if (taskKey.isBlank) return TaskDetailsResult.Failure("Task ID cannot be blank")

        val result = CommandExecutor.capture(
            cmd = buildCommand(taskKey),
            env = environment,
            timeoutSeconds = timeoutSeconds
        )

        if (result.exitCode != 0) {
            Environment.debug {
                "${executable.value} returned ${result.exitCode} for ${taskKey.value}: ${result.stderr}"
            }
            return TaskDetailsResult.Failure("${executable.value} exit ${result.exitCode}")
        }

        return parseTaskDetails(JiraCliOutput(result.stdout))
    }

    private fun buildCreateSubtaskCommand(
        parentKey: JiraTaskKey,
        title: String?
    ): List<String> {
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
    ): List<String> = listOf(
        executable.value,
        JiraCliCommand.TASK.token,
        JiraCliCommand.MOVE.token,
        taskKey.value,
        targetStatus,
        "--no-input"
    )

    private fun buildCommand(taskKey: JiraTaskKey): List<String> = listOf(
        executable.value,
        JiraCliCommand.TASK.token,
        JiraCliCommand.VIEW.token,
        taskKey.value,
        JiraCliOption.OUTPUT.flag,
        JiraCliOutputFormat.JSON.token
    )

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

    private fun toSeconds(timeoutMs: Int): Long {
        require(timeoutMs > 0) { "CLI plugin timeout must be positive (was $timeoutMs)" }
        return (timeoutMs.toLong() + 999) / 1000
    }

    companion object {
        private val pathSeparatorChars = setOf('/', '\\')

        fun resolveExecutableFromPaths(plugin: Plugin.Cli): JiraCliExecutable {
            val candidates = plugin.paths
                .takeIf { it.isNotEmpty() }
                ?: error("CLI plugin '${plugin.provider}' must declare at least one path candidate")

            val pathEntries = System.getenv("PATH")
                ?.split(File.pathSeparatorChar)
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val pathextEntries = System.getenv("PATHEXT")
                ?.split(File.pathSeparatorChar)
                ?.mapNotNull { ext ->
                    val trimmed = ext.trim()
                    if (trimmed.isEmpty()) null
                    else if (trimmed.startsWith(".")) trimmed
                    else ".$trimmed"
                }
                ?: emptyList()

            for (candidate in candidates) {
                if (candidate.isBlank()) continue
                findExecutable(candidate, pathEntries, pathextEntries)?.let {
                    return JiraCliExecutable(it)
                }
            }

            throw IllegalStateException(
                "Unable to resolve executable for CLI plugin '${plugin.provider}'. " +
                    "Checked candidates: ${candidates.joinToString()}"
            )
        }

        private fun findExecutable(
            candidate: String,
            pathEntries: List<String>,
            pathextEntries: List<String>
        ): String? {
            val containsSeparator = candidate.any { pathSeparatorChars.contains(it) }
            if (containsSeparator) {
                val candidatePath = runCatching { Paths.get(candidate) }.getOrNull()
                if (candidatePath != null && isExecutable(candidatePath)) {
                    return candidatePath.toAbsolutePath().normalize().toString()
                }
                return null
            }

            for (dir in pathEntries) {
                val base = Paths.get(dir, candidate)
                if (isExecutable(base)) {
                    return base.toAbsolutePath().normalize().toString()
                }
                for (ext in pathextEntries) {
                    val extended = Paths.get(dir, candidate + ext)
                    if (isExecutable(extended)) {
                        return extended.toAbsolutePath().normalize().toString()
                    }
                }
            }

            return null
        }

        private fun isExecutable(path: Path): Boolean =
            Files.exists(path) && Files.isRegularFile(path) && Files.isExecutable(path)
    }
}
