pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Provisiona o JDK da toolchain automaticamente — a JVM instalada na máquina
    // só precisa existir pro Gradle subir; a versão que compila é a do toolchain.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "order-processing"

include(":order")
include(":notifications")
