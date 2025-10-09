package io.amichne.kapture.cli

import io.amichne.kapture.core.ExternalClient
import io.amichne.kapture.core.command.CommandExecutor
import io.amichne.kapture.core.git.BranchUtils
import io.amichne.kapture.core.model.config.Config
import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.SubtaskCreationResult
import io.amichne.kapture.core.model.task.TaskDetailsResult
import io.amichne.kapture.core.model.task.TaskSearchResult
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

    fun executeSubtask(
        args: List<String>,
        config: Config,
        workDir: File,
        env: Map<String, String>,
        client: ExternalClient<*>
    ) {
        if (args.isEmpty()) {
            System.err.println("Usage:")
            System.err.println("  git subtask <PARENT-ID> <subtask-title>  # Create a subtask under parent")
            System.err.println("  git subtask <SUBTASK-ID>                 # Transition subtask to In Progress")
            exitProcess(1)
        }

        val firstArg = args[0]
        val hasTitle = args.size > 1

        if (hasTitle) {
            // Mode 1: Create new subtask under parent
            val parentId = firstArg
            val title = args.drop(1).joinToString(" ")

            println("Creating subtask under parent ${parentId}...")

            when (val result = client.adapter.createSubtask(parentId, title)) {
                is SubtaskCreationResult.Success -> {
                    val subtaskKey = result.subtaskKey
                    println("✓ Created subtask: ${subtaskKey}")
                    println("  Parent: ${parentId}")

                    // Transition the new subtask to In Progress
                    println("Transitioning ${subtaskKey} to 'In Progress'...")
                    when (val transitionResult = client.adapter.transitionTask(subtaskKey, "In Progress")) {
                        is TaskTransitionResult.Success -> {
                            println("✓ Subtask ${subtaskKey} → In Progress")
                        }

                        is TaskTransitionResult.Failure -> {
                            System.err.println("⚠ Subtask created but failed to transition: ${transitionResult.message}")
                        }
                    }
                }

                is SubtaskCreationResult.Failure -> {
                    System.err.println("✗ Failed to create subtask: ${result.message}")
                    exitProcess(1)
                }
            }
        } else {
            // Mode 2: Transition existing subtask to In Progress
            val subtaskId = firstArg

            // Validate subtask exists
            val statusResult = client.getTaskStatus(subtaskId)
            when (statusResult) {
                is TaskSearchResult.Found -> {
                    val normalizedStatus = statusResult.normalizedStatus()
                    val internalStatus = statusResult.status.internal
                    val isAlreadyInProgress = internalStatus == InternalStatus.IN_PROGRESS ||
                                              normalizedStatus.contains("IN_PROGRESS")

                    if (isAlreadyInProgress) {
                        println("✓ Subtask ${subtaskId} is already in 'In Progress'")
                        return
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

            // Transition subtask to In Progress
            println("Transitioning ${subtaskId} to 'In Progress'...")
            when (val transitionResult = client.adapter.transitionTask(subtaskId, "In Progress")) {
                is TaskTransitionResult.Success -> {
                    println("✓ Subtask ${subtaskId} → In Progress")
                }

                is TaskTransitionResult.Failure -> {
                    System.err.println("✗ Failed to transition subtask: ${transitionResult.message}")
                    exitProcess(1)
                }
            }
        }
    }

    fun executeStart(
        args: List<String>,
        config: Config,
        workDir: File,
        env: Map<String, String>,
        client: ExternalClient<*>
    ) {
        if (args.isEmpty()) {
            System.err.println("Usage: git start <TASK-ID> [--branch/-B <branch-name>]")
            exitProcess(1)
        }

        // Parse arguments
        val taskId = args[0]
        val branchFlagIndex = args.indexOfFirst { it == "--branch" || it == "-B" }
        val customBranchName = if (branchFlagIndex >= 0 && branchFlagIndex + 1 < args.size) {
            args[branchFlagIndex + 1]
        } else null

        // Validate task exists and get current status
        val statusResult = client.getTaskStatus(taskId)
        val taskDetails = when (statusResult) {
            is TaskSearchResult.Found -> {
                val normalizedStatus = statusResult.normalizedStatus()
                val internalAllowed = statusResult.status.internal?.let {
                    it == InternalStatus.TODO || it == InternalStatus.IN_PROGRESS
                } == true
                val legacyAllowed = normalizedStatus.contains("READY") || normalizedStatus.contains("IN_PROGRESS")
                if (!internalAllowed && !legacyAllowed) {
                    System.err.println("✗ Task ${taskId} must be in 'Ready for Dev' status")
                    System.err.println("  Current status: ${statusResult.displayStatus()}")
                    exitProcess(1)
                }
                // Fetch task details for branch naming
                client.adapter.getTaskDetails(taskId)
            }

            TaskSearchResult.NotFound -> {
                System.err.println("✗ Task ${taskId} not found")
                exitProcess(1)
            }

            is TaskSearchResult.Error -> {
                System.err.println("✗ Failed to check task status: ${statusResult.message}")
                exitProcess(1)
            }
        }

        // Determine branch name
        val branchName = customBranchName ?: when (taskDetails) {
            is TaskDetailsResult.Success -> {
                // Generate branch name from task title
                generateBranchNameFromTitle(taskId, taskDetails.summary, config)
            }
            is TaskDetailsResult.Failure -> {
                // Fall back to simple task ID-based name
                generateBranchName(taskId, config)
            }
        }

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

        // Transition task to "In Progress"
        println("Transitioning ${taskId} to 'In Progress'...")
        when (val transitionResult = client.adapter.transitionTask(taskId, "In Progress")) {
            is TaskTransitionResult.Success -> {
                println("✓ Branch created: ${branchName}")
                println("✓ Task ${taskId} → In Progress")
            }

            is TaskTransitionResult.Failure -> {
                System.err.println("⚠ Branch created but failed to transition task: ${transitionResult.message}")
                exitProcess(1)
            }
        }
    }

    fun executeReview(
        args: List<String>,
        config: Config,
        workDir: File,
        env: Map<String, String>,
        client: ExternalClient<*>
    ) {
        // Optional title parameter
        val customTitle = args.joinToString(" ").ifBlank { null }

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
                val status = statusResult.status
                val normalizedStatus = statusResult.normalizedStatus()
                val internalAllowed = status.internal == InternalStatus.IN_PROGRESS
                val legacyAllowed = normalizedStatus.contains("IN_PROGRESS")
                if (!internalAllowed && !legacyAllowed) {
                    System.err.println("✗ Subtask ${subtaskId} must be in 'In Progress' status")
                    System.err.println("  Current status: ${statusResult.displayStatus()}")
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
                val prTitle = customTitle ?: taskDetails.summary
                Pair(prTitle, buildPullRequestBody(taskDetails, client))
            }

            is TaskDetailsResult.Failure -> {
                System.err.println("⚠ Could not fetch task details: ${taskDetails.message}")
                val prTitle = customTitle ?: subtaskId
                Pair(prTitle, "")
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

    fun executeMerge(
        args: List<String>,
        config: Config,
        workDir: File,
        env: Map<String, String>,
        client: ExternalClient<*>
    ) {
        // Parse arguments
        val nonFlagArgs = args.filter { !it.startsWith("--") }
        val closeParent = args.any { it.startsWith("--close-parent") && (it == "--close-parent" || it.contains("=true")) }

        // Determine subtask ID - either from args or from current branch
        val subtaskId = if (nonFlagArgs.isNotEmpty()) {
            nonFlagArgs[0]
        } else {
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
            val extracted = BranchUtils.extractTask(currentBranch, config.branchPattern)

            if (extracted.isNullOrBlank()) {
                System.err.println("✗ Current branch '${currentBranch}' does not contain a valid task ID")
                System.err.println("Usage: git merge [<subtask-id>] [--close-parent]")
                exitProcess(1)
            }
            extracted
        }

        // Validate subtask is in "Code Review" status
        val statusResult = client.getTaskStatus(subtaskId)
        when (statusResult) {
            is TaskSearchResult.Found -> {
                val status = statusResult.status
                val normalizedStatus = statusResult.normalizedStatus()
                val internalAllowed = status.internal == InternalStatus.REVIEW
                val legacyAllowed = normalizedStatus.contains("CODE_REVIEW") || normalizedStatus.contains("REVIEW")
                if (!internalAllowed && !legacyAllowed) {
                    System.err.println("✗ Subtask ${subtaskId} must be in 'Code Review' status")
                    System.err.println("  Current status: ${statusResult.displayStatus()}")
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

                // Check if we should close the parent
                if (closeParent) {
                    val taskDetails = client.adapter.getTaskDetails(subtaskId)
                    when (taskDetails) {
                        is TaskDetailsResult.Success -> {
                            taskDetails.parentKey?.let { parentKey ->
                                println("\nChecking parent story ${parentKey}...")
                                // TODO: Check if parent has other open subtasks
                                // For now, we'll just transition it
                                when (val parentTransition = client.adapter.transitionTask(parentKey, "Done")) {
                                    is TaskTransitionResult.Success -> {
                                        println("✓ Parent story ${parentKey} → Closed")
                                    }
                                    is TaskTransitionResult.Failure -> {
                                        System.err.println("⚠ Failed to close parent story: ${parentTransition.message}")
                                    }
                                }
                            } ?: run {
                                System.err.println("⚠ No parent story found for ${subtaskId}")
                            }
                        }
                        is TaskDetailsResult.Failure -> {
                            System.err.println("⚠ Could not fetch task details to close parent: ${taskDetails.message}")
                        }
                    }
                }
            }

            is TaskTransitionResult.Failure -> {
                System.err.println("⚠ PR merged but failed to transition subtask: ${transitionResult.message}")
            }
        }
    }

    private fun generateBranchName(
        taskId: String,
        config: Config
    ): String {
        // Simple fallback when task details not available
        return "${taskId}/dev"
    }

    private fun generateBranchNameFromTitle(
        taskId: String,
        title: String,
        config: Config
    ): String {
        // Normalize title to branch-safe format
        // TODO: Make this configurable via config.branchNameNormalizer
        val normalized = title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(50) // Limit length

        return if (normalized.isNotEmpty()) {
            "${taskId}/${normalized}"
        } else {
            "${taskId}/dev"
        }
    }

    private fun buildPullRequestBody(
        details: TaskDetailsResult.Success,
        client: ExternalClient<*>
    ): String {
        val sections = mutableListOf<String>()

        // Add Jira task details section
        sections.add(
            """
            <details>
            <summary>📋 Jira Task Details</summary>

            **Task:** ${details.key}
            **Summary:** ${details.summary}

            ${details.description.ifBlank { "_No description provided_" }}
            </details>
        """.trimIndent()
        )

        // Add related PRs section (sibling subtasks)
        details.parentKey?.let { parentKey ->
            val relatedPrs = findRelatedPullRequests(parentKey, details.key, client)
            if (relatedPrs.isNotEmpty()) {
                sections.add(
                    """
                    <details>
                    <summary>🔗 Related Pull Requests</summary>

                    ${relatedPrs.joinToString("\n")}
                    </details>
                """.trimIndent()
                )
            }
        }

        return sections.joinToString("\n\n")
    }

    private fun findRelatedPullRequests(
        parentKey: String,
        currentKey: String,
        client: ExternalClient<*>
    ): List<String> {
        // This would query Jira for sibling subtasks and find their associated PRs
        // For now, return empty list as this requires more complex JQL queries
        Environment.debug { "Finding related PRs for parent ${parentKey}, excluding ${currentKey}" }
        return emptyList()
    }

    fun executeWork(
        args: List<String>,
        config: Config,
        workDir: File,
        env: Map<String, String>,
        client: ExternalClient<*>
    ) {
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

        // Get task status
        val statusResult = client.getTaskStatus(subtaskId)
        val statusDisplay = when (statusResult) {
            is TaskSearchResult.Found -> statusResult.displayStatus()
            else -> "Unknown"
        }

        // Get task details
        val taskDetails = client.adapter.getTaskDetails(subtaskId)
        when (taskDetails) {
            is TaskDetailsResult.Success -> {
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("Work Log for ${taskDetails.key}")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("\nTask: ${taskDetails.summary}")
                println("Status: ${statusDisplay}")
                taskDetails.parentKey?.let { println("Parent: ${it}") }
                println()

                // Show recent commits
                println("Recent Commits:")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val logResult = CommandExecutor.capture(
                    cmd = listOf("git", "log", "--oneline", "-10"),
                    workDir = workDir,
                    env = env
                )

                if (logResult.exitCode == 0 && logResult.stdout.isNotBlank()) {
                    println(logResult.stdout)
                } else {
                    println("No commits found")
                }

                // Show current branch status
                println("\nBranch Status:")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val statusResult = CommandExecutor.capture(
                    cmd = listOf("git", "status", "--short"),
                    workDir = workDir,
                    env = env
                )

                if (statusResult.exitCode == 0) {
                    if (statusResult.stdout.isNotBlank()) {
                        println(statusResult.stdout)
                    } else {
                        println("Working directory clean")
                    }
                }

                // Check for associated PR
                println("\nPull Request:")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val prResult = CommandExecutor.capture(
                    cmd = listOf("gh", "pr", "view", "--json", "url,state,title"),
                    workDir = workDir,
                    env = env
                )

                if (prResult.exitCode == 0 && prResult.stdout.isNotBlank()) {
                    println(prResult.stdout)
                } else {
                    println("No pull request found for this branch")
                }
            }

            is TaskDetailsResult.Failure -> {
                System.err.println("✗ Failed to fetch task details: ${taskDetails.message}")
                exitProcess(1)
            }
        }
    }

    private fun TaskSearchResult.Found.displayStatus(): String =
        status.raw ?: status.internal?.name ?: "UNKNOWN"

    private fun TaskSearchResult.Found.normalizedStatus(): String =
        displayStatus().replace(" ", "_").uppercase()
}
