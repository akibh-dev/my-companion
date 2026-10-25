package com.akib.mycompanion.context.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A saved place (FR-3.1). [wifiNames] are networks seen there: while one is in range you count as
 * inside even if location is off, which also defines "outside" for keep-screen-on (FR-3.10).
 */
data class Place(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS,
    val wifiNames: List<String> = emptyList(),
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        Geo.distanceMeters(this.latitude, this.longitude, latitude, longitude) <= radiusMeters

    fun isValid(): Boolean = name.isNotBlank() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        radiusMeters in MIN_RADIUS..MAX_RADIUS

    companion object {
        const val DEFAULT_RADIUS = 150
        const val MIN_RADIUS = 50
        const val MAX_RADIUS = 2_000
    }
}

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle (haversine) distance. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    /** "23.8103, 90.4125" or "23.8103 90.4125" (as copied from a map app). */
    fun parse(text: String): Pair<Double, Double>? {
        val parts = text.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
    }
}
