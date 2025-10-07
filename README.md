# Kapture

Kapture is a minimally intrusive Jira workflow orchestrator, driven by `git`, automating task management as you navigate
the development cycle, from created to closed.

### Git under the hood

Kapture is a Kotlin-based Git wrapper that inserts policy, status, and telemetry interceptors in front of the real `git`
executable. It resolves the genuine Git binary on each commandInvocation, evaluates configurable rules (branch naming,
task status, etc.), and finally forwards the original command to Git.

Kapture provides a robust manner to apply consistent governance regarding task management,
across entire organizations, without disrupting workflows or adding developer overhead.

### Highlights

- Branch name policy checks with configurable warning/blocking behaviour
- Remote task status lookups before `commit`/`push` via the pluggable `ExternalClient`
- Git event tracking to record events (_when `trackingEnabled` is on_)
- Shadow JAR distribution *and* GraalVM native image build, for zero-dependency installs
- Extensible interceptor pipeline allow registering custom logic without disturbing existing rules

## Quick Start

1. **Install Kapture** using the provided `scripts/install.sh` or download the latest release assets (`kapture.jar`,
   `kapture-linux-x64`).
2. **Configure credentials** by enabling the `jiraCli` adapter in your Kapture config and authenticating the GitHub
   CLI (`gh auth login`).
3. **Bootstrap workflow** in a feature repository:
   ```bash
   git kapture subtask PROJ-123 "Describe the work"
   git kapture branch PROJ-456
   # make changes, commit
   git kapture review
   git kapture merge
   ```

These commands create a Jira subtask, spin up a Git branch, open a GitHub pull request, and close the subtask once
merged.

## Overview

The workflow automation implements the following flow:

1. **Subtask Creation** - Create a subtask under a parent story with automatic status validation
2. **Branch Creation** - Create a git branch and transition the subtask to "In Progress"
3. **Pull Request Creation** - Create a GitHub PR with Jira details and transition to "Code Review"
4. **Branch Merge** - Merge the PR and transition the subtask to "Closed"

## Prerequisites

- Kapture configured with `jira-cli` integration
- GitHub CLI (`gh`) installed and authenticated
- Jira credentials configured (via `JIRA_API_TOKEN` environment variable)

> Refer to the [Configuration](./docs/configuration.md) documentation for detailed configuration information.

## Commands

### 1. Create a Subtask

**Usage**

```bash
git kapture subtask <PARENT-ID> [subtask-title]
```

**Inputs**

- `PARENT-ID`: Required Jira task key for the parent story/epic.
- `subtask-title`: Optional summary for the new subtask. Defaults to Jira template if omitted.

**Outputs**

- Standard out: Progress log with created subtask key.
- Exit code `0`: Subtask created successfully.
- Exit code `1`: Validation failure (missing parent, disallowed status) or Jira CLI error.

**Example (success)**

```bash
$ git kapture subtask PROJ-123 "Implement user authentication"
Creating subtask under parent PROJ-123...
✓ Created subtask: PROJ-124
  Parent: PROJ-123
```

**Example (failure)**

```bash
$ git kapture subtask PROJ-999
Creating subtask under parent PROJ-999...
✗ Failed to create subtask: Parent task not found
```

---

### 2. Create a Branch

**Usage**

```bash
git kapture branch <SUBTASK-ID>
```

**Inputs**

- `SUBTASK-ID`: Required Jira subtask key. Must match the configured branch pattern and exist in Jira.

**Outputs**

- Standard out: Branch name and transition status.
- Git branch: Created locally via `git checkout -b` following your `branchPattern`.
- Exit code `0`: Branch created and Jira task transitioned to "In Progress".
- Exit code `1`: Subtask missing, status disallowed, git branch command failed, or Jira transition error.

**Example (success)**

```bash
$ git kapture branch PROJ-124
Creating branch: PROJ-124/dev
✓ Branch created: PROJ-124/dev
✓ Subtask PROJ-124 → In Progress
```

