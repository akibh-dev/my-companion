# Plan 18: Baseline profile (do last)

**Status:** planned, not started. Do this **after every feature works and the screens stop changing**.
A profile records which of the app's methods run, so each redesign makes it stale; generating it
once at the end avoids redoing the work.

## What it is

A baseline profile is a list of the app's hot code (startup, opening each tab, scrolling) shipped
inside the APK as `baseline-prof.txt`. On install, `profileinstaller` hands it to Android, which
compiles that code to machine code ahead of time instead of interpreting it the first few times.

Today only the libraries' own profiles (Compose, Navigation) ship with the app, so our own screens
run uncompiled on first use. Measured on the CMF Phone 1 (release build, see the navigation work in
PR #17): about one slow frame per tab switch, worst returning to Home (~33 ms against a 16 ms
budget). After `cmd package compile -m speed-profile -f` it improved, which shows what a profile buys.

Without our own profile, Android still learns the hot code from real use and compiles it while the
phone is idle and charging, but only days later and again after every reinstall.

## Expected gain

- 20-40% faster cold start and first render of each screen (typical published figures; we will
  measure our own).
- Tab switches should mostly lose their one slow frame.
- No runtime cost; slightly longer install and a few hundred KB of extra storage.

## Steps

1. **Branch** `perf/baseline-profile` from `main`.
2. **Module** `:baselineprofile` (`com.android.test`) with the `androidx.baselineprofile` Gradle
   plugin, targeting `:app`. Add the plugin to `:app` and add
   `androidx.profileinstaller:profileinstaller` explicitly (today it only arrives transitively).
3. **Generator** (`BaselineProfileGenerator`, UiAutomator) walking the real journeys:
   - cold start to Home;
   - each tab: People, Reminders, Player (Up next, Playlists, History);
   - open the reminder editor sheet and swipe it away;
   - Settings and each link in it, and back;
   - scroll each list.
   Must not create, edit or delete data (the phone holds real reminders and contacts).
4. **Generate on the phone**, connected by USB or the phone's hotspot (never wireless adb on the
   office Wi-Fi, which crashes the PC). Build the APKs with Gradle, install with `adb install`, and run
   with `adb shell am instrument`. Never `connectedAndroidTest`: it uninstalls the app and wipes its data.
5. **Commit** the generated `app/src/main/baseline-prof.txt` (and `startup-prof.txt` if startup
   profiles are enabled for dex layout).
6. **Measure before and after** on release builds, each after
   `adb shell cmd package compile --reset <package>` so the phone's own learned profile doesn't skew it:
   - cold start time (`am start -W`, 10 runs, median);
   - janky frames per 12 tab switches (`dumpsys gfxinfo`, the deadline-based "Janky frames" count).
   Optionally add a `StartupBenchmark` in the same module with `CompilationMode.None()` vs
   `CompilationMode.Partial()`.
7. **Check every screen** in light and dark mode, then open the PR.

## Related, also at the end

- **R8 shrinking** (`isMinifyEnabled = true` for release): smaller APK and faster code. It needs keep
  rules for Hilt, Room, ONNX Runtime and the YouTube extractor, and every feature must be retested
  on the phone, so it pairs well with this plan.
- **Refreshing the profile:** regenerate whenever a screen is substantially redesigned. CI can't do it
  (it needs a real device), so it stays a manual step before a release.

## References

- [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Create Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
- [Measure with Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
