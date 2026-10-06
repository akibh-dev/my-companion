# ADR 003: Jetpack Compose vs XML Views

## Status
Accepted (Compose)

## Context
Need to choose UI framework for a new Android project in 2024+. Considerations:
- Long-term support (Google's direction)
- Learning investment (career relevance)
- Productivity (boilerplate, preview, recomposition)
- Interoperability (can mix with XML if needed)

## Decision
**Jetpack Compose** (Declarative UI, Kotlin-first)

## Consequences

### Positive (Compose)
- **Declarative**: Describe *what* UI should look like, not *how* to update it
- **Less boilerplate**: No `findViewById`, no ViewBinding, no XML layouts
- **Kotlin-native**: Type-safe, IDE support, refactoring works
- **Live Preview**: `@Preview` in Android Studio, interactive
- **Recomposition**: Granular updates, automatic diffing
- **State-driven**: `State`, `MutableState`, `remember`, `derivedStateOf`
- **Material 3**: First-class support, theming, dynamic color
- **Animation API**: Simple, powerful (`animate*AsState`, `Transition`)
- **Interop**: `AndroidView` for legacy views, `ComposeView` in XML
- **Future-proof**: Google's recommended UI toolkit

### Negative (Compose)
- **Learning curve**: Mental model shift (imperative → declarative)
- **Recomposition gotchas**: Unnecessary recompositions, stability
- **Compiler plugin version coupling**: Must match Kotlin version
- **Large APK size**: Compose runtime (~2-3MB)
- **Preview flakiness**: Sometimes fails to render
- **Ecosystem maturity**: Some niche components still missing

### Positive (XML + ViewBinding)
- **Mature**: 15+ years, battle-tested, stable
- **Explicit**: Clear view hierarchy, easy to debug layout inspector
- **Team familiarity**: Most Android devs know it
- **No compiler plugin**: Simpler build config
- **Smaller runtime**: No Compose runtime

### Negative (XML)
- **Verbose**: Layout XML + ViewBinding + findViewById (if no binding)
- **Imperative updates**: Manual `textView.text = "..."`, error-prone
- **No live preview**: Layout editor is limited
- **Theme overhead**: Styles/themes in XML, harder to dynamic
- **Not Google's future**: Maintenance mode, no new features

## Alternatives Considered
| Approach | Verdict |
|----------|---------|
| **XML only** | Rejected - learning dead-end, more boilerplate |
| **Compose only** | Accepted - can interop if needed |
| **Hybrid (Compose in XML)** | Viable migration path, but not for new project |
| **Flutter/KMP** | Out of scope - learning native Android first |

## Implementation Notes

### Project Setup
```kotlin
// build.gradle.kts (app)
android {
    buildFeatures { compose = true }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"  // Match Compose Compiler
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
```

### Theme (Material 3)
```kotlin
// ui/theme/Theme.kt
@Composable
fun MyCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = TypographyDefaults.typography
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
```

### Navigation
```kotlin
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    startRoute: String = "home"
) {
    val navController = rememberNavController()
    NavHost(navController, startRoute, modifier) {
        composable("home") { HomeScreen(onSettingsClick = { navController.navigate("settings") }) }
        composable("settings") { SettingsScreen() }
        composable("player?mediaId={mediaId}") { backStackEntry ->
            val mediaId = backStackEntry.getString()!!
            PlayerScreen(mediaId = mediaId)
        }
    }
}
```

### State Management Patterns
```kotlin
// 1. Simple state (recomposition)
var count by remember { mutableStateOf(0) }

// 2. Derived state (avoid recomposition)
val doubled = remember(count) { derivedStateOf { count * 2 } }

// 3. ViewModel state (survives config change)
val uiState: StateFlow<UiState> = viewModel.uiState
val state by uiState.collectAsStateWithLifecycle()

// 4. State hoisting (stateless composable)
@Composable
fun Counter(count: Int, onIncrement: () -> Unit) { ... }

// 5. Side effects
LaunchedEffect(key) { ... }           // Coroutine tied to composition
DisposableEffect(key) { onDispose { } } // Cleanup on leave/key change
SideEffect { ... }                    // Run on every successful composition
```

### Common Pitfalls & Solutions
| Pitfall | Solution |
|---------|----------|
| Unnecessary recomposition | Use `derivedStateOf`, `remember` with keys, stable types |
| Lambda allocation in composition | Extract to `remember { }` or top-level function |
| `MutableState` in data class | Use `mutableStateOf` at top level, or `StateFlow` from VM |
| Preview not updating | Check `@Preview` parameters, rebuild project |
| Keyboard not showing | `Modifier.focusRequester(requester)` + `requester.requestFocus()` |

### Testing
```kotlin
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testButtonClick() {
        composeTestRule.setContent { HomeScreen(onSettingsClick = { }) }
        composeTestRule.onNodeWithText("Settings").performClick()
        // Assert navigation or state change
    }
}
```

## Interop Strategy (If Needed)
```kotlin
// XML → Compose
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/compose_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />

// In Fragment/Activity
composeView.setContent {
    MyComposeScreen()
}

// Compose → XML (legacy view)
AndroidView(
    factory = { ctx -> LegacyCustomView(ctx) },
    modifier = Modifier.fillMaxSize(),
    update = { view -> view.updateData(newData) }
)
```

## References
- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [Compose Mental Model](https://developer.android.com/jetpack/compose/mental-model)
- [Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Migrate from Views](https://developer.android.com/jetpack/compose/migration)
- [Compose Samples](https://github.com/android/compose-samples)