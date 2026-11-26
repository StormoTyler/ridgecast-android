package com.ridgecast.core.request

/**
 * The 14 on/off options packed into the flags field of a request.
 *
 * [bit] is the bit position from docs/sms-format.md in ridgecast-server (bit 0 is the least
 * significant). [fieldName] is the matching `ForecastVars` field in the server's `src/models.rs`,
 * which the shared test fixtures also use, so all three codebases name the options the same way.
 */
enum class RequestOption(val bit: Int, val fieldName: String, val isTracker: Boolean = false) {
    TRACK_ALERTS(0, "track_alerts", isTracker = true),
    NO_FORECAST(1, "no_forecast"),
    TRACK_AVY(2, "track_avy", isTracker = true),
    ELEVATION(3, "elevation"),
    WET_BULB_GLOBE_TEMPERATURE(4, "wet_bulb_globe_temperature"),
    HEAT_RISK(5, "heat_risk"),
    RELATIVE_HUMIDITY(6, "relative_humidity"),
    VISIBILITY(7, "visibility"),
    CEILING_HEIGHT(8, "ceiling_height"),
    PRESSURE(9, "pressure"),
    SNOW_LEVEL(10, "snow_level"),
    WIND_GUST(11, "wind_gust"),
    BRIEF(12, "brief"),
    DAILY_FORECAST(13, "daily_forecast", isTracker = true),
    ;

    val mask: Int get() = 1 shl bit

    companion object {
        /** The forecast properties that [NO_FORECAST] makes meaningless. */
        val forecastProperties: Set<RequestOption> = setOf(
            ELEVATION, WET_BULB_GLOBE_TEMPERATURE, HEAT_RISK, RELATIVE_HUMIDITY, VISIBILITY,
            CEILING_HEIGHT, PRESSURE, SNOW_LEVEL, WIND_GUST,
        )

        private val byFieldName = entries.associateBy { it.fieldName }

        fun fromFieldName(name: String): RequestOption? = byFieldName[name]
    }
}
