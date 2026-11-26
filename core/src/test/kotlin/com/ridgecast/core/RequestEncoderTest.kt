package com.ridgecast.core

import com.ridgecast.core.request.encodeRequest
import com.ridgecast.core.request.InvalidRequestException
import com.ridgecast.core.request.normalizeTrackerName
import com.ridgecast.core.request.Request
import com.ridgecast.core.request.RequestField
import com.ridgecast.core.request.RequestOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

/** Encoder behavior the shared JSON fixtures can't express. Spec cases live in shared/fixtures. */
class RequestEncoderTest {

    private val base = Request(latitude = 44.0512, longitude = -123.0921)

    @Test
    fun `output does not depend on the device locale`() {
        val saved = Locale.getDefault()
        try {
            for (locale in listOf(Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("hi-IN-u-nu-deva"))) {
                Locale.setDefault(locale)
                val request = base.copy(
                    options = setOf(RequestOption.TRACK_ALERTS, RequestOption.WIND_GUST, RequestOption.SNOW_LEVEL),
                    trackerHours = 168,
                    forecastDays = 3,
                    trackerName = "Tahoe",
                )
                assertEquals("RC1 44.0512 -123.0921 0C01 168 3 Tahoe", encodeRequest(request), "locale $locale")
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `each option sets only its own bit`() {
        for (option in RequestOption.entries) {
            val request = base.copy(options = setOf(option), trackerHours = 24, trackerName = "Camp")
            val flags = encodeRequest(request).split(" ")[3]
            assertEquals(String.format(Locale.US, "%04X", 1 shl option.bit), flags, option.name)
        }
    }

    @Test
    fun `options use bits 0 to 13 once each`() {
        assertEquals((0..13).toList(), RequestOption.entries.map { it.bit }.sorted())
        assertEquals(RequestOption.entries.size, RequestOption.entries.map { it.fieldName }.toSet().size)
        assertEquals(0x3FFF, base.copy(options = RequestOption.entries.toSet()).flagBits)
    }

    @Test
    fun `only alerts, avalanche and daily forecast create trackers`() {
        assertEquals(
            setOf(RequestOption.TRACK_ALERTS, RequestOption.TRACK_AVY, RequestOption.DAILY_FORECAST),
            RequestOption.entries.filter { it.isTracker }.toSet(),
        )
    }

    @Test
    fun `coordinates that are not numbers are rejected`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(
                RequestField.LATITUDE,
                assertThrows<InvalidRequestException> { encodeRequest(base.copy(latitude = value)) }.field,
            )
            assertEquals(
                RequestField.LONGITUDE,
                assertThrows<InvalidRequestException> { encodeRequest(base.copy(longitude = value)) }.field,
            )
        }
    }

    @Test
    fun `tabs and newlines in a name become single spaces`() {
        assertEquals("Mt Hood", normalizeTrackerName("\tMt\n Hood \r\n"))
    }
}
