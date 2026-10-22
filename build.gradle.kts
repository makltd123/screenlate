buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships built-in Kotlin pinned to an older Kotlin Gradle plugin; this lifts it to the catalog version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
