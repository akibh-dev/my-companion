# 07 — Android and Compose

[Previous](06-coroutines-flow.md) · [Index](../README.md) · [Next](08-repository-tour.md)

Use a separate Android Studio Empty Activity Compose project for this lesson. This chapter's code is a complete screen declaration, not a console program. Your project needs Material 3, Compose runtime/foundation, Activity Compose, lifecycle ViewModel Compose, and lifecycle runtime Compose. This repository already has those dependencies, but keep the initial exercise outside production navigation.

## Android owns component lifetimes

An Activity is an Android entry point, not the entire app. Android can create/destroy it for configuration changes and reclaim a background process. Lifecycle callbacks describe component transitions; they do not guarantee a final onDestroy before process death.

A ViewModel survives ordinary configuration changes for its owner, but not process death. Remember survives recomposition; rememberSaveable can restore supported small values through saved state. SavedStateHandle can restore suitable ViewModel inputs. Room/DataStore persist durable data. Do not store a large database or bitmap in saved-instance state.

## State down, events up

Compose functions describe UI from state and may run repeatedly. Recomposition is not an event handler. Do not write a database row or start a service directly in a composable body. Send user intent to a state owner, then render the resulting state.

Hoist state to the lowest common owner that needs it. A field can hold editing text locally; a ViewModel can coordinate screen validation and repository work. A repository should own persisted truth instead of several screens independently caching conflicting copies.

This screen validates input and shows a summary; it does not persist or schedule a reminder. Its button says Preview to make that boundary visible. In your learning project's package, add the package declaration above these imports:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReminderFormState(
    val title: String = "",
    val minutes: String = "",
    val feedback: String = "",
)

class ReminderFormViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ReminderFormState())
    val state = mutableState.asStateFlow()

    fun titleChanged(value: String) {
        mutableState.update { it.copy(title = value, feedback = "") }
    }

    fun minutesChanged(value: String) {
        mutableState.update { it.copy(minutes = value, feedback = "") }
    }

    fun preview() {
        mutableState.update { current ->
            val minutes = current.minutes.trim().toIntOrNull()
            val message = when {
                current.title.isBlank() -> "Enter a title"
                minutes == null || minutes !in 0..10_080 -> "Enter minutes from 0 to 10080"
                else -> "${current.title.trim()} in $minutes minutes"
            }
            current.copy(feedback = message)
        }
    }
}

@Composable
fun ReminderFormRoute(model: ReminderFormViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = state.title,
            onValueChange = model::titleChanged,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.minutes,
            onValueChange = model::minutesChanged,
            label = { Text("Minutes") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = model::preview) { Text("Preview") }
        Text(state.feedback)
    }
}
```

Call ReminderFormRoute() inside the template Activity's existing setContent and MaterialTheme. Keep the template's Activity/package setup. This default-constructible ViewModel uses the standard factory; the production app's injected ViewModels use Hilt factories.

Text editing above uses a simple value/onValueChange API. For advanced editing, learn Compose text field state and selection/composition handling; the production app has additional typing constraints. Do not transform user input on every keystroke without considering cursor and IME behavior.

## Effects and navigation

LaunchedEffect ties coroutine work to composition and its keys. A key change cancels/restarts that work. DisposableEffect acquires/releases resources with explicit disposal. Neither is a substitute for deciding which lifecycle owns an operation. Navigation has a back stack; route arguments should identify data rather than carry whole mutable repositories.

CollectAsStateWithLifecycle turns observable state into Compose state while respecting lifecycle collection. It does not make an unsafe upstream function main-safe, nor persist data by itself.

## Verification

Enter a blank title, abc minutes, -1, 10081, and valid 15. Expected: useful validation messages for invalid cases; a trimmed summary for the valid case. Rotate with populated fields: the ViewModel retains them. Restart the process: this version does not promise restoration. Add SavedStateHandle or persistence in the capstone, then test that requirement separately.

Check readable labels, font scaling, keyboard visibility, focus order, and TalkBack output. Permissions and services belong in later integration steps, not inside this preview-only validation lesson.

References: [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course), [Compose state](https://developer.android.com/develop/ui/compose/state), [architecture recommendations](https://developer.android.com/topic/architecture/recommendations).
