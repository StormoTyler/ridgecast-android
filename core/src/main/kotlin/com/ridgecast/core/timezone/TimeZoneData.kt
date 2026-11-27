package com.ridgecast.core.timezone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.ZoneId

/** The version of shared/timezones/us-timezones.json that [parseTimeZoneData] reads. */
const val TIME_ZONE_DATA_FORMAT_VERSION = 1

/**
 * The US time zone boundaries from shared/timezones/us-timezones.json, ready for [timeZoneAt].
 *
 * Parse it once with [parseTimeZoneData] and keep it: parsing the 1.2 MB file is the slow part,
 * and each lookup after that is fast.
 */
class TimeZoneData internal constructor(internal val zones: List<Zone>)

internal class Zone(val id: ZoneId, val polygons: List<ZonePolygon>)

/**
 * One polygon of a zone. Each ring is a flat [lon, lat, lon, lat, ...] array without the closing
 * point; the first ring is the outer boundary and the rest are holes.
 */
internal class ZonePolygon(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val rings: List<DoubleArray>,
)

/**
 * Parses the text of shared/timezones/us-timezones.json.
 *
 * @throws IllegalArgumentException if the text is not valid time zone data in format version
 *   [TIME_ZONE_DATA_FORMAT_VERSION], or names a zone this device's time zone database lacks
 */
fun parseTimeZoneData(json: String): TimeZoneData {
    val file = format.decodeFromString<FileJson>(json)
    require(file.formatVersion == TIME_ZONE_DATA_FORMAT_VERSION) {
        "unsupported time zone data format version ${file.formatVersion}"
    }
    return TimeZoneData(file.zones.map(::toZone))
}

private val format = Json { ignoreUnknownKeys = true }

// The file's JSON shape. The compiler plugin generates the parsing code for these classes.
@Serializable
private class FileJson(@SerialName("format_version") val formatVersion: Int, val zones: List<ZoneJson>)

@Serializable
private class ZoneJson(val id: String, val polygons: List<PolygonJson>)

@Serializable
private class PolygonJson(val bbox: DoubleArray, val rings: List<DoubleArray>)

private fun toZone(zone: ZoneJson): Zone =
    Zone(toZoneId(zone.id), zone.polygons.map { toPolygon(zone.id, it) })

private fun toZoneId(id: String): ZoneId =
    try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("unknown time zone id $id", e)
    }

private fun toPolygon(zoneId: String, polygon: PolygonJson): ZonePolygon {
    require(polygon.bbox.size == 4) { "$zoneId: bbox must have 4 values" }
    require(polygon.rings.isNotEmpty()) { "$zoneId: polygon has no rings" }
    require(polygon.rings.all { it.size >= 6 && it.size % 2 == 0 }) {
        "$zoneId: each ring must be at least 3 points, as lon, lat pairs"
    }
    val (minLon, minLat, maxLon, maxLat) = polygon.bbox
    return ZonePolygon(minLon, minLat, maxLon, maxLat, polygon.rings)
}
