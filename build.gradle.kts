import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    // Kotlin multiplatform plugin provides the ability to target multiple native
    // platforms from a single codebase.  Version 2.0.0 matches the plan
    // specification【835536755365059†L316-L323】.
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"

}

// Define common repositories.  Ktor and kotlinx‑serialization live on
// Maven Central.  Gradle will download these dependencies when the
// project is built.  Using explicit repositories avoids implicit
// injection of additional repositories.
repositories {
    mavenCentral()
}

// Configure the multiplatform targets and dependencies.  This project
// produces three native executables (linuxX64, macosX64 and macosArm64)
// as requested【835536755365059†L316-L323】.  An optional jvm target may be added
// for local debugging, but is omitted here for brevity.
kotlin {
    linuxX64("linux") {
        binaries {
            executable {
                // The fully qualified name of the entry point.  See
                // src/commonMain/kotlin/Main.kt for the actual function.
                entryPoint = "io.gira.cli.main"
            }
        }
    }
    macosX64("macosX64") {
        binaries {
            executable {
                entryPoint = "io.gira.cli.main"
            }
        }
    }
    macosArm64("macosArm64") {
        binaries {
            executable {
                entryPoint = "io.gira.cli.main"
            }
        }
    }

    // Optional: define a JVM target for debugging or unit tests.  It is not
    // required by the specification but can be handy during development.
    jvm("jvm")

    // Define the common and platform specific source sets.  The native
    // targets depend on the "nativeMain" set which contains the native
    // implementations of the Exec and Platform expect objects.  The
    // jvmMain set is provided for completeness but not fully implemented.
    sourceSets {
        val commonMain by getting {
            dependencies {
                // JSON serialization support【835536755365059†L316-L331】
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                // Core Ktor client for HTTP calls【835536755365059†L316-L331】
                implementation("io.ktor:ktor-client-core:2.3.12")
                // Content negotiation and JSON conversion【835536755365059†L316-L331】
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                // Kotlin coroutines for asynchronous HTTP calls and other
                // concurrency needs.  Version aligns with Kotlin 2.0.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.0")
            }
        }
        val nativeMain by creating {
            dependencies {
                // Use the curl engine on native platforms【835536755365059†L316-L335】.
                implementation("io.ktor:ktor-client-curl:2.3.12")
            }
        }
        val linuxMain by getting {
            dependsOn(nativeMain)
        }
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }
        val jvmMain by getting {
            // For the JVM, use CIO; note that this target is optional and
            // primarily intended for local testing.  We still depend on
            // nativeMain because the default HTTP engine is abstracted.
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.12")
            }
        }
    }
}
