package io.amichne.kapture.test

import io.amichne.kapture.core.model.task.InternalStatus
import io.amichne.kapture.core.model.task.TaskSearchResult
import io.amichne.kapture.core.model.task.TaskStatus

fun taskStatus(
    raw: String?,
    internal: InternalStatus? = null,
    provider: String = "test",
    key: String = "TEST-123"
): TaskStatus = TaskStatus(
    provider = provider,
    key = key,
    raw = raw,
    internal = internal
)

fun foundStatus(
    raw: String?,
    internal: InternalStatus? = null,
    provider: String = "test",
    key: String = "TEST-123"
): TaskSearchResult.Found = TaskSearchResult.Found(
    taskStatus(raw, internal, provider, key)
)
