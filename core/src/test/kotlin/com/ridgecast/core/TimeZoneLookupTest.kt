package com.ridgecast.core

import com.ridgecast.core.timezone.TimeZoneData
import com.ridgecast.core.timezone.parseTimeZoneData
import com.ridgecast.core.timezone.timeZoneAt
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId

class TimeZoneLookupTest {

    // shared/fixtures/timezones.json against shared/timezones/us-timezones.json, as the iOS app runs it

    @TestFactory
    fun `known places are in their time zone`(): List<DynamicTest> =
        fixtureCases("timezones.json").map { case ->
            DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) {
                val expected = case.getValue("zone").jsonPrimitive.contentOrNull?.let(ZoneId::of)
                val latitude = case.getValue("latitude").jsonPrimitive.double
                val longitude = case.getValue("longitude").jsonPrimitive.double
                assertEquals(expected, timeZoneAt(usTimeZones, latitude, longitude))
            }
        }

    @Test
    fun `every zone in the data has a known place`() {
        val known = fixtureCases("timezones.json").mapNotNull { it.getValue("zone").jsonPrimitive.contentOrNull }.toSet()
        val inData = Regex(""""id":"([^"]+)"""").findAll(usTimeZonesText).map { it.groupValues[1] }.toSet()
        assertEquals(34, inData.size)
        assertEquals(emptySet<String>(), inData - known, "zones with no known place in shared/fixtures/timezones.json")
    }

    // The ray casting, on small shapes that are easy to check by hand

    @Test
    fun `a point inside the outer ring is in the zone`() {
        assertEquals(ZONE_A, timeZoneAt(squareWithHole, latitude = 1.0, longitude = 1.0))
    }

    @Test
    fun `a point in a hole is in no zone`() {
        assertNull(timeZoneAt(squareWithHole, latitude = 5.0, longitude = 5.0))
    }

    @Test
    fun `a point outside the bounding box is in no zone`() {
        assertNull(timeZoneAt(squareWithHole, latitude = 5.0, longitude = 11.0))
    }

    @Test
    fun `a point in the notch of a concave polygon is in no zone`() {
        // inside the bounding box of the L shape, but in its missing corner
        assertNull(timeZoneAt(lShape, latitude = 8.0, longitude = 8.0))
        assertEquals(ZONE_A, timeZoneAt(lShape, latitude = 8.0, longitude = 2.0))
        assertEquals(ZONE_A, timeZoneAt(lShape, latitude = 2.0, longitude = 8.0))
    }

    @Test
    fun `a ray through a vertex counts it once`() {
        // the ray east from (lon 1, lat 5) runs exactly through the diamond's east vertex (10, 5)
        val diamond = data(zone(ZONE_A_ID, ring(5, 0, 10, 5, 5, 10, 0, 5)))
        assertEquals(ZONE_A, timeZoneAt(diamond, latitude = 5.0, longitude = 1.0))
    }

    @Test
    fun `later polygons of a zone and later zones are checked`() {
        val twoZones = data(
            zone(ZONE_A_ID, ring(0, 0, 1, 0, 1, 1, 0, 1), ring(20, 20, 21, 20, 21, 21, 20, 21)),
            zone(ZONE_B_ID, ring(10, 10, 11, 10, 11, 11, 10, 11)),
        )
        assertEquals(ZONE_A, timeZoneAt(twoZones, latitude = 20.5, longitude = 20.5))
        assertEquals(ZONE_B, timeZoneAt(twoZones, latitude = 10.5, longitude = 10.5))
    }

    @Test
    fun `coordinates that are not numbers are in no zone`() {
        assertNull(timeZoneAt(squareWithHole, latitude = Double.NaN, longitude = 1.0))
        assertNull(timeZoneAt(squareWithHole, latitude = 1.0, longitude = Double.NaN))
    }

    // Parsing

    @Test
    fun `an unsupported format version is rejected`() {
        val text = file(zone(ZONE_A_ID, ring(0, 0, 1, 0, 1, 1))).replace("\"format_version\":1", "\"format_version\":2")
        assertThrows<IllegalArgumentException> { parseTimeZoneData(text) }
    }

    @Test
    fun `an unknown zone id is rejected`() {
        assertThrows<IllegalArgumentException> { parseTimeZoneData(file(zone("Test/NoSuchZone", ring(0, 0, 1, 0, 1, 1)))) }
    }

    @Test
    fun `a ring with an odd number of values or fewer than 3 points is rejected`() {
        assertThrows<IllegalArgumentException> { parseTimeZoneData(file(zone(ZONE_A_ID, "[0,0,1,0,1]"))) }
        assertThrows<IllegalArgumentException> { parseTimeZoneData(file(zone(ZONE_A_ID, "[0,0,1,0]"))) }
    }

    @Test
    fun `text that is not time zone data is rejected`() {
        assertThrows<IllegalArgumentException> { parseTimeZoneData("not json") }
        assertThrows<IllegalArgumentException> { parseTimeZoneData("{\"format_version\":1}") }
    }

    private companion object {
        // Labels for the made-up shapes below. They have to be real time zone ids, since the parser
        // rejects unknown ones, so these are fixed-offset ids that name no place. The offsets are
        // never used: the lookup only returns the label of the shape containing the point.
        const val ZONE_A_ID = "Etc/GMT+1"
        const val ZONE_B_ID = "Etc/GMT+2"
        val ZONE_A: ZoneId = ZoneId.of(ZONE_A_ID)
        val ZONE_B: ZoneId = ZoneId.of(ZONE_B_ID)

        val usTimeZonesText: String by lazy { sharedFile("timezones/us-timezones.json").readText() }
        val usTimeZones: TimeZoneData by lazy { parseTimeZoneData(usTimeZonesText) }

        // a 10 by 10 square with a 2 by 2 hole in the middle, drawn the other way round
        val squareWithHole = data(zone(ZONE_A_ID, ring(0, 0, 10, 0, 10, 10, 0, 10) + "," + ring(4, 4, 4, 6, 6, 6, 6, 4)))

        // a 10 by 10 square missing its 5 by 5 north-east corner
        val lShape = data(zone(ZONE_A_ID, ring(0, 0, 10, 0, 10, 5, 5, 5, 5, 10, 0, 10)))

        fun data(vararg zones: String): TimeZoneData = parseTimeZoneData(file(*zones))

        fun file(vararg zones: String): String =
            """{"format_version":1,"zones":[${zones.joinToString(",")}]}"""

        /** A zone with one polygon per ring list, its bounding box worked out from its rings. */
        fun zone(id: String, vararg polygons: String): String {
            val entries = polygons.map { rings ->
                val values = Regex("-?[0-9.]+").findAll(rings).map { it.value.toDouble() }.toList()
                val lons = values.filterIndexed { i, _ -> i % 2 == 0 }
                val lats = values.filterIndexed { i, _ -> i % 2 == 1 }
                val bbox = listOf(lons.min(), lats.min(), lons.max(), lats.max()).joinToString(",")
                """{"bbox":[$bbox],"rings":[$rings]}"""
            }
            return """{"id":"$id","polygons":[${entries.joinToString(",")}]}"""
        }

        /** A ring as JSON, from lon, lat pairs. */
        fun ring(vararg values: Int): String = values.joinToString(",", "[", "]")
    }
}
