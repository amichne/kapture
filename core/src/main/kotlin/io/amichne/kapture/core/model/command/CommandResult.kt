package io.amichne.kapture.core.model.command

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
