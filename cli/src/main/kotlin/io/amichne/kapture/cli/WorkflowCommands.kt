package io.amichne.kapture.cli

import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskTransitionResult
import io.amichne.kapture.core.util.Environment
import java.io.File
import kotlin.system.exitProcess

/**
 * Workflow commands orchestrate the full Jira + Git + GitHub flow:
 * - subtask: Create subtask and validate parent status
 * - branch: Create branch and transition subtask to In Progress
 * - review: Create pull request and transition to Code Review
 * - merge: Merge branch and close subtask
 */
object WorkflowCommands {

    fun executeSubtask(args: List<String>, config: Config, client: ExternalClient<*>) {
        if (args.isEmpty()) {
            System.err.println("Usage: git kapture subtask <PARENT-ID> [subtask-title]")
            exitProcess(1)
        }

        val parentId = args[0]
        val title = args.drop(1).joinToString(" ").ifBlank { null }

        println("Creating subtask under parent ${parentId}...")

        when (val result = client.adapter.createSubtask(parentId, title)) {
            is SubtaskCreationResult.Success -> {
                println("✓ Created subtask: ${result.subtaskKey}")
                println("  Parent: ${parentId}")
            }
            is SubtaskCreationResult.Failure -> {
                System.err.println("✗ Failed to create subtask: ${result.message}")
                exitProcess(1)
            }
        }
    }

    fun executeBranch(args: List<String>, config: Config, workDir: File, env: Map<String, String>, client: ExternalClient<*>) {
        if (args.isEmpty()) {
            System.err.println("Usage: git kapture branch <SUBTASK-ID>")
            exitProcess(1)
        }

        val subtaskId = args[0]

        // Validate subtask exists and get current status
        val statusResult = client.getTaskStatus(subtaskId)
        when (statusResult) {
            is TaskSearchResult.Found -> {
                val normalizedStatus = statusResult.status.replace(" ", "_").uppercase()
                if (!normalizedStatus.contains("READY") && !normalizedStatus.contains("IN_PROGRESS")) {
                    System.err.println("✗ Subtask ${subtaskId} must be in 'Ready for Dev' status")
                    System.err.println("  Current status: ${statusResult.status}")
                    exitProcess(1)
                }
            }
            TaskSearchResult.NotFound -> {
                System.err.println("✗ Subtask ${subtaskId} not found")
                exitProcess(1)
            }
            is TaskSearchResult.Error -> {
                System.err.println("✗ Failed to check subtask status: ${statusResult.message}")
                exitProcess(1)
            }
        }

        // Generate branch name from pattern and task
        val branchName = generateBranchName(subtaskId, config)

        println("Creating branch: ${branchName}")
        val createBranchResult = CommandExecutor.capture(
            cmd = listOf("git", "checkout", "-b", branchName),
            workDir = workDir,
            env = env
        )

        if (createBranchResult.exitCode != 0) {
            System.err.println("✗ Failed to create branch: ${createBranchResult.stderr}")
            exitProcess(createBranchResult.exitCode)
        }

        // Transition subtask to "In Progress"
        println("Transitioning ${subtaskId} to 'In Progress'...")
        when (val transitionResult = client.adapter.transitionTask(subtaskId, "In Progress")) {
            is TaskTransitionResult.Success -> {
                println("✓ Branch created: ${branchName}")
                println("✓ Subtask ${subtaskId} → In Progress")
            }
            is TaskTransitionResult.Failure -> {
                System.err.println("⚠ Branch created but failed to transition subtask: ${transitionResult.message}")
                exitProcess(1)
            }
        }
    }

    fun executeReview(args: List<String>, config: Config, workDir: File, env: Map<String, String>, client: ExternalClient<*>) {
        // Get current branch and extract task
        val currentBranchResult = CommandExecutor.capture(
            cmd = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
            workDir = workDir,
            env = env
        )

        if (currentBranchResult.exitCode != 0) {
            System.err.println("✗ Failed to get current branch")
            exitProcess(currentBranchResult.exitCode)
        }

        val currentBranch = currentBranchResult.stdout.trim()
        val subtaskId = BranchUtils.extractTask(currentBranch, config.branchPattern)

        if (subtaskId.isNullOrBlank()) {
            System.err.println("✗ Current branch '${currentBranch}' does not contain a valid task ID")
            System.err.println("  Expected pattern: ${config.branchPattern}")
            exitProcess(1)
        }

        // Validate subtask is in "In Progress" status
        val statusResult = client.getTaskStatus(subtaskId)
        when (statusResult) {
            is TaskSearchResult.Found -> {
                val normalizedStatus = statusResult.status.replace(" ", "_").uppercase()
                if (!normalizedStatus.contains("IN_PROGRESS")) {
                    System.err.println("✗ Subtask ${subtaskId} must be in 'In Progress' status")
                    System.err.println("  Current status: ${statusResult.status}")
                    exitProcess(1)
                }
            }
            TaskSearchResult.NotFound -> {
                System.err.println("✗ Subtask ${subtaskId} not found")
                exitProcess(1)
            }
            is TaskSearchResult.Error -> {
                System.err.println("✗ Failed to check subtask status: ${statusResult.message}")
                exitProcess(1)
            }
        }

        // Get task details for PR body
        val taskDetails = client.adapter.getTaskDetails(subtaskId)
        val (title, body) = when (taskDetails) {
            is TaskDetailsResult.Success -> {
                Pair(taskDetails.summary, buildPullRequestBody(taskDetails, client))
            }
            is TaskDetailsResult.Failure -> {
                System.err.println("⚠ Could not fetch task details: ${taskDetails.message}")
                Pair(subtaskId, "")
            }
        }

        // Push current branch to remote
        println("Pushing branch to remote...")
        val pushResult = CommandExecutor.capture(
            cmd = listOf("git", "push", "-u", "origin", currentBranch),
            workDir = workDir,
            env = env
        )

        if (pushResult.exitCode != 0) {
            System.err.println("✗ Failed to push branch: ${pushResult.stderr}")
            exitProcess(pushResult.exitCode)
        }

        // Create pull request using GitHub CLI
        println("Creating pull request...")
        val prCommand = listOf(
            "gh", "pr", "create",
            "--title", title,
            "--body", body
        )

        val prResult = CommandExecutor.capture(
            cmd = prCommand,
            workDir = workDir,
            env = env
        )

        if (prResult.exitCode != 0) {
            System.err.println("✗ Failed to create pull request: ${prResult.stderr}")
            exitProcess(prResult.exitCode)
        }

        // Transition subtask to "Code Review"
        println("Transitioning ${subtaskId} to 'Code Review'...")
        when (val transitionResult = client.adapter.transitionTask(subtaskId, "Code Review")) {
            is TaskTransitionResult.Success -> {
                println("✓ Pull request created")
                println("✓ Subtask ${subtaskId} → Code Review")
                println("\n${prResult.stdout}")
            }
            is TaskTransitionResult.Failure -> {
                System.err.println("⚠ PR created but failed to transition subtask: ${transitionResult.message}")
                println("\n${prResult.stdout}")
            }
        }
    }

