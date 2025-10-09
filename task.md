# Goal API surface for git interceptor 

The result should be a client, than when some configuration is applied, will allow it to intercept Git commands and 
perform custom logic before and after their execution.

This should result in many of the existing flow in git being unchanged, while allowing for custom logic to be applied.

For example, I want the below command to be intercepted:

```shell
git commit -m "Initial commit"
```

Where the interceptor would check:
- The branch name matches the configured pattern.
- The ticket status is allowed for commits.
- The ticket status is allowed for pushes.

The interceptor should return a non-zero exit code if any of these checks fail, preventing the Git command from executing.
This behavior should be configurable through the interceptor's configuration, with options to warn instead of blocking.

There should also be the ability to apply some modifications to the Git command before it is executed,
such as adding or removing arguments.

For example, if the interceptor detects that the Git command is a commit, it could automatically add the ticket key to the commit message if it's missing.

Ie. If the Git command is `git commit -m "Initial commit"`, the interceptor could modify it to `git commit -m "KAP-123: Initial commit"`.

Much of the core fucntionality for this exists today, but it's wired incorrectly.

Wire it correctly, and ensure that the interceptor can be configured to apply these modifications based on the Git command being executed.

When there's no existing `git` command corresponding to the intercepted Git command, we want to make sure not to require any `kapture` command prefix to be expected.

That is we want to expose the  following commands.


## Commands

### `git subtask <parent-ticket> <subtask-title>` - Create a subtask under a parent story

Given KAP-123 is the parent story we want to create a subtask under, we'd expect the following to work:

```shell
git subtask KAP-123 "Subtask title"
```

This should validate that the parent story is valid (exists, and passes the ticket policy), create a subtask under it, and transition it to the `In Progress` status.

We want to enable configuration options to specify inheritance rules for subtasks, such as copying labels or assigning them to a specific assignee.


### `git subtask <subtask-ticket>` - Transition a subtask to In Progress

```shell
git subtask KAP-456
```

This should validate that the subtask exists and is in an allowed state, and transition it to the `In Progress` status.

### `git review <optional-title>?` - Transition a pull request to Code Review

```shell
git review KAP-789
```

This should create a pull request for the specified ticket and transition it to the `Code Review` status.
If a title is provided, it will be used for the pull request; otherwise, the default title from the ticket will be used.

We should also allow for configuration options to specify integration agnostic rules for pull requests,
such as copying labels or assigning them to a specific reviewer.

Keep the configuration options flexible enough to support different integrations and workflows, with room for future expansion.

### `git merge <subtask-ticket> --close-parent=<boolean, default=false>` - Merge a pull request and transition the subtask to Closed

This should merge the pull request for the specified ticket and transition the subtask to the `Closed` status.
If `--close-parent` is set to `true`, it will also transition the parent story to the `Closed` status if there are no other open subtasks.


### `git work` - Show the current work log and activity snapshots

This should display a summary of the current checked out subtasks work log, including recent commits, pull requests, and subtasks.

This command should also allow for filtering by date range and other criteria, and provide options to export the work log to a file or share it with others.
