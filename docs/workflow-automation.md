# Workflow Automation

Kapture now includes a complete Jira workflow automation system that orchestrates the full development cycle from subtask creation through branch merge.

## Overview

The workflow automation implements the following flow:

1. **Subtask Creation** - Create a subtask under a parent story with automatic status validation
2. **Branch Creation** - Create a git branch and transition the subtask to "In Progress"
3. **Pull Request Creation** - Create a GitHub PR with Jira details and transition to "Code Review"
4. **Branch Merge** - Merge the PR and transition the subtask to "Closed"

## Prerequisites

- Kapture configured with `jira-cli` integration (REST adapter not fully supported for workflow operations)
- GitHub CLI (`gh`) installed and authenticated
- Jira credentials configured (via `JIRA_API_TOKEN` environment variable)

## Commands

### 1. Create a Subtask

```bash
git kapture subtask <PARENT-ID> [subtask-title]
```

**Behavior:**
- Validates that parent issue exists and is in "Ready for Dev" or "In Progress" status
- Auto-transitions parent to "In Progress" if currently "Ready for Dev"
- Creates a new subtask linked to the parent
- Returns the newly created subtask key

**Example:**
```bash
$ git kapture subtask PROJ-123 "Implement user authentication"
Creating subtask under parent PROJ-123...
✓ Created subtask: PROJ-124
  Parent: PROJ-123
```

**Exit Codes:**
- `0` - Success
- `1` - Validation failure or creation error

---

### 2. Create a Branch

```bash
git kapture branch <SUBTASK-ID>
```

**Behavior:**
- Validates that subtask exists and is in "Ready for Dev" status
- Creates a new git branch following the configured `branchPattern`
- Transitions the subtask to "In Progress"

**Example:**
```bash
$ git kapture branch PROJ-124
Creating branch: PROJ-124/dev
✓ Branch created: PROJ-124/dev
✓ Subtask PROJ-124 → In Progress
```

**Exit Codes:**
- `0` - Success
- `1` - Validation failure, branch creation error, or transition failure

---

### 3. Create a Pull Request

```bash
git kapture pr
```

**Behavior:**
- Extracts subtask ID from current branch name (using `branchPattern`)
- Validates that subtask is in "In Progress" status
- Fetches issue details from Jira (summary, description)
- Pushes current branch to remote
- Creates GitHub PR with:
  - Title from Jira issue summary
  - Collapsible Jira ticket details section
  - Collapsible related PRs section (sibling subtasks)
- Transitions subtask to "Code Review"

**Example:**
```bash
$ git kapture pr
Pushing branch to remote...
Creating pull request...
Transitioning PROJ-124 to 'Code Review'...
✓ Pull request created
✓ Subtask PROJ-124 → Code Review

https://github.com/org/repo/pull/42
```

**Pull Request Body Format:**
```markdown
<details>
<summary>📋 Jira Ticket Details</summary>

**Ticket:** PROJ-124
**Summary:** Implement user authentication

_Full issue description from Jira_
</details>

<details>
<summary>🔗 Related Pull Requests</summary>

- #40 - PROJ-125: Add login UI
- #41 - PROJ-126: Update user model
</details>
```

**Exit Codes:**
- `0` - Success
- `1` - Validation failure, push error, PR creation error, or transition failure

---

### 4. Merge Pull Request

```bash
git kapture merge
```

**Behavior:**
- Extracts subtask ID from current branch name
- Validates that subtask is in "Code Review" status
- Merges the pull request using GitHub CLI (auto-merge with squash)
- Transitions subtask to "Done" (closed)

**Example:**
```bash
$ git kapture merge
Merging pull request...
Transitioning PROJ-124 to 'Closed'...
✓ Pull request merged
✓ Subtask PROJ-124 → Closed
```

**Exit Codes:**
- `0` - Success
- `1` - Validation failure, merge error, or transition failure

---

## Configuration

### Branch Pattern

The `branchPattern` in your Kapture config controls how branch names are validated and parsed:

```json
{
  "branchPattern": "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$"
}
```

This pattern expects branches in the format: `TICKET-123/description`

### Status Rules

Configure which Jira statuses are allowed for each workflow operation by customizing the status rules in your config:

```json
{
  "statusRules": {
    "allowCommitWhen": ["IN_PROGRESS", "READY"],
    "allowPushWhen": ["READY", "IN_REVIEW"]
  }
}
```

### Jira CLI Integration

Workflow commands require the `jira-cli` integration type:

```json
{
  "external": {
    "type": "jira_cli",
    "executable": "jira",
    "environment": {
      "JIRA_API_TOKEN": "<your-token>",
      "JIRA_EMAIL": "you@example.com"
    }
  }
}
```

## Complete Workflow Example

```bash
# 1. Create a subtask for the story
$ git kapture subtask STORY-100 "Add password reset feature"
✓ Created subtask: STORY-101

# 2. Create a branch and start work
$ git kapture branch STORY-101
✓ Branch created: STORY-101/dev
✓ Subtask STORY-101 → In Progress

# 3. Make changes and commit
$ echo "code" >> src/reset.ts
$ git add .
$ git commit -m "Implement password reset"

# 4. Create pull request
$ git kapture pr
✓ Pull request created
✓ Subtask STORY-101 → Code Review

# 5. After approval, merge the PR
$ git kapture merge
✓ Pull request merged
✓ Subtask STORY-101 → Closed
```

## Status Transitions

The workflow enforces the following status transitions:

```
Parent Story:
  Ready for Dev → In Progress (auto-transition on subtask creation)

Subtask:
  Ready for Dev → In Progress (on branch creation)
  In Progress → Code Review (on PR creation)
  Code Review → Done (on PR merge)
```

## Error Handling

All workflow commands include comprehensive validation:

- **Status Checks**: Ensures issues are in the correct state before proceeding
- **Ticket Existence**: Validates that referenced tickets exist in Jira
- **Branch Name Validation**: Ensures branch names follow the configured pattern
- **Git Operations**: Proper error handling for all git/GitHub operations

Error messages include:
- ✗ marks for failures
- ✓ marks for successes
- ⚠ marks for partial successes (e.g., PR created but transition failed)

## Limitations

- REST adapter does not currently support workflow operations (use `jira-cli` adapter)
- Related PRs feature requires additional JQL query implementation
- Status names must match your Jira workflow configuration exactly
- GitHub CLI must be authenticated and configured for the repository

## Architecture

Workflow commands are implemented in:

- `cli/src/main/kotlin/io/amichne/kapture/cli/WorkflowCommands.kt` - Command orchestration
- `core/src/main/kotlin/io/amichne/kapture/core/http/adapter/Adapter.kt` - Interface definition
- `core/src/main/kotlin/io/amichne/kapture/core/http/adapter/JiraCliAdapter.kt` - Jira CLI integration

New adapter methods:
- `createSubtask(parentId, title)` - Creates a subtask
- `transitionIssue(issueId, targetStatus)` - Transitions issue status
- `getIssueDetails(issueId)` - Fetches full issue details
