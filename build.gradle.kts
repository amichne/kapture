plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.gradleup.shadow") version "9.2.2"
}

allprojects {
    repositories {
        mavenCentral()
    }
}


subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")


    dependencies {
        testImplementation(kotlin("test"))
    }

    kotlin {
        jvmToolchain(21)
    }


    tasks.test {
        useJUnitPlatform()
    }
}