**Example (failure)**

```bash
$ git kapture branch PROJ-555
Creating branch: PROJ-555/dev
✗ Subtask PROJ-555 must be in 'Ready for Dev' status
  Current status: Done
```

---

### 3. Create a Review

**Usage**

```bash
git kapture review
```

**Inputs**

- Current git branch: Must contain a task key matching `branchPattern`.
- Git working tree: Must be clean enough for `git push` and `gh pr create` to succeed.
- Environment: GitHub CLI authenticated; Jira credentials valid.

**Outputs**

- Standard out: Push status, PR creation status, Jira transition logs, PR URL.
- Exit code `0`: Branch pushed, PR opened, subtask transitioned to "Code Review".
- Exit code `1`: Task extraction failure, status invalid, push/PR creation error, or Jira transition error.

**Example (success)**

```bash
$ git kapture review
Pushing branch to remote...
Creating pull request...
Transitioning PROJ-124 to 'Code Review'...
✓ Pull request created
✓ Subtask PROJ-124 → Code Review

https://github.com/org/repo/pull/42
```

**Example (failure)**

```bash
$ git kapture review
✗ Current branch 'main' does not contain a valid task ID
  Expected pattern: ^(?<task>[A-Z]+-\d+)/[a-z0-9._-]+$
```

**Pull Request Body Template**

```markdown
<details>
<summary>📋 Jira Task Details</summary>

**Task:** PROJ-124
**Summary:** Implement user authentication

_Full task description from Jira_
</details>

<details>
<summary>🔗 Related Pull Requests</summary>

- #40 - PROJ-125: Add login UI
- #41 - PROJ-126: Update user model
</details>
```

---

### 4. Merge Pull Request

**Usage**

```bash
git kapture merge
```

**Inputs**

- Current git branch: Must still reference the feature branch that opened the PR.
- GitHub CLI: Must have an open PR associated with the branch.
- Jira: Subtask must be in "Code Review" status.

**Outputs**

- Standard out: Merge status and Jira transition result.
- Exit code `0`: PR merged (squash + auto) and subtask transitioned to "Done".
- Exit code `1`: Task missing/invalid, merge command failure, or Jira transition failure.

**Example (success)**

```bash
$ git kapture merge
Merging pull request...
Transitioning PROJ-124 to 'Closed'...
✓ Pull request merged
✓ Subtask PROJ-124 → Closed
```

**Example (failure)**

```bash
$ git kapture merge
Merging pull request...
✗ Failed to merge pull request: pull request not found for branch PROJ-124/dev
```

## Complete Workflow Example

<details>

<summary>Expand</summary>

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
$ git kapture review
✓ Pull request created
✓ Subtask STORY-101 → Code Review

# 5. After approval, merge the PR
$ git kapture merge
✓ Pull request merged
✓ Subtask STORY-101 → Closed
```

</details>

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

- **Status Checks**: Ensures tasks are in the correct state before proceeding
- **Task Existence**: Validates that referenced tasks exist in Jira
- **Branch Name Validation**: Ensures branch names follow the configured pattern
- **Git Operations**: Proper error handling for all git/GitHub operations

Error messages include:

- ✗ marks for failures
- ✓ marks for successes
- ⚠ marks for partial successes (e.g., PR created but transition failed)

## Architecture

Workflow commands are implemented in:

- [Command orchestration](./cli/src/main/kotlin/io/amichne/kapture/cli/WorkflowCommands.kt)
- [Interface definition](./core/src/main/kotlin/io/amichne/kapture/core/http/adapter/Adapter.kt)
- [Jira CLI integration](./core/src/main/kotlin/io/amichne/kapture/core/http/adapter/JiraCliAdapter.kt)

## Upcoming functionality

- Status names updated to be parsed via configuration and mapped to semantic category
- REST adapter integration completion, to allow dynamic workflow operations per event
- Related PRs referencing via JQL query implementation
