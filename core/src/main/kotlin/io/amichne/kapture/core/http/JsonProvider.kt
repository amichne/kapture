package io.amichne.kapture.core.http

import kotlinx.serialization.json.Json
import java.util.Base64

object JsonProvider {
    val defaultJson = Json { ignoreUnknownKeys = true }

    fun encodeBasic(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}
