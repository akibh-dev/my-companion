package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.context.domain.Place
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutsideTest {

    private val home = Place(
        id = 1,
        name = "Home",
        latitude = 23.8103,
        longitude = 90.4125,
        wifiNames = listOf("HomeNet"),
    )
    private val office = Place(
        id = 2,
        name = "Office",
        latitude = 23.7500,
        longitude = 90.3900,
        wifiNames = listOf(""),
    )

    @Test
    fun `outside only when at no saved place and no saved Wi-Fi is in range`() {
        assertThat(isOutside(insidePlaces = emptySet(), wifiInRange = false)).isTrue()
        assertThat(isOutside(insidePlaces = setOf(1L), wifiInRange = false)).isFalse()
        assertThat(isOutside(insidePlaces = emptySet(), wifiInRange = true)).isFalse()
        assertThat(isOutside(insidePlaces = setOf(1L, 2L), wifiInRange = true)).isFalse()
    }

    @Test
    fun `a saved place's Wi-Fi seen nearby counts as in range`() {
        assertThat(savedWifiInRange(listOf(home, office), setOf("CafeWifi", "HomeNet"))).isTrue()
        assertThat(savedWifiInRange(listOf(home, office), setOf("CafeWifi"))).isFalse()
        assertThat(savedWifiInRange(listOf(home, office), emptySet())).isFalse()
        assertThat(savedWifiInRange(emptyList(), setOf("HomeNet"))).isFalse()
    }

    @Test
    fun `blank saved names never match`() {
        assertThat(savedWifiInRange(listOf(office), setOf(""))).isFalse()
    }

    @Test
    fun `places around a position`() {
        assertThat(placesAt(listOf(home, office), 23.8104, 90.4126)).containsExactly(1L)
        assertThat(placesAt(listOf(home, office), 24.0, 91.0)).isEmpty()
    }
}
