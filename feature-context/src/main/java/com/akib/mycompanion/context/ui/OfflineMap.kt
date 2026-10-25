package com.akib.mycompanion.context.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akib.mycompanion.context.domain.Place
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The offline map for picking a place (D4, plan §10). It reads a PMTiles file from the phone, so no
 * map tile comes from the internet. The map exists only while this screen is open, then frees its memory.
 */
object OfflineMap {
    /** Copy the file here with `adb push map.pmtiles /sdcard/Android/data/com.akib.mycompanion/files/maps/`. */
    fun file(context: Context): File = File(context.getExternalFilesDir("maps"), "map.pmtiles")

    fun isAvailable(context: Context) = file(context).let { it.exists() && it.length() > 0 }

    /**
     * A plain style: land, water, parks, roads and buildings, no labels (labels would need font files).
     * Layers for both common schemas (Protomaps basemap and OpenMapTiles), so either kind of file works.
     */
    fun style(file: File): String = """
        {"version":8,"sources":{"map":{"type":"vector","url":"pmtiles://file://${file.absolutePath}"}},
         "layers":[
          {"id":"bg","type":"background","paint":{"background-color":"#f2efe9"}},
          {"id":"landuse","type":"fill","source":"map","source-layer":"landuse","paint":{"fill-color":"#dfe8cf","fill-opacity":0.7}},
          {"id":"landcover","type":"fill","source":"map","source-layer":"landcover","paint":{"fill-color":"#d4e4bc","fill-opacity":0.7}},
          {"id":"park","type":"fill","source":"map","source-layer":"park","paint":{"fill-color":"#cde3b5"}},
          {"id":"water","type":"fill","source":"map","source-layer":"water","paint":{"fill-color":"#a9cbe8"}},
          {"id":"roads","type":"line","source":"map","source-layer":"roads","paint":{"line-color":"#ffffff","line-width":2}},
          {"id":"transportation","type":"line","source":"map","source-layer":"transportation","paint":{"line-color":"#ffffff","line-width":2}},
          {"id":"buildings","type":"fill","source":"map","source-layer":"buildings","minzoom":14,"paint":{"fill-color":"#d9d0c9"}},
          {"id":"building","type":"fill","source":"map","source-layer":"building","minzoom":14,"paint":{"fill-color":"#d9d0c9"}}
         ]}
    """.trimIndent()

    /** A circle of [radiusMeters] around a point, as a polygon (flat-earth is fine at this size). */
    fun circle(latitude: Double, longitude: Double, radiusMeters: Int, steps: Int = 64): Polygon {
        val dLat = radiusMeters / METERS_PER_DEGREE
        val dLon = dLat / cos(Math.toRadians(latitude))
        val ring = (0..steps).map { i ->
            val angle = 2 * Math.PI * i / steps
            Point.fromLngLat(longitude + dLon * cos(angle), latitude + dLat * sin(angle))
        }
        return Polygon.fromLngLats(listOf(ring))
    }

    private const val METERS_PER_DEGREE = 111_320.0
}

/** Map with your saved places as circles; tap to drop the pin for the place being edited. */
@Composable
fun MapPicker(
    pin: Pair<Double, Double>?,
    radiusMeters: Int,
    others: List<Place>,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!OfflineMap.isAvailable(context)) {
        Column(modifier) {
            Text("No offline map on this phone yet.", style = MaterialTheme.typography.titleSmall)
            Text(
                "Build a PMTiles file for Bangladesh on your computer (see Docs/offline-map.md) and copy it with:\n" +
                    "adb push map.pmtiles /sdcard/Android/data/${context.packageName}/files/maps/map.pmtiles\n" +
                    "Until then, use your current location or type the coordinates.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    val currentPick by rememberUpdatedState(onPick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val holder = remember { arrayOfNulls<MapLibreMap>(1) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                holder[0] = map
                map.setStyle(Style.Builder().fromJson(OfflineMap.style(OfflineMap.file(context)))) { style ->
                    style.addSource(GeoJsonSource(PLACES))
                    style.addLayer(FillLayer(PLACES, PLACES).withProperties(PropertyFactory.fillColor("#331B8A5A")))
                    style.addSource(GeoJsonSource(PICK))
                    style.addLayer(FillLayer("$PICK-fill", PICK).withProperties(PropertyFactory.fillColor("#551B8A5A")))
                    style.addLayer(LineLayer("$PICK-line", PICK).withProperties(PropertyFactory.lineColor("#1B8A5A")))
                    style.addSource(GeoJsonSource(PIN))
                    style.addLayer(
                        CircleLayer(
                            PIN,
                            PIN
                        ).withProperties(PropertyFactory.circleColor("#1B8A5A"), PropertyFactory.circleRadius(6f))
                    )
                    draw(map, pin, radiusMeters, others)
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(pin?.let { LatLng(it.first, it.second) } ?: DHAKA)
                    .zoom(if (pin != null) 15.0 else 11.0)
                    .build()
                map.addOnMapClickListener { point ->
                    currentPick(point.latitude, point.longitude)
                    true
                }
            }
            mapView
        },
        update = { holder[0]?.let { draw(it, pin, radiusMeters, others) } },
        modifier = modifier,
    )
    Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
}

private fun draw(map: MapLibreMap, pin: Pair<Double, Double>?, radius: Int, others: List<Place>) {
    val style = map.style ?: return
    style.getSourceAs<GeoJsonSource>(PLACES)?.setGeoJson(
        FeatureCollection.fromFeatures(
            others.map {
                Feature.fromGeometry(OfflineMap.circle(it.latitude, it.longitude, it.radiusMeters))
            }
        ),
    )
    style.getSourceAs<GeoJsonSource>(PICK)?.setGeoJson(
        FeatureCollection.fromFeatures(
            listOfNotNull(
                pin?.let {
                    Feature.fromGeometry(OfflineMap.circle(it.first, it.second, radius))
                }
            )
        ),
    )
    style.getSourceAs<GeoJsonSource>(PIN)?.setGeoJson(
        FeatureCollection.fromFeatures(
            listOfNotNull(pin?.let { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)) })
        ),
    )
}

private const val PLACES = "places"
private const val PICK = "pick"
private const val PIN = "pin"
private val DHAKA = LatLng(23.8103, 90.4125)
