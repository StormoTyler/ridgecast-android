import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    // parses the time zone data; the tests also use it to read the shared fixtures
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// the fixtures and time zone data shared with the iOS app
val shared = rootProject.layout.projectDirectory.dir("shared")

tasks.test {
    useJUnitPlatform()
    // rerun the tests when the shared files change, not only when the Kotlin does
    inputs.dir(shared.dir("fixtures")).withPropertyName("sharedFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(shared.dir("timezones")).withPropertyName("sharedTimeZones").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ridgecast.shared", shared.asFile.absolutePath)
}
