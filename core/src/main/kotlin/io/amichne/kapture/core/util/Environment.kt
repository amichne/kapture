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

    fun full(): Map<String, String> = System.getenv().toMap()

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

    val debugEnabled: Boolean
        get() = System.getenv("KAPTURE_DEBUG") == "1"

    fun debug(message: () -> String) {
        if (debugEnabled) {
            System.err.println("[kapture] ${message()}")
        }
    }
}
