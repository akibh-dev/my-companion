package com.akib.mycompanion.context.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.data.RuleRun
import com.akib.mycompanion.context.domain.Geo
import com.akib.mycompanion.context.domain.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlacesViewModel @Inject constructor(repository: ContextRepository) : ViewModel() {
    val places: StateFlow<List<Place>> = repository.places.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val runs: StateFlow<List<RuleRun>> = repository.runs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
}

/** Saved places (plan §11, "places list"). */
@Composable
fun PlacesRoute(
    onOpenPlace: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (places.isEmpty()) item { Text("No saved places yet. Add home or the office to use them in rules.") }
            items(places, key = { it.id }) { place ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        modifier = Modifier.clickable { onOpenPlace(place.id) },
                        headlineContent = { Text(place.name) },
                        supportingContent = {
                            val wifi = place.wifiNames.takeIf { it.isNotEmpty() }
                                ?.let { " · Wi-Fi ${it.joinToString()}" }
                            Text(
                                "%.5f, %.5f · %d m".format(place.latitude, place.longitude, place.radiusMeters) +
                                    wifi.orEmpty()
                            )
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onOpenPlace(null) },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add place") },
            text = { Text("Add place") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

/** What the rules changed and when (plan §11, "history"). */
@Composable
fun RuleHistoryRoute(modifier: Modifier = Modifier, viewModel: PlacesViewModel = hiltViewModel()) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (runs.isEmpty()) item { Text("Nothing yet. Changes appear here when a rule starts or ends.") }
        items(runs) { run ->
            ListItem(
                headlineContent = { Text(run.ruleName) },
                supportingContent = { Text(run.changes) },
                overlineContent = { Text(format.format(Date(run.time))) },
            )
        }
    }
}

data class PlaceDraft(
    val id: Long = 0,
    val name: String = "",
    val coordinates: String = "",
    val radius: Int = Place.DEFAULT_RADIUS,
    val wifiNames: String = "",
    val message: String? = null,
) {
    val point: Pair<Double, Double>? get() = Geo.parse(coordinates)

    fun toPlace(): Place? {
        val (lat, lon) = point ?: return null
        val place =
            Place(id, name.trim(), lat, lon, radius, wifiNames.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        return place.takeIf { it.isValid() }
    }
}

@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ContextRepository,
) : ViewModel() {
    private val placeId: Long? = savedState.get<String>("id")?.toLongOrNull()
    private val mutableDraft = MutableStateFlow(PlaceDraft())
    val draft: StateFlow<PlaceDraft> = mutableDraft.asStateFlow()
    val places: StateFlow<List<Place>> = repository.places.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val isNew get() = placeId == null

    init {
        placeId?.let { id ->
            viewModelScope.launch {
                repository.place(id)?.let {
                    mutableDraft.value = PlaceDraft(
                        id = it.id,
                        name = it.name,
                        coordinates = format(it.latitude, it.longitude),
                        radius = it.radiusMeters,
                        wifiNames = it.wifiNames.joinToString(", "),
                    )
                }
            }
        }
    }

    fun onName(name: String) = mutableDraft.update { it.copy(name = name) }

    fun onCoordinates(text: String) = mutableDraft.update { it.copy(coordinates = text, message = null) }

    fun onPick(lat: Double, lon: Double) = onCoordinates(format(lat, lon))

    fun onRadius(radius: Int) = mutableDraft.update { it.copy(radius = radius) }

    fun onWifi(text: String) = mutableDraft.update { it.copy(wifiNames = text) }

    fun onMessage(text: String) = mutableDraft.update { it.copy(message = text) }

    fun save(onDone: () -> Unit) {
        val place = mutableDraft.value.toPlace() ?: return
        viewModelScope.launch {
            repository.save(place)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            placeId?.let { repository.deletePlace(it) }
            onDone()
        }
    }

    private fun format(lat: Double, lon: Double) = "%.6f, %.6f".format(lat, lon)
}

/** Place editor: the offline map, your current location or typed coordinates, and a radius (FR-3.1). */
@Composable
fun PlaceEditorRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceEditorViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            currentLocation(
                context,
                viewModel
            )
        } else {
            viewModel.onMessage("Location permission is needed for this")
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(draft.name, viewModel::onName, label = {
                Text("Name (e.g. Home, Office)")
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            MapPicker(
                pin = draft.point,
                radiusMeters = draft.radius,
                others = places.filter { it.id != draft.id },
                onPick = viewModel::onPick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }
        item {
            OutlinedTextField(
                draft.coordinates,
                viewModel::onCoordinates,
                label = { Text("Latitude, longitude") },
                supportingText = {
                    Text(
                        draft.message ?: when {
                            draft.coordinates.isNotBlank() && draft.point == null -> "e.g. 23.8103, 90.4125"
                            else -> "Tap the map, use your location, or paste coordinates"
                        }
                    )
                },
                isError = draft.coordinates.isNotBlank() && draft.point == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedButton(onClick = {
                val permission = Manifest.permission.ACCESS_FINE_LOCATION
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    currentLocation(context, viewModel)
                } else {
                    askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) { Text("Use my current location") }
        }
        item {
            Text("Radius: ${draft.radius} m", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = draft.radius.toFloat(),
                onValueChange = { viewModel.onRadius((it / 10).roundToInt() * 10) },
                valueRange = Place.MIN_RADIUS.toFloat()..Place.MAX_RADIUS.toFloat(),
            )
        }
        item {
            OutlinedTextField(
                draft.wifiNames,
                viewModel::onWifi,
                label = { Text("Wi-Fi networks here (optional, comma separated)") },
                supportingText = { Text("While one is in range you count as here, even with location off") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.save(onDone) }, enabled = draft.toPlace() != null) { Text("Save") }
                if (!viewModel.isNew) OutlinedButton(onClick = { viewModel.delete(onDone) }) { Text("Delete") }
            }
        }
    }
}

@SuppressLint("MissingPermission") // asked for just before
private fun currentLocation(context: Context, viewModel: PlaceEditorViewModel) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = if (manager.isProviderEnabled(
            LocationManager.GPS_PROVIDER
        )
    ) {
        LocationManager.GPS_PROVIDER
    } else {
        LocationManager.NETWORK_PROVIDER
    }
    viewModel.onMessage("Finding your location…")
    manager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { location ->
        if (location != null) {
            viewModel.onPick(
                location.latitude,
                location.longitude
            )
        } else {
            viewModel.onMessage("Couldn't get a location fix. Try outdoors.")
        }
    }
}
