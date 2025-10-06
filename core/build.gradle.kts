plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    api("io.ktor:ktor-client-core:2.3.12")
    api("io.ktor:ktor-client-cio:2.3.12")
    api("io.ktor:ktor-client-content-negotiation:2.3.12")
    api("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
}
