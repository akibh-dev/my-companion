package dev.akibh.mycompanion.context.maps

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import dev.akibh.mycompanion.context.domain.Place

/**
 * Google Map for picking a place (D21): tap or long-press drops the pin, the radius is drawn around
 * it and the other saved places are shown in grey. The camera follows the pin when it's set from
 * outside the map (search, current location, typed coordinates) and lands off screen.
 */
@Composable
fun MapPicker(
    pin: Pair<Double, Double>?,
    radiusMeters: Int,
    others: List<Place>,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasKey = remember { MapsKey.of(context) != null }
    if (!hasKey) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "The map needs a Google Maps API key: add MAPS_API_KEY to local.properties and rebuild " +
                    "(Docs/20-google-maps.md). Until then, use your location or paste coordinates.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    val start = pin ?: others.firstOrNull()?.let { it.latitude to it.longitude } ?: DHAKA
    val zoom = if (pin != null || others.isNotEmpty()) 16f else 12f
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(start.first, start.second), zoom)
    }
    LaunchedEffect(pin) {
        val target = pin?.let { LatLng(it.first, it.second) } ?: return@LaunchedEffect
        val visible = camera.projection?.visibleRegion?.latLngBounds
        if (visible == null || !visible.contains(target)) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(target, maxOf(camera.position.zoom, 16f)))
        }
    }
    val canShowLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val accent = MaterialTheme.colorScheme.primary
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = canShowLocation),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = canShowLocation,
        ),
        onMapClick = { onPick(it.latitude, it.longitude) },
        onMapLongClick = { onPick(it.latitude, it.longitude) },
    ) {
        others.forEach { place ->
            Circle(
                center = LatLng(place.latitude, place.longitude),
                radius = place.radiusMeters.toDouble(),
                strokeColor = Color.Gray,
                strokeWidth = 3f,
                fillColor = Color.Gray.copy(alpha = 0.15f),
            )
        }
        pin?.let { (lat, lon) ->
            val point = LatLng(lat, lon)
            key(point) { Marker(state = rememberMarkerState(position = point)) }
            Circle(
                center = point,
                radius = radiusMeters.toDouble(),
                strokeColor = accent,
                strokeWidth = 4f,
                fillColor = accent.copy(alpha = 0.18f),
            )
        }
    }
}

/** Where the map opens with no pin and no saved places. */
private val DHAKA = 23.8103 to 90.4125
