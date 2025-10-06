plugins {
    application
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":interceptors"))
}

application {
    mainClass.set("io.amichne.kapture.cli.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("kapture")
    archiveClassifier.set("")
    minimize()
}
