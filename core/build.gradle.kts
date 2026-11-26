import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // reads the shared JSON fixtures; tests only, so the app does not ship it
    testImplementation(libs.kotlinx.serialization.json)
}

// the request fixtures shared with the iOS app
val sharedFixtures = rootProject.layout.projectDirectory.dir("shared/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(sharedFixtures).withPropertyName("sharedFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ridgecast.fixtures", sharedFixtures.asFile.absolutePath)
}
