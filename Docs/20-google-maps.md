# Google Maps for saved places

The place editor (**Home → Context rules → Saved places → Add place**) shows a Google Map and a
search box (decision D21). Search a place by name, or tap or long-press the map to drop the pin,
then set the radius and save. "Use my current location" and typed coordinates still work
without the map.

Only picking a place uses Google. Detecting that you arrived or left (`PlaceDetector`) stays on
the phone, using its own location and Wi-Fi.

## 1. Get an API key (once)

1. Open the [Google Cloud console](https://console.cloud.google.com/) and create or pick a project.
   Google requires a billing account on the project.
2. **APIs & Services → Library**: enable **Maps SDK for Android** and **Places API (New)**.
3. **APIs & Services → Credentials → Create credentials → API key**.
4. Restrict the key:
   - **Application restrictions → Android apps**: add package `dev.akibh.mycompanion` with the
     SHA-1 of the key that signs the app. For the debug keystore on this laptop:
     `keytool -list -v -keystore ~/.android/debug.keystore -storepass android | grep SHA1`.
   - **API restrictions**: only Maps SDK for Android and Places API (New).

Cost: the map in the app (Maps SDK for Android) has no charge. Places search is metered.
Each search, meaning the suggestions while you type plus the place you pick, is billed as one
session. Saving a few places a month stays well inside Google's free monthly allowance. Check
[Google's pricing page](https://developers.google.com/maps/billing-and-pricing/pricing) for the
current numbers.

## 2. Put the key in the build

Add it to `local.properties` in the project folder. That file is git-ignored, so the key is never
committed:

```properties
MAPS_API_KEY=AIza...
```

Rebuild and install (`./gradlew installDebug`). The build puts the key in the app manifest
(`com.google.android.geo.API_KEY`). On CI, set a `MAPS_API_KEY` environment variable if a build
with a working map is needed; without it CI still builds.

## Without a key

The app still builds and runs. The map area explains that the key is missing, the search box is
hidden, and places can be added with current location or coordinates.

## How it's built

| Part | File |
|---|---|
| Map, pin, radius circle, other places in grey | `feature-context/.../context/maps/MapPicker.kt` (maps-compose) |
| Search suggestions and the chosen place's location | `.../context/maps/PlaceSearch.kt` (Places SDK, `GooglePlaceSearch`) |
| Search state (300 ms pause before searching, errors) | `PlaceEditorViewModel` in `.../context/ui/PlacesScreen.kt` |

Versions: maps-compose 6.11.0 and Places 5.1.1 are the newest built for this project's Kotlin 2.2
and Compose BOM 2025.08. Newer ones need Kotlin 2.3 or 2.4, which is a separate upgrade.
