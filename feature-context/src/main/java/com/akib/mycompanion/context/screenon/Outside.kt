package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.context.domain.Place

/**
 * "Outside" for keep-screen-on (FR-3.10): not at any saved place and no saved place's Wi-Fi
 * network in range. A saved Wi-Fi in range counts as being there even when location says otherwise,
 * because indoors location is often off by more than a place's radius.
 */
fun isOutside(insidePlaces: Set<Long>, wifiInRange: Boolean): Boolean = insidePlaces.isEmpty() && !wifiInRange

/** True when a network seen nearby (scanned or connected) is one saved with any place. */
fun savedWifiInRange(places: List<Place>, seen: Set<String>): Boolean {
    if (seen.isEmpty()) return false
    return places.any { place -> place.wifiNames.any { name -> name.isNotBlank() && name in seen } }
}

/** Places whose circle holds the given position. */
fun placesAt(places: List<Place>, latitude: Double, longitude: Double): Set<Long> =
    places.filter { it.contains(latitude, longitude) }.map { it.id }.toSet()
