package com.ridgecast.core.request

import com.ridgecast.core.timezone.LocationZone
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Something about a request the user may want to know before sending it. Warnings never stop a
 * request from being sent.
 */
enum class RequestWarning {
    /** The daily forecast tracker may end before the next 6 AM forecast at the location. */
    DAILY_FORECAST_MAY_MISS,

    /** The location is outside NWS coverage, so the server may have no forecast for it. */
    OUTSIDE_COVERAGE,
}

/** The local time at the location when the server sends daily forecasts. */
val DAILY_FORECAST_TIME: LocalTime = LocalTime.of(6, 0)

/**
 * The warnings for a request sent at [now] from a location in [zone].
 *
 * The checks treat [now] as when the tracker starts. The app cannot know when the request will
 * actually reach the server: a message sent without service can wait hours before it goes out. The
 * server replies when the tracker starts, and that reply is what tells the user its real times.
 *
 * The request is assumed valid: [encodeRequest] rejects the invalid ones. With [LocationZone.Unknown]
 * there are no warnings, since the app cannot tell. Outside coverage, the daily forecast check is
 * skipped, since the location has no US time zone to find 6 AM in.
 */
fun requestWarnings(request: Request, now: Instant, zone: LocationZone): Set<RequestWarning> =
    when (zone) {
        is LocationZone.InZone ->
            if (dailyForecastMayMiss(request, now, zone.zoneId)) setOf(RequestWarning.DAILY_FORECAST_MAY_MISS) else emptySet()
        LocationZone.OutsideCoverage -> setOf(RequestWarning.OUTSIDE_COVERAGE)
        LocationZone.Unknown -> emptySet()
    }

/**
 * The first [DAILY_FORECAST_TIME] in [zone] after [now]. At exactly 6 AM, that is tomorrow's.
 *
 * Daylight saving time is handled: the result is 6 AM on the location's clock, whether that day's
 * offset is standard or daylight time.
 */
fun nextDailyForecast(now: Instant, zone: ZoneId): Instant {
    val sameDay = ZonedDateTime.of(now.atZone(zone).toLocalDate(), DAILY_FORECAST_TIME, zone)
    val forecast = if (sameDay.toInstant() > now) sameDay else sameDay.plusDays(1)
    return forecast.toInstant()
}

/** Whether the request has a daily forecast tracker that ends before [nextDailyForecast]. */
private fun dailyForecastMayMiss(request: Request, now: Instant, zone: ZoneId): Boolean {
    if (RequestOption.DAILY_FORECAST !in request.options) return false
    val trackerEnds = now.plus(Duration.ofHours(request.trackerHours.toLong()))
    return trackerEnds < nextDailyForecast(now, zone)
}
