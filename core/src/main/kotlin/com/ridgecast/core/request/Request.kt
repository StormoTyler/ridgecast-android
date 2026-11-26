package com.ridgecast.core.request

/**
 * A forecast request as the user set it up, before it is encoded.
 *
 * It holds the user's choices as they are, so the form can keep a tracker name or duration while
 * the tracker toggles are off. [encodeRequest] decides what is actually sent.
 */
data class Request(
    val latitude: Double,
    val longitude: Double,
    val options: Set<RequestOption> = emptySet(),
    /** Hours a created tracker stays active. Only sent when a tracker option is on. */
    val trackerHours: Int = 0,
    /** Days of forecast in the reply, counting today. Only sent when [RequestOption.NO_FORECAST] is off. */
    val forecastDays: Int = 1,
    /** Name shown in the tracker's messages. Only sent when a tracker option is on. */
    val trackerName: String? = null,
) {
    /** True when the request creates a tracker: alerts, avalanche or daily forecast. */
    val createsTracker: Boolean get() = options.any { it.isTracker }

    /** The options as the 16-bit flags value. */
    val flagBits: Int get() = options.fold(0) { bits, option -> bits or option.mask }
}
