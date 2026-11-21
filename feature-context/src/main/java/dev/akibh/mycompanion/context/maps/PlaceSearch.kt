package dev.akibh.mycompanion.context.maps

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place as GooglePlace
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** One search suggestion: "Bashundhara City" / "Panthapath, Dhaka". */
data class PlaceSuggestion(val id: String, val primary: String, val secondary: String)

/** Finding places by name for the place editor; behind an interface so the editor can be tested. */
interface PlaceSearch {
    /** False without a Maps API key: the editor hides search and says why. */
    val isAvailable: Boolean

    suspend fun suggest(query: String, near: Pair<Double, Double>?): List<PlaceSuggestion>

    /** Latitude and longitude of a suggestion; ends the billing session for this search. */
    suspend fun locate(id: String): Pair<Double, Double>?
}

/** The Maps API key from the app manifest (MAPS_API_KEY in local.properties), or null if not set. */
object MapsKey {
    fun of(context: Context): String? = runCatching {
        context.packageManager
            .getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
            .metaData?.getString(META_DATA)
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("\${") }

    private const val META_DATA = "com.google.android.geo.API_KEY"
}

/**
 * Google Places autocomplete (Places API (New)). Suggestions and the chosen place's location share
 * one session token, which Google bills as a single search.
 */
@Singleton
class GooglePlaceSearch @Inject constructor(@ApplicationContext private val context: Context) : PlaceSearch {
    private val key: String? by lazy { MapsKey.of(context) }
    override val isAvailable: Boolean get() = key != null

    private val client: PlacesClient by lazy {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context, requireNotNull(key))
        Places.createClient(context)
    }
    private var session: AutocompleteSessionToken? = null

    override suspend fun suggest(query: String, near: Pair<Double, Double>?): List<PlaceSuggestion> {
        if (!isAvailable) return emptyList()
        val token = session ?: AutocompleteSessionToken.newInstance().also { session = it }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .setLocationBias(bias(near))
            .build()
        return client.findAutocompletePredictions(request).await().autocompletePredictions.map {
            PlaceSuggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
        }
    }

    override suspend fun locate(id: String): Pair<Double, Double>? {
        if (!isAvailable) return null
        val request = FetchPlaceRequest.builder(id, listOf(GooglePlace.Field.LOCATION))
            .apply { session?.let(::setSessionToken) }
            .build()
        session = null
        val location = client.fetchPlace(request).await().place.location ?: return null
        return location.latitude to location.longitude
    }

    /** Around the pin when there is one, otherwise Bangladesh. A bias, not a limit: other places still match. */
    private fun bias(near: Pair<Double, Double>?): RectangularBounds = if (near != null) {
        val (lat, lon) = near
        RectangularBounds.newInstance(LatLng(lat - SPAN, lon - SPAN), LatLng(lat + SPAN, lon + SPAN))
    } else {
        RectangularBounds.newInstance(LatLng(20.6, 88.0), LatLng(26.7, 92.7))
    }

    private companion object {
        /** About 50 km. */
        const val SPAN = 0.45
    }
}
