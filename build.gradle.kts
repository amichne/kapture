plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
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
