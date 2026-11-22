package dev.akibh.mycompanion.context.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.context.data.ContextRepository
import dev.akibh.mycompanion.context.data.RuleRun
import dev.akibh.mycompanion.context.domain.Geo
import dev.akibh.mycompanion.context.domain.Place
import dev.akibh.mycompanion.context.maps.MapPicker
import dev.akibh.mycompanion.context.maps.PlaceSearch
import dev.akibh.mycompanion.context.maps.PlaceSuggestion
import dev.akibh.mycompanion.ui.components.AddButton
import dev.akibh.mycompanion.ui.components.AddButtonClearance
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.PrimaryButton
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlacesViewModel @Inject constructor(repository: ContextRepository) : ViewModel() {
    // null until the database answers, so the empty state doesn't flash on open.
    val places: StateFlow<List<Place>?> = repository.places.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )
    val runs: StateFlow<List<RuleRun>?> = repository.runs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
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
            contentPadding = PaddingValues(PageMargin, PageMargin, PageMargin, AddButtonClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (places?.isEmpty() == true) {
                item {
                    EmptyState(
                        symbol = DotSymbol.SEARCH,
                        title = "No saved places yet",
                        body = "Add home or the office to use them in rules.",
                    )
                }
            }
            items(places.orEmpty(), key = { it.id }) { place ->
                val wifi = place.wifiNames.takeIf { it.isNotEmpty() }?.let { " · Wi-Fi ${it.joinToString()}" }
                OpenRow(
                    title = place.name,
                    summary = "%.5f, %.5f · %d m".format(place.latitude, place.longitude, place.radiusMeters) +
                        wifi.orEmpty(),
                    onClick = { onOpenPlace(place.id) },
                )
            }
        }
        AddButton("Add place", onClick = { onOpenPlace(null) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** A card that opens something: title, a muted line, and a chevron that says "tap me". */
@Composable
internal fun OpenRow(title: String, summary: String, onClick: () -> Unit) {
    SectionCard(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the rules changed and when (plan §11, "history"). */
@Composable
fun RuleHistoryRoute(modifier: Modifier = Modifier, viewModel: PlacesViewModel = hiltViewModel()) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    runs?.let { RuleHistoryScreen(it, modifier) }
}

@Composable
fun RuleHistoryScreen(runs: List<RuleRun>, modifier: Modifier = Modifier) {
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PageMargin),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (runs.isEmpty()) {
            item {
                EmptyState(
                    symbol = DotSymbol.RULES,
                    title = "Nothing yet",
                    body = "Changes appear here when a rule starts or ends.",
                )
            }
        }
        items(runs) { run ->
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Note(format.format(Date(run.time)))
                    Text(run.ruleName, style = MaterialTheme.typography.titleSmall)
                    Text(run.changes, style = MaterialTheme.typography.bodyMedium)
                }
            }
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

    /** Why it can't be saved yet, or null when it can. */
    val problem: String?
        get() = when {
            name.isBlank() -> "Give the place a name"
            point == null -> "Choose where it is: search, tap the map, or enter coordinates"
            toPlace() == null -> "Those coordinates are out of range"
            else -> null
        }

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
    private val search: PlaceSearch,
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

    /** Search by name needs the Maps API key, like the map. */
    val canSearch: Boolean get() = search.isAvailable
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()
    private val mutableResults = MutableStateFlow<SearchState>(SearchState.Idle)
    val results: StateFlow<SearchState> = mutableResults.asStateFlow()

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            mutableQuery.debounce(SEARCH_DELAY_MILLIS).distinctUntilChanged().collectLatest(::runSearch)
        }
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

    fun onQuery(text: String) {
        mutableQuery.value = text
        if (text.trim().length < MIN_QUERY) mutableResults.value = SearchState.Idle
    }

    /** Pins the chosen place, names the draft after it if it has no name yet, and closes the list. */
    fun choose(suggestion: PlaceSuggestion) {
        viewModelScope.launch {
            mutableResults.value = SearchState.Searching
            val point = try {
                search.locate(suggestion.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (point == null) {
                mutableResults.value = SearchState.Failed("Couldn't open that place. Check the internet connection.")
                return@launch
            }
            onPick(point.first, point.second)
            if (mutableDraft.value.name.isBlank()) onName(suggestion.primary)
            mutableQuery.value = ""
            mutableResults.value = SearchState.Idle
        }
    }

    private suspend fun runSearch(text: String) {
        val query = text.trim()
        if (query.length < MIN_QUERY || !search.isAvailable) {
            mutableResults.value = SearchState.Idle
            return
        }
        mutableResults.value = SearchState.Searching
        mutableResults.value = try {
            SearchState.Results(search.suggest(query, mutableDraft.value.point))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SearchState.Failed("Search didn't work. Check the internet connection.")
        }
    }

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

    internal companion object {
        const val SEARCH_DELAY_MILLIS = 350L
        const val MIN_QUERY = 2
    }
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val suggestions: List<PlaceSuggestion>) : SearchState
    data class Failed(val message: String) : SearchState
}

/** Place editor: search, the Google Map, your current location or typed coordinates, and a radius (FR-3.1). */
@Composable
fun PlaceEditorRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceEditorViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
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
    // No red before the user has done anything: problems show after a tap on Save.
    var triedToSave by rememberSaveable { mutableStateOf(false) }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    val nameMissing = draft.name.isBlank() && (triedToSave || nameEdited)
    val coordinatesWrong = draft.point == null && (triedToSave || draft.coordinates.isNotBlank())

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PageMargin),
        verticalArrangement = Arrangement.spacedBy(SectionGap)
    ) {
        item {
            Section("Name and search") {
                OutlinedTextField(
                    draft.name,
                    {
                        nameEdited = true
                        viewModel.onName(it)
                    },
                    label = { Text("Name (e.g. Home, Office)") },
                    isError = nameMissing,
                    supportingText = if (nameMissing) {
                        { Text("Give the place a name") }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (viewModel.canSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        label = { Text("Search for a place") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        supportingText = {
                            when (val state = results) {
                                SearchState.Searching -> Text("Searching…")
                                is SearchState.Failed -> Text(state.message)
                                is SearchState.Results -> if (state.suggestions.isEmpty()) Text("No places found")
                                SearchState.Idle -> Unit
                            }
                        },
                        isError = results is SearchState.Failed,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    (results as? SearchState.Results)?.suggestions?.forEach { suggestion ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    // Close the keyboard so the map and the rest of the form show.
                                    focus.clearFocus()
                                    viewModel.choose(suggestion)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(suggestion.primary, style = MaterialTheme.typography.bodyLarge)
                            if (suggestion.secondary.isNotBlank()) Note(suggestion.secondary)
                        }
                    }
                }
            }
        }
        item {
            Section("Map and coordinates") {
                MapPicker(
                    pin = draft.point,
                    radiusMeters = draft.radius,
                    others = places.filter { it.id != draft.id },
                    onPick = viewModel::onPick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                OutlinedTextField(
                    draft.coordinates,
                    viewModel::onCoordinates,
                    label = { Text("Latitude, longitude") },
                    supportingText = {
                        Text(
                            draft.message ?: when {
                                draft.coordinates.isNotBlank() && draft.point == null -> "e.g. 23.8103, 90.4125"
                                viewModel.canSearch -> "Search, tap the map, use your location, or paste coordinates"
                                else -> "Use your location or paste coordinates"
                            }
                        )
                    },
                    isError = coordinatesWrong,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    onClick = {
                        val permission = Manifest.permission.ACCESS_FINE_LOCATION
                        if (ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            currentLocation(context, viewModel)
                        } else {
                            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use my current location") }
            }
        }
        item {
            Section("Radius") {
                Text("${draft.radius} m", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = draft.radius.toFloat(),
                    onValueChange = { viewModel.onRadius((it / 10).roundToInt() * 10) },
                    valueRange = Place.MIN_RADIUS.toFloat()..Place.MAX_RADIUS.toFloat(),
                )
            }
        }
        item {
            Section(
                "Wi-Fi",
                note = "While one is in range you count as here, even with location off.",
            ) {
                OutlinedTextField(
                    draft.wifiNames,
                    viewModel::onWifi,
                    label = { Text("Wi-Fi networks here (optional, comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val problem = draft.problem
                if (triedToSave && problem != null) {
                    Text(
                        problem,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                PrimaryButton(onClick = {
                    triedToSave = true
                    if (problem == null) viewModel.save(onDone)
                }) { Text("Save") }
                if (!viewModel.isNew) {
                    SecondaryButton(
                        onClick = { viewModel.delete(onDone) },
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete") }
                }
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
