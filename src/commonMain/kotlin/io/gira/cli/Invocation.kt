package io.gira.cli

/**
 * Represents a single invocation of the git wrapper.  It captures
 * the raw arguments provided by the user, the first token (command)
 * in lower case (if any) and the resolved path to the real git
 * binary.  Interceptors use [Invocation] to decide whether they
 * should trigger on this command【835536755365059†L148-L173】.
 */
data class Invocation(
    val args: List<String>,
    val command: String?,
    val realGit: String
)
