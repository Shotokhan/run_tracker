# RunTracker

An offline Android running tracker: GPS-based distance/pace, distance-based
splits, an optional distance goal with a snoozeable alert, and local run
history. No internet permission is requested and nothing leaves the device.

## Before you build: read this

I wrote every file below carefully, but **I could not compile or run this
project myself** — my environment has no Android SDK and can't reach Google's
Maven repository. So treat the first CI build as the real test. If the
GitHub Actions run fails, paste me the error log and I'll fix it
immediately; that's a normal and expected part of shipping a project this
size, not a sign anything is fundamentally wrong.

## How to build (GitHub Actions — no Android Studio needed)

This repo includes `.github/workflows/build.yml`, which builds a debug APK
on GitHub's servers every time you push. You never need Android Studio
installed locally.

1. Create a new (empty) GitHub repository and push this whole folder to it:
   ```bash
   cd RunTracker
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<your-repo>.git
   git push -u origin main
   ```
2. Go to your repo's **Actions** tab on GitHub. The "Build & Release APK"
   workflow should already be running (it triggers automatically on push to
   `main`).
3. When it finishes (a few minutes), you have two ways to get the APK:
   - **Actions tab → the completed run → Artifacts** section at the
     bottom → download `runtracker-debug-apk` (a zip containing the `.apk`).
   - **Releases** (right sidebar of the repo, or `github.com/<you>/<repo>/releases`)
     — the workflow auto-creates a prerelease tagged `build-<commit-sha>`
     with `app-debug.apk` attached directly, which is the more convenient
     one to download straight to your phone.
4. Transfer the `.apk` to your Android phone (email it to yourself, use a
   cloud drive, or download the Release link directly from the phone's
   browser) and tap it to install. You'll need to allow "install from
   unknown sources" for whichever app you used to open it — Android will
   prompt you for this the first time. **Use a real phone (Android 8.0+),
   not an emulator** — GPS in emulators is simulated and won't give you real
   pace/distance data.
5. On first launch, grant the location permission (and notification
   permission on Android 13+) when prompted.

### Updating the app without losing your runs

Android only installs an APK over an existing app if both are signed with
the **same key**. GitHub's runners create a brand-new debug key on every
run, so early builds could not be installed as updates. From this version on,
a fixed debug key (`app/debug.keystore`) is committed to the repo and used
for every build, so **all future builds install in-place as updates and keep
your saved runs** (the database schema has not changed either).

