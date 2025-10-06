plugins {
    application
    id("com.gradleup.shadow")
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
