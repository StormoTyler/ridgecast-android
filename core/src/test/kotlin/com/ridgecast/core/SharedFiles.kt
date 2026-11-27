package com.ridgecast.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** A file in the shared/ folder, which holds the fixtures and data shared with the iOS app. */
fun sharedFile(path: String): File {
    val dir = System.getProperty("ridgecast.shared")
        ?: error("ridgecast.shared is not set; run the tests through Gradle")
    return File(dir, path)
}

/** The cases of a fixture file in shared/fixtures. */
fun fixtureCases(fileName: String): List<JsonObject> =
    Json.parseToJsonElement(sharedFile("fixtures/$fileName").readText())
        .jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
