plugins {
    application
    id("com.gradleup.shadow")
    id("org.graalvm.buildtools.native")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":interceptors"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("io.amichne.kapture.cli.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("kapture")
    archiveClassifier.set("")
    minimize()
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }

    binaries {
        named("main") {
            imageName.set("kapture")
            mainClass.set("io.amichne.kapture.cli.MainKt")
            useFatJar.set(true)
            resources.autodetect()
            buildArgs.add("--initialize-at-build-time")
            buildArgs.add("--enable-http")
            buildArgs.add("--enable-url-protocols=https")
            buildArgs.add("--report-unsupported-elements-at-runtime")
            buildArgs.add("--no-fallback")
            buildArgs.add("--no-server")

        }
    }
}
