package com.ridgecast.core

import com.ridgecast.core.request.encodeRequest
import com.ridgecast.core.request.InvalidRequestException
import com.ridgecast.core.request.Request
import com.ridgecast.core.request.RequestOption
import com.ridgecast.core.request.RequestWarning
import com.ridgecast.core.request.requestWarnings
import com.ridgecast.core.timezone.LocationZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.time.ZoneId

/** Runs the request and warning fixtures in shared/fixtures, which the iOS app runs too. */
class SharedFixtureTest {

    @TestFactory
    fun `valid requests encode to their message`(): List<DynamicTest> =
        cases("requests.json").map { case ->
            DynamicTest.dynamicTest(case.name) {
                val expected = case.json.getValue("message").jsonPrimitive.content
                assertEquals(expected, encodeRequest(case.request))
            }
        }

    @TestFactory
    fun `expected messages match the spec pattern and length`(): List<DynamicTest> =
        cases("requests.json").map { case ->
            DynamicTest.dynamicTest(case.name) {
                val message = case.json.getValue("message").jsonPrimitive.content
                assertTrue(SPEC_PATTERN.matches(message), "does not match the spec pattern: $message")
                assertTrue(message.length <= 44, "longer than 44 characters: $message")
            }
        }

    @TestFactory
    fun `invalid requests are rejected naming their field`(): List<DynamicTest> =
        cases("invalid-requests.json").map { case ->
            DynamicTest.dynamicTest(case.name) {
                val field = case.json.getValue("field").jsonPrimitive.content
                val error = assertThrows<InvalidRequestException> { encodeRequest(case.request) }
                assertEquals(field, error.field.fieldName)
            }
        }

    @TestFactory
    fun `requests give their warnings`(): List<DynamicTest> =
        cases("warnings.json").map { case ->
            DynamicTest.dynamicTest(case.name) {
                val now = OffsetDateTime.parse(case.json.getValue("now").jsonPrimitive.content).toInstant()
                val expected = case.json.getValue("warnings").jsonArray
                    .map { RequestWarning.valueOf(it.jsonPrimitive.content.uppercase()) }.toSet()
                assertEquals(expected, requestWarnings(case.request, now, locationZone(case.json)))
            }
        }

    private class Case(val name: String, val request: Request, val json: JsonObject)

    private fun cases(fileName: String): List<Case> =
        fixtureCases(fileName).map { case ->
            Case(case.getValue("name").jsonPrimitive.content, parseRequest(case.getValue("request").jsonObject), case)
        }

    /** The case's location zone: zone_unknown, else zone, with null meaning outside coverage. */
    private fun locationZone(json: JsonObject): LocationZone {
        if (json["zone_unknown"]?.jsonPrimitive?.boolean == true) return LocationZone.Unknown
        val id = json.getValue("zone").jsonPrimitive.contentOrNull ?: return LocationZone.OutsideCoverage
        return LocationZone.InZone(ZoneId.of(id))
    }

    private fun parseRequest(json: JsonObject) = Request(
        latitude = json.getValue("latitude").jsonPrimitive.double,
        longitude = json.getValue("longitude").jsonPrimitive.double,
        options = json["options"]?.jsonArray.orEmpty().map { element ->
            val name = element.jsonPrimitive.content
            RequestOption.fromFieldName(name) ?: error("unknown option in fixture: $name")
        }.toSet(),
        trackerHours = json["tracker_hours"]?.jsonPrimitive?.int ?: 0,
        forecastDays = json["forecast_days"]?.jsonPrimitive?.int ?: 1,
        trackerName = json["tracker_name"]?.jsonPrimitive?.contentOrNull,
    )

    private companion object {
        // the version 1 pattern from docs/sms-format.md in ridgecast-server
        val SPEC_PATTERN = Regex(
            """^[Rr][Cc]1 +(-?\d{1,2}(\.\d{1,4})?) +(-?\d{1,3}(\.\d{1,4})?) +([0-9A-Fa-f]{4}) +(\d{1,3}) +([1-7])( +([A-Za-z0-9'.#&()/-]+( +[A-Za-z0-9'.#&()/-]+)*))?$""",
        )
    }
}
