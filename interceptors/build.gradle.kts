plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}


dependencies {
    implementation(project(":core"))
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}
