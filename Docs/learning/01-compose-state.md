# Step 01: Compose State & Events

| | |
|---|---|
| **Phase** | 0 — Compose & App Foundations |
| **Time** | 2–4 hours |
| **Branch** | `feat/step-01-compose-state` |
| **You need** | Hello World running on your device ([How to Start](README.md#how-to-start-do-this-once)) |

## Goal

Turn the static "Hello World!" into a small **Home screen** that reacts to taps:

```
┌──────────────────────────────────────┐
│ My Companion                         │
│                                      │
│ Tapped 3 times        [ +1 ] [Reset] │
│                                      │
│ Custom notifications           (●  ) │
│ Background playback            (  ●) │
│                                      │
│ Both features on — nice!             │
└──────────────────────────────────────┘
```

By the end you will understand **why** the screen updates — not just that it does.
This is the single most important Compose idea; every later step builds on it.

---

## Part 1 — Concepts (read first, ~30 min)

### 1.1 UI = f(state)

In Compose, a `@Composable` function **describes** the UI for the current data.
It does not hold widgets you later mutate (no `textView.setText(...)`).

```
   state ──▶ @Composable function ──▶ UI on screen
     ▲                                   │
     └──────────── event (tap) ◀─────────┘
```

When state changes, Compose **calls your function again** with the new data. That re-call is called
**recomposition**.

### 1.2 Composition vs recomposition

| Term | Meaning |
|------|---------|
| **Composition** | First time Compose runs your functions and builds the UI tree |
| **Recomposition** | Compose re-runs *only the functions that read state which changed* |
| **Skipping** | If a composable's inputs didn't change, Compose may skip re-running it |

Consequence: a composable function can run **many times, in any order, at any moment**. So:
- Don't do side effects (network, file writes, logging in production) directly in a composable body.
- Any normal local variable (`var x = 0`) is **reset on every recomposition**.

### 1.3 Observable state: `mutableStateOf`

Compose only notices changes to values it can **observe**:

```kotlin
val count = mutableIntStateOf(0)   // MutableIntState — Compose watches .intValue
val enabled = mutableStateOf(false) // MutableState<Boolean> — Compose watches .value
```

Reading `.value` inside a composable subscribes that composable. Writing `.value` schedules a recomposition.

`mutableIntStateOf` / `mutableFloatStateOf` / `mutableLongStateOf` avoid boxing primitives — prefer them for numbers.

### 1.4 Surviving recomposition: `remember`

`mutableStateOf(0)` alone would create a **new** state object (starting at 0) every recomposition.
`remember { ... }` stores the object in the composition so the same one comes back next time:

```kotlin
val count = remember { mutableIntStateOf(0) }   // count.intValue
var count by remember { mutableIntStateOf(0) }  // property delegate: just `count`
```

The `by` version needs these imports (the IDE often misses them):

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

### 1.5 Surviving Activity recreation: `rememberSaveable`

`remember` lives as long as the composition. When the Activity is **recreated** (rotation, dark-mode switch,
language change) the composition is thrown away and state resets.

`rememberSaveable` additionally saves the value into the Activity's saved-instance `Bundle`, so it comes back.

| | Survives recomposition | Survives rotation / theme change | Survives app being killed |
|---|---|---|---|
| plain `var` | ❌ | ❌ | ❌ |
| `remember` | ✅ | ❌ | ❌ |
| `rememberSaveable` | ✅ | ✅ | ✅ (if killed by the system in background) |
| DataStore / Room *(Steps 11, 13)* | ✅ | ✅ | ✅ (even after swipe-away / reboot) |

### 1.6 State hoisting

A composable that **owns** its state is hard to reuse and test. Instead, move ("hoist") the state up to the
caller and pass down two things:

- **value** — what to show (`checked: Boolean`)
- **event callback** — what happened (`onCheckedChange: (Boolean) -> Unit`)

```
HomeScreen  (stateful: owns `notificationsEnabled`)
    │  checked = notificationsEnabled ▼        ▲ onCheckedChange(newValue)
FeatureToggle  (stateless: just draws + reports taps)
```

This is **unidirectional data flow**: state goes down, events go up. `Switch`, `TextField`, `Checkbox`
in Material 3 all work this way — that's why `Switch` needs both `checked` and `onCheckedChange`.

---

## Part 2 — Build It (~1.5 h)

> Type the code. Autocomplete is fine; copy-paste is not. Run on the device after every part.

### 2.1 Create the Home screen file

Create `app/src/main/java/com/akib/mycompanion/presentation/home/HomeScreen.kt`
(this matches the planned `presentation/` layer from `AGENTS.md`):

```kotlin
package com.akib.mycompanion.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var count = 0 // ⚠️ deliberately wrong — see 2.2

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Tapped $count times")
        Button(onClick = { count++ }) {
            Text(text = "+1")
        }
    }
}
```

In `MainActivity.kt`, replace `Greeting("Hello World!")` inside `MyApp()` with `HomeScreen()`
(add `import com.akib.mycompanion.presentation.home.HomeScreen`). Delete the now-unused `Greeting`.

Run: `./gradlew :app:installDebug` (or ▶ in the IDE).

### 2.2 Observe the bug

Tap **+1** several times. The text stays at **0**.

Why? `count++` does change the local variable, but nothing tells Compose to redraw, and even if
something did, `var count = 0` would reset it to 0 on the re-run. Two problems:
1. not **observable** → fix with `mutableIntStateOf`
2. not **remembered** → fix with `remember`

### 2.3 Fix it with `remember` + `mutableIntStateOf`

```kotlin
var count by remember { mutableIntStateOf(0) }
```

Add the imports: `remember`, `mutableIntStateOf`, `getValue`, `setValue` (all in `androidx.compose.runtime`).

Run again — the counter now works. ✅

### 2.4 Rotate (or toggle dark mode)

Tap to 5, then rotate the phone (enable auto-rotate) or switch dark mode from Quick Settings.
The counter goes back to **0** — the Activity was recreated.

Replace `remember` with `rememberSaveable`
(`import androidx.compose.runtime.saveable.rememberSaveable`) and try again. The count survives. ✅

### 2.5 Hoist the counter into a stateless composable

Extract the counter UI so it doesn't own any state:

```kotlin
@Composable
fun Counter(
    count: Int,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Tapped $count times", modifier = Modifier.weight(1f))
        Button(onClick = onIncrement) { Text(text = "+1") }
        OutlinedButton(onClick = onReset, enabled = count > 0) { Text(text = "Reset") }
    }
}
```

`HomeScreen` keeps the state and passes value + events down:

```kotlin
Counter(count = count, onIncrement = { count++ }, onReset = { count = 0 })
```

Notice **Reset** is disabled at 0 — the UI is derived from state, you never "disable the button" manually.

### 2.6 Your turn: feature toggles (no full solution given first)

Build a stateless `FeatureToggle` composable and use it twice in `HomeScreen`.

Requirements:
- [ ] Signature: `FeatureToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)`
- [ ] Row with the title on the left (taking remaining width) and a Material 3 `Switch` on the right
- [ ] Two toggles in `HomeScreen`: "Custom notifications" and "Background playback"
- [ ] Each toggle's state survives rotation
- [ ] When **both** are on, show the text "Both features on — nice!" below them
- [ ] A title "My Companion" at the top using `MaterialTheme.typography.headlineMedium`
- [ ] Space items 16.dp apart using `verticalArrangement = Arrangement.spacedBy(16.dp)` on the `Column`

Try it yourself before opening the solution.

<details>
<summary>Solution (open only after trying)</summary>

```kotlin
package com.akib.mycompanion.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(false) }
    var backgroundPlaybackEnabled by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "My Companion", style = MaterialTheme.typography.headlineMedium)

        Counter(count = count, onIncrement = { count++ }, onReset = { count = 0 })

        FeatureToggle(
            title = "Custom notifications",
            checked = notificationsEnabled,
            onCheckedChange = { notificationsEnabled = it },
        )
        FeatureToggle(
            title = "Background playback",
            checked = backgroundPlaybackEnabled,
            onCheckedChange = { backgroundPlaybackEnabled = it },
        )

        if (notificationsEnabled && backgroundPlaybackEnabled) {
            Text(text = "Both features on — nice!")
        }
    }
}

@Composable
fun Counter(
    count: Int,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Tapped $count times", modifier = Modifier.weight(1f))
        Button(onClick = onIncrement) { Text(text = "+1") }
        OutlinedButton(onClick = onReset, enabled = count > 0) { Text(text = "Reset") }
    }
}

@Composable
fun FeatureToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme { HomeScreen() }
}

@Preview(showBackground = true)
@Composable
private fun FeatureTogglePreview() {
    MaterialTheme {
        FeatureToggle(title = "Custom notifications", checked = true, onCheckedChange = {})
    }
}
```

Note how `FeatureTogglePreview` can show the "on" state directly — that's a perk of stateless composables.

</details>

---

## Part 3 — Break It (~30 min)

Do each experiment, observe, then undo it. Write what you saw in your learning note.

1. **Forget `remember`**: change to `var count by mutableIntStateOf(0)` (no `remember`).
   What does the IDE/lint say? What happens when you tap?
2. **Watch recomposition**: add `Log.d("Recompose", "Counter($count)")` as the first line of `Counter`, and
   `Log.d("Recompose", "FeatureToggle($title)")` in `FeatureToggle`. Run `adb logcat -s Recompose`.
   Tap **+1**, then flip one switch. Which functions log each time? Which are **skipped**?
   *(Remove the logs afterwards — logging in a composable body is a side effect.)*
3. **Ignore the event**: pass `onCheckedChange = {}` to one toggle. Tap it. Why doesn't the switch move,
   even though you tapped it? *(Hint: who owns `checked`?)*
4. **Process death**: set the counter to 7, press Home, then run
   `adb shell am kill com.akib.mycompanion` and reopen the app from Recents. Is the 7 still there?
   Now swipe the app away from Recents and reopen. What's different, and why? (See the table in 1.5.)
5. **Modifier order**: in `HomeScreen`, swap to `modifier.padding(16.dp).fillMaxSize()`. Any visible
   difference here? *(Keep the question in mind — Step 02 explains when order matters.)*

---

## Part 4 — Checkpoint Questions

Answer from memory, then check.

1. What triggers a recomposition?
2. Why does `var count = 0` inside a composable never show a change?
3. Difference between `remember` and `rememberSaveable`?
4. What two parameters does a stateless/hoisted composable usually take for one piece of state?
5. Why is it a bad idea to make a network call directly in a composable body?

<details>
<summary>Answers</summary>

1. Writing to a Compose state object (`MutableState.value`, `MutableIntState.intValue`, …) that some composable read during its last composition.
2. Plain variables aren't observable, so no recomposition is scheduled — and even if one happened, the variable would be re-initialised to `0`.
3. `remember` survives recompositions only; `rememberSaveable` also survives Activity recreation and system process death by saving into the saved-instance `Bundle`.
4. The current **value** and an **event callback** (e.g. `checked` + `onCheckedChange`).
5. Composables can run many times, in any order, and be skipped or cancelled — the call could fire repeatedly or not at all. Side effects belong in effect APIs (`LaunchedEffect`, later) or, better, in a ViewModel (Step 05).

</details>

---

## Done When

- [ ] `HomeScreen` is in `presentation/home/` and `MainActivity` shows it
- [ ] Counter and both toggles survive rotation / dark-mode switch
- [ ] `Counter` and `FeatureToggle` hold **no** state of their own
- [ ] Previews render in the IDE
- [ ] All 5 "Break It" experiments done and noted
- [ ] `./gradlew assembleDebug` passes
- [ ] Learning note below filled in

## Commit & PR

```bash
git switch -c feat/step-01-compose-state
git add app/src/main/java/com/akib/mycompanion Docs/learning/01-compose-state.md
git commit -m "feat: add stateful home screen with counter and feature toggles"
git push -u origin feat/step-01-compose-state
# open a PR to main, review your own diff, merge
```

Then mark Step 01 ✅ in [README.md](README.md) and move on to Step 02.

---

## Official Docs

- [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state)
- [Thinking in Compose (mental model)](https://developer.android.com/jetpack/compose/mental-model)
- [Where to hoist state](https://developer.android.com/jetpack/compose/state-hoisting)
- [Lifecycle of composables](https://developer.android.com/jetpack/compose/lifecycle)
- [Codelab: State in Jetpack Compose](https://developer.android.com/codelabs/jetpack-compose-state)

---

## My Learning Note

> Fill this in yourself after finishing the step.

### What I Built


### Key Classes / APIs Used
- `remember` —
- `rememberSaveable` —
- `mutableIntStateOf` / `mutableStateOf` —
- `Switch` / `Button` / `OutlinedButton` —

### Break It — What I Observed
| Experiment | What happened | Why |
|------------|---------------|-----|
| 1. No `remember` | | |
| 2. Recompose logs | | |
| 3. Ignored event | | |
| 4. Process death vs swipe-away | | |
| 5. Modifier order | | |

### Problems Faced & Solutions
| Problem | Solution | Source |
|---------|----------|--------|
| | | |

### Questions for Later
- [ ]
