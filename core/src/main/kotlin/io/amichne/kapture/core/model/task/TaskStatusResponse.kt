package io.amichne.kapture.core.model.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("TaskStatusResponse")
data class TaskStatusResponse(val status: String)
