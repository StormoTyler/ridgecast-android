package com.ridgecast.core

import com.ridgecast.core.request.nextDailyForecast
import com.ridgecast.core.timezone.LocationZone
import com.ridgecast.core.timezone.locationZoneOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

// The warning rules themselves are in shared/fixtures/warnings.json, run by SharedFixtureTest.
class RequestWarningsTest {

    @Test
    fun `the next daily forecast is 6 AM on the location's clock on both sides of a clock change`() {
        // the evening before Denver springs forward: 6 AM the next morning is daylight time
        assertEquals(at("2026-03-08T06:00:00-06:00"), nextDailyForecast(at("2026-03-07T18:00:00-07:00"), DENVER))
        // the evening before Denver falls back: 6 AM the next morning is standard time
        assertEquals(at("2026-11-01T06:00:00-07:00"), nextDailyForecast(at("2026-10-31T18:00:00-06:00"), DENVER))
    }

    @Test
    fun `the next daily forecast is today's until 6 AM, then tomorrow's`() {
        assertEquals(at("2026-06-15T06:00:00-06:00"), nextDailyForecast(at("2026-06-15T05:59:59-06:00"), DENVER))
        assertEquals(at("2026-06-16T06:00:00-06:00"), nextDailyForecast(at("2026-06-15T06:00:00-06:00"), DENVER))
    }

    @Test
    fun `a lookup with no zone means outside coverage`() {
        assertEquals(LocationZone.InZone(DENVER), locationZoneOf(DENVER))
        assertEquals(LocationZone.OutsideCoverage, locationZoneOf(null))
    }

    private companion object {
        val DENVER: ZoneId = ZoneId.of("America/Denver")

        fun at(text: String): Instant = OffsetDateTime.parse(text).toInstant()
    }
}
