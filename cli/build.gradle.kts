plugins {
    application
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

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }

    binaries {
        named("main") {
            useFatJar.set(true)
            resources.autodetect()
            imageName.set("kapture")
            mainClass.set("io.amichne.kapture.cli.MainKt")
            buildArgs.addAll(
                listOf(
                    "--initialize-at-build-time",
                    "--enable-http",
                    "--enable-url-protocols=https",
                    "--report-unsupported-elements-at-runtime",
                    "--no-fallback",
                    "--no-server",
                    "--strict-image-heap",
                    "-R:MaxHeapSize=512m",
                    "-march=native",
                )
            )
        }
    }
}
