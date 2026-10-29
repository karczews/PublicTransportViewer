# Screenshot Testing

Home-tab screenshot tests cover both map SDKs (`map-tomtom`, `map-maplibre`). They run
**on an emulator/device** via `connectedDebugAndroidTest` — JVM screenshot tools (Paparazzi,
Roborazzi, Compose Preview screenshots) cannot render real map content: both SDKs draw through
native Surface/GL views that Robolectric/Layoutlib leave blank.

## How it works

- `core-testing`'s `ScreenshotGolden` compares `onRoot().captureToImage()` captures against PNG
  goldens in `<module>/src/androidTest/assets/screenshots/`.
- Verify mode retries the capture until it matches (map tiles arrive asynchronously, so early
  frames after a cold start differ) and fails with actual/diff PNGs saved to the app's external
  files dir when the timeout expires. A 2% mismatch tolerance absorbs GPU dithering and tile
  drift; the per-channel tolerance is 12/255.
- Record mode (`-Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true`) waits for
  two consecutive settled, non-blank frames, then saves to the external files dir instead of
  asserting. If the map never settles before the timeout, the last frame is still saved with a
  warning — always inspect a recorded image before committing it as golden.
- Every saved capture (recorded, actual, diff) is also mirrored to shared storage
  (`Pictures/screenshots`), which survives the APK uninstall that `connectedDebugAndroidTest`
  performs after its run. CI pulls failure artifacts from there; the app-private external files
  dir copy is for local `adb pull` convenience.

## Recording goldens locally

1. Start an emulator matching the canonical CI config (Pixel 6, API 34, google_apis —
   goldens are size-sensitive: API 34 captures the 1080x2209 app window, newer APIs capture
   the full 1080x2400 screen including system bars).
2. Fix the status bar, matching CI:
   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```
3. Run the module's tests in record mode and pull the results:
   ```bash
   ./gradlew :map-maplibre:installDebugAndroidTest
   adb shell am instrument -w -e recordScreenshots true \
     -e class com.github.karczews.publictransportviewer.map.maplibre.MapLibreHomeScreenScreenshotTest \
     com.github.karczews.publictransportviewer.map.maplibre.test/com.github.karczews.publictransportviewer.KoinTestRunner
   adb pull /storage/emulated/0/Android/data/com.github.karczews.publictransportviewer.map.maplibre.test/files/screenshots/ /tmp/screenshots/
   # TomTom equivalents:
   # ./gradlew :map-tomtom:installDebugAndroidTest -PtomtomApiKey="$TOMTOM_API_KEY"
   # adb shell am instrument -w -e recordScreenshots true \
   #   -e class com.github.karczews.publictransportviewer.map.tomtom.TomTomHomeScreenScreenshotTest \
   #   com.github.karczews.publictransportviewer.map.tomtom.test/com.github.karczews.publictransportviewer.KoinTestRunner
   # adb pull /storage/emulated/0/Android/data/com.github.karczews.publictransportviewer.map.tomtom.test/files/screenshots/ /tmp/screenshots/
   ```
   (`am instrument` leaves the APKs installed so the pull works; `connectedDebugAndroidTest`
   uninstalls them afterwards, which is why failure artifacts are also mirrored to shared
   storage — see above.)
4. Inspect the PNGs, copy them to `src/androidTest/assets/screenshots/`, and re-run in verify
   mode (plain `connectedDebugAndroidTest`).

## TomTom API key

TomTom tests (including screenshots) skip via `Assume` when `BuildConfig.TOMTOM_API_KEY` is
blank. Locally, put `tomtomApiKey=...` in `~/.gradle/gradle.properties`; on CI, set the
`TOMTOM_API_KEY` secret (passed as `-PtomtomApiKey`). Without the secret the TomTom tests skip
and stay green. TomTom goldens live in `map-tomtom/src/androidTest/assets/screenshots/` like the
MapLibre ones; re-record them with a key following the steps above (TomTom captures need no
status-bar demo mode on API 34 — only the app window is captured).

## CI

`screenshot-tests.yml` (wired into `ci.yml`'s gate) boots a Pixel 6 API 34 emulator, fixes the
status bar as above, runs both map modules' `connectedDebugAndroidTest`, and uploads the device
screenshot outputs on every run plus the test reports on failure.
