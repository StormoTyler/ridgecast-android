package com.ridgecast.core.request

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/*
 * Encodes a Request as a version 1 RidgeCast SMS request, as defined in docs/sms-format.md in
 * ridgecast-server.
 *
 * The encoder applies the spec's fixed forms, so the same choices always produce the same message
 * on Android and iOS:
 * - Coordinates are rounded half up to 4 decimal places and always written with 4.
 * - Without a tracker option, the duration is sent as 0 and no name is sent.
 * - With NO_FORECAST, the forecast length is sent as 1.
 * - The tracker name is trimmed and runs of spaces become one space.
 *
 * A request that would still be rejected by the server, or that is over the apps' own limits,
 * throws InvalidRequestException. The UI should validate first and show the user what to fix;
 * this is the last line of defense.
 */

const val REQUEST_VERSION = 1
const val REQUEST_PREFIX = "RC$REQUEST_VERSION"

const val MAX_TRACKER_HOURS = 168

// the Android and iOS apps send at most 3 days; the server accepts up to 7, and a user can edit
// the number in the message by hand
const val MAX_FORECAST_DAYS = 3
const val MAX_TRACKER_NAME_LENGTH = 10

private const val COORDINATE_DECIMALS = 4
private val trackerNameChars = Regex("[A-Za-z0-9 '.#&()/-]+")
private val whitespaceRun = Regex("\\s+")

/** The request field that could not be encoded. Names match the shared fixtures. */
enum class RequestField(val fieldName: String) {
    LATITUDE("latitude"),
    LONGITUDE("longitude"),
    TRACKER_HOURS("tracker_hours"),
    FORECAST_DAYS("forecast_days"),
    TRACKER_NAME("tracker_name"),
}

class InvalidRequestException(val field: RequestField, message: String) :
    IllegalArgumentException(message)

/** Returns the SMS message for [request], or throws [InvalidRequestException]. */
fun encodeRequest(request: Request): String =
    listOfNotNull(
        REQUEST_PREFIX,
        encodeCoordinate(request.latitude, 90, RequestField.LATITUDE),
        encodeCoordinate(request.longitude, 180, RequestField.LONGITUDE),
        String.format(Locale.US, "%04X", request.flagBits),
        encodeTrackerHours(request).toString(),
        encodeForecastDays(request).toString(),
        encodeTrackerName(request),
    ).joinToString(" ")

/** Trims [name] and turns each run of whitespace into one space, as the server reads it. */
fun normalizeTrackerName(name: String): String = name.trim().replace(whitespaceRun, " ")

private fun encodeCoordinate(value: Double, limit: Int, field: RequestField): String {
    if (!value.isFinite()) {
        throw InvalidRequestException(field, "${field.fieldName} is not a number")
    }
    // BigDecimal.valueOf uses the shortest decimal form of the double, so 44.05125 rounds to
    // 44.0513 as a person would expect, and the output never depends on the device locale.
    val rounded = BigDecimal.valueOf(value).setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP)
    if (rounded.abs() > BigDecimal(limit)) {
        throw InvalidRequestException(field, "${field.fieldName} must be from -$limit to $limit")
    }
    // a value that rounds to zero is written without a sign
    return if (rounded.signum() == 0) rounded.abs().toPlainString() else rounded.toPlainString()
}

private fun encodeTrackerHours(request: Request): Int = when {
    !request.createsTracker -> 0
    request.trackerHours in 1..MAX_TRACKER_HOURS -> request.trackerHours
    else -> throw InvalidRequestException(
        RequestField.TRACKER_HOURS,
        "tracker_hours must be from 1 to $MAX_TRACKER_HOURS when a tracker option is on",
    )
}

private fun encodeForecastDays(request: Request): Int = when {
    RequestOption.NO_FORECAST in request.options -> 1
    request.forecastDays in 1..MAX_FORECAST_DAYS -> request.forecastDays
    else -> throw InvalidRequestException(
        RequestField.FORECAST_DAYS,
        "forecast_days must be from 1 to $MAX_FORECAST_DAYS",
    )
}

/** The name to send, or null when the request creates no tracker. */
private fun encodeTrackerName(request: Request): String? {
    if (!request.createsTracker) return null
    val name = normalizeTrackerName(request.trackerName.orEmpty())
    val problem = when {
        name.isEmpty() -> "tracker_name is required when a tracker option is on"
        name.length > MAX_TRACKER_NAME_LENGTH ->
            "tracker_name must be at most $MAX_TRACKER_NAME_LENGTH characters"
        !trackerNameChars.matches(name) -> "tracker_name contains a character that is not allowed"
        else -> null
    }
    return if (problem == null) name else throw InvalidRequestException(RequestField.TRACKER_NAME, problem)
}
