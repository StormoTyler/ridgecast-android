package com.ridgecast.core.timezone

import java.time.ZoneId

/** What the app knows about the time zone of a request's location. */
sealed interface LocationZone {
    /** The location is in this US time zone. */
    data class InZone(val zoneId: ZoneId) : LocationZone

    /** The location is in no US time zone, so it is outside NWS coverage. */
    data object OutsideCoverage : LocationZone

    /** The time zone data is not loaded yet, or failed to load, so the app cannot tell. */
    data object Unknown : LocationZone
}

/** The zone of the location from [timeZoneAt]'s answer: in that zone, or outside coverage if none. */
fun locationZoneOf(zoneId: ZoneId?): LocationZone =
    zoneId?.let(LocationZone::InZone) ?: LocationZone.OutsideCoverage
