package com.ridgecast.core.timezone

import java.time.ZoneId

/**
 * The US time zone the location is in, or null if it is in none, which means it is outside NWS
 * coverage. Also null for coordinates that are not numbers.
 *
 * The boundaries are simplified to about 110 m, so a location that close to a zone border, or to
 * the edge of territorial waters, may get the zone next to it. The apps only warn on the result.
 */
fun timeZoneAt(data: TimeZoneData, latitude: Double, longitude: Double): ZoneId? =
    data.zones.firstOrNull { zone -> zone.polygons.any { it.contains(longitude, latitude) } }?.id

/**
 * Whether the point is inside the polygon and not in one of its holes.
 *
 * The bounding box check skips most polygons cheaply. After it, a ray from the point going east
 * crosses the polygon's rings an odd number of times exactly when the point is inside: holes are
 * handled by counting their crossings too.
 */
internal fun ZonePolygon.contains(lon: Double, lat: Double): Boolean =
    lon in minLon..maxLon && lat in minLat..maxLat &&
        rings.sumOf { crossings(it, lon, lat) } % 2 == 1

/** How many edges of the ring a ray from (lon, lat) going east crosses. */
internal fun crossings(ring: DoubleArray, lon: Double, lat: Double): Int {
    val points = ring.size / 2
    return (0 until points).count { i ->
        // the edge from the previous point to this one; for point 0 that is the last point,
        // since the ring has no closing point
        val previous = (i + points - 1) % points
        crosses(ring[2 * previous], ring[2 * previous + 1], ring[2 * i], ring[2 * i + 1], lon, lat)
    }
}

/**
 * Whether the ray from (lon, lat) going east crosses the edge (x1, y1)-(x2, y2).
 *
 * The edge must straddle the ray's latitude, counting an end exactly on it as above, so a ray
 * through a vertex counts it once, not twice. Then the ray crosses if the edge is east of the point
 * at that latitude. The division is safe: straddling means y1 != y2.
 */
private fun crosses(x1: Double, y1: Double, x2: Double, y2: Double, lon: Double, lat: Double): Boolean =
    (y1 > lat) != (y2 > lat) && lon < x1 + (lat - y1) * (x2 - x1) / (y2 - y1)