    fun executeMerge(args: List<String>, config: Config, workDir: File, env: Map<String, String>, client: ExternalClient<*>) {
        // Get current branch and extract task
        val currentBranchResult = CommandExecutor.capture(
            cmd = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
            workDir = workDir,
            env = env
        )

        if (currentBranchResult.exitCode != 0) {
            System.err.println("✗ Failed to get current branch")
            exitProcess(currentBranchResult.exitCode)
        }

        val currentBranch = currentBranchResult.stdout.trim()
        val subtaskId = BranchUtils.extractTask(currentBranch, config.branchPattern)

        if (subtaskId.isNullOrBlank()) {
            System.err.println("✗ Current branch '${currentBranch}' does not contain a valid task ID")
            exitProcess(1)
        }

        // Validate subtask is in "Code Review" status
        val statusResult = client.getTaskStatus(subtaskId)
        when (statusResult) {
            is TaskSearchResult.Found -> {
                val normalizedStatus = statusResult.status.replace(" ", "_").uppercase()
                if (!normalizedStatus.contains("CODE_REVIEW") && !normalizedStatus.contains("REVIEW")) {
                    System.err.println("✗ Subtask ${subtaskId} must be in 'Code Review' status")
                    System.err.println("  Current status: ${statusResult.status}")
                    exitProcess(1)
                }
            }
            TaskSearchResult.NotFound -> {
                System.err.println("✗ Subtask ${subtaskId} not found")
                exitProcess(1)
            }
            is TaskSearchResult.Error -> {
                System.err.println("✗ Failed to check subtask status: ${statusResult.message}")
                exitProcess(1)
            }
        }

        // Merge pull request using GitHub CLI
        println("Merging pull request...")
        val mergeResult = CommandExecutor.capture(
            cmd = listOf("gh", "pr", "merge", "--auto", "--squash"),
            workDir = workDir,
            env = env
        )

        if (mergeResult.exitCode != 0) {
            System.err.println("✗ Failed to merge pull request: ${mergeResult.stderr}")
            exitProcess(mergeResult.exitCode)
        }

        // Transition subtask to "Closed"
        println("Transitioning ${subtaskId} to 'Closed'...")
        when (val transitionResult = client.adapter.transitionTask(subtaskId, "Done")) {
            is TaskTransitionResult.Success -> {
                println("✓ Pull request merged")
                println("✓ Subtask ${subtaskId} → Closed")
            }
            is TaskTransitionResult.Failure -> {
                System.err.println("⚠ PR merged but failed to transition subtask: ${transitionResult.message}")
            }
        }
    }

    private fun generateBranchName(taskId: String, config: Config): String {
        // Extract pattern format and generate branch name
        // For now, use a simple format: TASK-ID/description
        return "${taskId}/dev"
    }

    private fun buildPullRequestBody(details: TaskDetailsResult.Success, client: ExternalClient<*>): String {
        val sections = mutableListOf<String>()

        // Add Jira task details section
        sections.add("""
            <details>
            <summary>📋 Jira Task Details</summary>

            **Task:** ${details.key}
            **Summary:** ${details.summary}

            ${details.description.ifBlank { "_No description provided_" }}
            </details>
        """.trimIndent())

        // Add related PRs section (sibling subtasks)
        details.parentKey?.let { parentKey ->
            val relatedPrs = findRelatedPullRequests(parentKey, details.key, client)
            if (relatedPrs.isNotEmpty()) {
                sections.add("""
                    <details>
                    <summary>🔗 Related Pull Requests</summary>

                    ${relatedPrs.joinToString("\n")}
                    </details>
                """.trimIndent())
            }
        }

        return sections.joinToString("\n\n")
    }

    private fun findRelatedPullRequests(parentKey: String, currentKey: String, client: ExternalClient<*>): List<String> {
        // This would query Jira for sibling subtasks and find their associated PRs
        // For now, return empty list as this requires more complex JQL queries
        Environment.debug { "Finding related PRs for parent ${parentKey}, excluding ${currentKey}" }
        return emptyList()
    }
}