One-time caveat: the build you installed *before* this version was signed
with a throwaway key that no longer exists, so installing this version over
it will fail with "App not installed" / a signature conflict. Uninstalling
the old app deletes its data, so before doing that, save any runs you want
to keep (e.g. your screenshots). After this one transition, updates are
seamless. (The key is public in the repo; that's fine for a personal
sideloaded app, but don't reuse this approach for anything published.)

### What the workflow does, step by step

- Checks out the code and sets up JDK 17 + the Android SDK on a GitHub-hosted
  Ubuntu runner.
- Accepts the Android SDK licenses (needed non-interactively in CI).
- Downloads `gradle-wrapper.jar` directly from Gradle's GitHub repo — I
  included `gradlew`/`gradlew.bat` (the wrapper *scripts*) in this project,
  but the wrapper *jar* is a binary file I can't generate by hand, so the
  workflow fetches it at build time instead. (This is the one simplification
  I made versus a fully offline-buildable repo — it's a one-line, well-known
  public download, not a security concern.)
- Runs `./gradlew assembleDebug` — this is the actual compile step, and
  where any real problems with the Kotlin code would surface.
- Uploads the resulting `app-debug.apk` as both a workflow artifact and a
  GitHub Release (only on pushes to `main`/`master`).

I simplified this from a typical example workflow by dropping the unit-test
steps entirely, since this project doesn't have any tests — there's nothing
for `./gradlew test` to run, and no test-report artifact to upload on
failure. If you add tests later, those steps are easy to add back.

### If the Actions build fails

Click into the failed step in the Actions run — Gradle's error output is
usually specific (e.g. a missing import, a version mismatch). Paste it to me
and I'll patch the source directly.

## Alternative: build locally in Android Studio

If you get access to a machine with Android Studio later, that still works
too — just open the `RunTracker/` folder as a project and press Run. Android
Studio manages the Gradle wrapper jar itself on sync, so you won't need the
manual download step CI does.

## What it does

- **Start / Stop** a run from the main screen.
- **Optional goal** in meters or kilometers, set before starting.
- **Live stats**: elapsed time, current distance, current (smoothed) pace,
  average pace.
- **Auto-splits** every 1 km (configurable in code — see below), each with
  its own distance/time/pace, shown live during the run.
- **Goal-reached alert**: vibration + a notification with **Snooze (+500 m)**
  or **Stop** actions — this works even if the app is backgrounded, as long
  as the phone hasn't force-killed the app.
- **Runs in the background**: tracking continues with the screen off or the
  app minimized, via a foreground service with a persistent notification
  (Android requires this notification — it can't be hidden — so the runner
  always knows tracking is active).
- **History**: every finished run is saved locally (Room/SQLite) and
  viewable later, with its own split breakdown.

## Design decisions worth knowing about

- **No Google Play Services dependency.** Location comes from
  `android.location.LocationManager`'s GPS provider directly, not the Fused
  Location Provider. This means the app works on any Android 8+ device
  (including ones without Google apps) and never touches the network, at
  the cost of slightly slower initial GPS lock compared to fused location
  (which can borrow a rough position from cell/Wi-Fi to speed up the first
  fix). Once GPS is locked, accuracy is the same either way.
- **GPS filtering.** Raw GPS fixes are noisy. Two filters keep distance
  honest: fixes with poor accuracy (`>20m`, tune via `minAccuracyMeters` in
  `TrackCalculator.kt`) are discarded, and consecutive fixes closer than 3m
  apart are ignored (otherwise standing still slowly "walks" your distance
  upward from GPS jitter).
- **Pace smoothing.** Instantaneous pace is computed over a rolling 15-second
  window rather than point-to-point, since point-to-point GPS pace swings
  wildly.
- **BPM/heart rate is intentionally not included**, per your call — phones
  don't have a built-in heart rate sensor, so this would need either a
  Bluetooth chest strap/watch (a separate, sizeable BLE integration) or a
  camera-based finger sensor (only usable standing still). Easy to add later
  if you want it — happy to build the Bluetooth HR version as a follow-up.
- **Snooze behavior**: tapping Snooze extends the goal by 500 m (hard-coded
  in `TrackingService.EXTRA_SNOOZE_METERS` default and in the in-app dialog)
  and clears the alert, so you keep running and get notified again at the
  new target. Change `500.0` in `MainScreen.kt` and `TrackingService.kt` if
  you'd rather snooze by a different amount.

## Where to tweak things

| Want to change...              | Edit...                                              |
|---------------------------------|-------------------------------------------------------|
| Split distance (default 1 km)   | `splitDistanceMeters` in `TrackCalculator.kt`         |
| GPS accuracy/jitter filtering   | `minAccuracyMeters`, `minDisplacementMeters` in `TrackCalculator.kt` |
| Pace smoothing window           | `paceWindowMillis` in `TrackCalculator.kt`            |
| Snooze increment                | `EXTRA_SNOOZE_METERS` default in `TrackingService.kt` and the dialog in `MainScreen.kt` |
| GPS update frequency            | `MIN_UPDATE_INTERVAL_MS` in `TrackingService.kt`      |
| App colors/theme                | `ui/theme/Color.kt`                                    |

## Project structure

```
app/src/main/java/com/example/runtracker/
├── MainActivity.kt          Permission requests + navigation host
├── RunTrackerApp.kt         Application class, exposes the Room database
├── core/
│   └── TrackCalculator.kt   Pure-Kotlin distance/pace/split math (no Android framework dependency besides Location)
├── service/
│   └── TrackingService.kt   Foreground service: GPS updates, notifications, saves finished runs
├── viewmodel/
│   └── TrackingViewModel.kt Binds to the service, exposes state + history to Compose
├── data/                    Room entities + DAO (SessionEntity, SplitEntity, AppDatabase)
├── ui/                      Compose screens: MainScreen, HistoryScreen, SessionDetailScreen
└── util/
    └── Formatters.kt        Distance/duration/pace display formatting
```

## A note on "offline"

Everything about tracking, storage, and alerts works with the phone in
airplane mode (GPS itself doesn't need a signal/data connection — only the
initial satellite lock, which can take longer without any network assist,
since this app deliberately skips the network-assisted fix for
Play-Services-free operation). Just make sure airplane mode doesn't also
disable GPS on your specific phone (a few manufacturers bundle them) —
if so, re-enable location services specifically.
