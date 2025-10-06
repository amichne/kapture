package io.amichne.kapture.core.util

object Environment {
    private val passthroughKeys = setOf(
        "PAGER",
        "LESS",
        "EDITOR",
        "VISUAL",
        "SSH_ASKPASS",
        "GPG_TTY"
    )

    /**
     * Returns a snapshot of the complete process environment map, suitable for
     * passing to subprocesses that should observe the user's current settings.
     */
    fun full(): Map<String, String> = System.getenv().toMap()

    /**
     * Filters the environment down to Git-related variables and common
     * terminal helpers so that interceptor-spawned commands behave like the
     * user's interactive Git sessions.
     */
    fun passthrough(): Map<String, String> {
        val env = full()
        val passthrough = HashMap<String, String>()
        env.forEach { (key, value) ->
            if (key.startsWith("GIT_") || key in passthroughKeys) {
                passthrough[key] = value
            }
        }
        return passthrough
    }

    /** Indicates whether verbose logging should be emitted based on `KAPTURE_DEBUG`. */
    val debugEnabled: Boolean
        get() = System.getenv("KAPTURE_DEBUG") == "1"

    /**
     * Writes a lazily-evaluated diagnostic message to stderr when debug mode
     * is active; otherwise the lambda is never invoked.
     */
    fun debug(message: () -> String) {
        if (debugEnabled) {
            System.err.println("[kapture] ${message()}")
        }
    }
}
