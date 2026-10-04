# Elsewhere

[![Latest release](https://img.shields.io/github/v/release/Wimmboo2/Elsewhere)](https://github.com/Wimmboo2/Elsewhere/releases/latest)
[![Release APK](https://github.com/Wimmboo2/Elsewhere/actions/workflows/release.yml/badge.svg)](https://github.com/Wimmboo2/Elsewhere/actions/workflows/release.yml)

## What it is

Elsewhere is an Android app that moves your phone's reported location to another city. Pick one of 34,148
cities, tap Start, and every app that asks Android for a location gets that city instead, through the platform's
own mock location API (no root).

## What it can do

- **Any city with 15,000+ people.** 34,148 cities in 244 countries from GeoNames, bundled in the app so the
  list works offline. Search ignores accents and matches the city or its region, so `reykjavik` finds Reykjavík
  and `kyoto` finds everything in Kyoto Prefecture.
- **Start and Stop.** A foreground service feeds Android's GPS and network test providers about once a second,
  with a quiet ongoing notification that has its own Stop button. Switching city during a trip moves you right away.
- **Pick the exact spot.** Tap the home map, drag it under a fixed pin and zoom to street level to put yourself on
  a specific building. Saved per city.
- **A sensible default inside each city.** If you haven't picked a spot, Elsewhere uses the nearest hotel within
  3 km from OpenStreetMap, and falls back to the GeoNames city point when there is no hotel nearby or no connection.
- **Favorites and recents.** Star cities, get the last 8 cities you left, and switch from a strip of up to six chips
  on the home screen or from the Quick switch sheet.
- **Setup that checks itself.** Onboarding walks through enabling Developer options and choosing Elsewhere as the
  mock location app. After that, status cards appear when something is actually wrong:
  - location permission is off
  - Elsewhere isn't the selected mock app
  - Android killed the trip in the background (with Restart)
  - notifications are blocked on Android 13+
- **Map preview.** Warm-tinted raster tiles with a loading skeleton and an offline state; the mocked location
  works without them.
- **Light, dark or system theme**, and a reduced-motion mode that follows Android's "Remove animations" setting.
- **No accounts, ads or analytics.** It never reads your real location; it only writes a fake one. The only network
  traffic is map tiles and the nearest-hotel lookup.

## How it works

```
GeoNames dumps ──tools/build_cities.py──▶ assets/cities.tsv ──▶ CityRepository (parsed off the main thread)
                                                                       │
                     DataStore (city, spots, favorites, active, ...) ◀─┤
                        │                                      AppViewModel (StateFlow) ──▶ Compose UI
                        ▼
              MockLocationService ──setTestProviderLocation()──▶ LocationManager GPS + network ──▶ other apps
```

**One activity, one ViewModel, no navigation library.** `AppViewModel` holds the app state in a single
`StateFlow`, plus a small layer stack (country picker, city picker, settings, spot picker) persisted in
`SavedStateHandle`. Screens are layers drawn over Home rather than navigation destinations, because every transition
is custom: pickers grow out of the element you tapped and shrink back into it, and the flag and city name fly
between screens. `ui/layers/Stage.kt` runs that choreography; `ui/motion/Motion.kt` holds every duration, easing and
spring, and animated values are read only in draw or layer lambdas so animations don't recompose.
[MOTION.md](MOTION.md) maps every animation to the function that implements it.

**The service and the UI share DataStore, not a binding.** The UI writes the selected city, the per-city spots
and the active flag. `MockLocationService` observes the same preferences and resolves the exact point with the
same function as the UI (`effectiveSpot` in `data/Spots.kt`: your spot, then the hotel, then the city point).
So the notification's Stop, a city switch from the app, and a picked spot all converge on one source of truth,
and the service keeps working when the UI is gone.

**Detecting the device setup instead of asking about it.** Whether Elsewhere is the selected mock location app
isn't exposed by any API, so `MockEnvironment` tries to add a throwaway test provider and treats a
`SecurityException` as "not selected". It does this on every resume, so the card disappears when you come back
from Developer options. A trip that Android killed is detected from the persisted active flag plus an in-process
"service running" flag, with a 600 ms grace period for a service that is still starting.

**City data is generated, not fetched.** `tools/build_cities.py` turns GeoNames `cities15000` and the admin-1 names
into a 1.4 MB TSV (about 0.7 MB compressed in the APK) sorted by population. Country names come from
`java.util.Locale` at runtime. Search runs on `Dispatchers.Default`, debounced by 150 ms.

**Maps without a map SDK.** The home preview is a 3×3 grid of raster tiles and the spot picker is a small pan/zoom
tile view (`SlippyMap.kt`). Both use Coil for loading and the disk cache, and need no API key. Flags are 244 vector
drawables generated from flag-icons by `tools/build_flags.py`.

**Releases build themselves.** `.github/workflows/release.yml` builds the release APK on every push to `main` and
publishes it as the GitHub Release named after `versionName`.

```
app/src/main/java/app/elsewhere/
  MainActivity.kt          splash, edge-to-edge, permission prompts, Settings intents
  data/                    city dataset, search, spots + nearest-hotel lookup, DataStore
  service/                 MockLocationService, setup detection
  ui/AppViewModel.kt       state, layer stack, search flows
  ui/layers/               transition controller (Stage) and the clipped layer host
  ui/home, pickers, sheet, settings, onboarding, spot   screens
  ui/map/                  tile math, tile loading + filter, map preview, pan/zoom picker map
  ui/motion, theme, icons, shape, flags                 design system
tools/                     data generators and the screenshot verification scripts
design/                    the original HTML prototype and spec
```

## What I figured out

- **Android Gradle Plugin quietly unpacks `.gz` assets.** The dataset first shipped as `cities.tsv.gz`, and the app
  could not open it. In the built APK the file was there, but renamed to `cities.tsv` and stored uncompressed: AGP
  gunzips `.gz` assets and drops the extension. The asset is now plain TSV, which the APK compresses anyway
  (`tools/build_cities.py`).
- **Reduced motion is not "no motion" in Compose.** With Android's animator scale at 0, Compose finishes every
  animation instantly, so the design's 150 ms reduced-motion crossfades never appeared. Animations run on a custom
  `MotionDurationScale` (`MotionClock`) that honours the system scale unless it is 0.
- **Matching a browser's colors and curves.** Compose interpolates colors in Oklab; CSS transitions use
  premultiplied sRGB, so mid-transition colors drifted. `Motion.lerpColor` does the CSS math. Several prototype
  transitions had no timing function and therefore ran on CSS `ease`, which `Motion.CssEase` reproduces.
- **CSS filters clamp between steps.** The map tint is four chained CSS filters. A single combined `ColorMatrix`
  skips the clamping after each step and brightens near-white tiles, so the filter is applied per pixel, in order,
  when a tile is decoded (`MapFilterTransformation`). A check against Chromium on real tiles shows zero difference
  (`tools/verify/check-filter.js`).
- **`LaunchedEffect` starts one frame late.** Fade-ins that snap to 0 could flash for one frame at full opacity,
  because the effect runs after the new state is drawn. `ChangeEffect` starts the animation from `SideEffect`,
  before the frame is drawn, like the prototype's `componentDidUpdate`.
- **Cancelled animations leave junk on screen.** Stopping within 400 ms of starting cancelled the burst ring
  halfway and left a faint circle around the button. One-shot animations now run in their own scope.
- **Shared loads must not belong to their first caller.** The parsed dataset is cached as a single `Deferred`. If
  the service happened to start that load, stopping the service would cancel it for everyone, and the UI would wait
  forever. The load now lives in its own process-wide scope.
- **Turning 244 SVG flags into small vectors.** The generator flattens curves, simplifies them (Ramer-Douglas-Peucker,
  coarser for detailed coats of arms) and drops detail too small to see at 32 dp. It also handles clip paths,
  gradients and the SVG markers that the US flag uses for its stars. Merging same-colored even-odd shapes punched
  holes in flags like Burundi and Liberia, which a side-by-side render against the source SVGs caught.
- **A city's point is often city hall.** GeoNames puts Tokyo at the Tokyo Metropolitan Government Building. That led
  to the spot picker and the nearest-hotel fallback. Two free OpenStreetMap services are tried in turn (Photon, then
  Overpass), and every answer, including "no hotel here", is cached per city so each phone asks once.
- **Verifying a pixel-exact port without an emulator.** The build environment had no hardware virtualization, so
  the app was rendered with Robolectric's native graphics and compared against the prototype in headless Chromium
  at the same size and density. Animations were frozen mid-transition at the same millisecond in both renderers
  (`tools/verify/`). Results and remaining differences are in [docs/DESIGN-FIDELITY.md](docs/DESIGN-FIDELITY.md).
- **Security and privacy tradeoffs.**
  - The app never reads the real location and sends nothing about you anywhere.
  - It does not try to hide that it is mocking from other apps.
  - Android 12+ only grants precise location if approximate is requested in the same prompt, so both are
    requested, and approximate is enough.
  - Releases are signed with a deliberately public test key, so local and CI builds update each other. Anyone could
    sign an update with it, which is why the workflow switches to a private key as soon as one is configured
    (see `signing/README.md`).

## How to run it

### Install on a phone

1. Download the APK from [Releases](https://github.com/Wimmboo2/Elsewhere/releases/latest) and open it on an
   Android 8.0+ phone. Android will ask you to allow installs from your browser or file manager. Play Protect will
   probably warn that the app is unknown: it isn't from the Play Store and is signed with a test key.
2. Open Elsewhere and follow the onboarding:
   - Settings > About phone > tap **Build number** seven times
   - Settings > Developer options > **Select mock location app** > Elsewhere
3. Allow location, and notifications on Android 13+. Pick a city and tap Start.

To check you have the official build, the release signing certificate's SHA-256 fingerprint is
`3E:08:FE:12:82:1A:C9:AF:D8:5C:98:C2:CB:0D:16:52:AA:36:EB:7D:71:DE:CE:14:5A:12:DA:6B:1C:E7:88:E1`.

On Samsung phones:
- Auto Blocker (Settings > Security and privacy) blocks installs from outside the Play Store. You can turn it back
  on after installing.
- Some One UI versions forget the mock location app after an update. If the "isn't the mock app yet" card comes
  back, re-select Elsewhere in Developer options.

### Build from source

Requirements: JDK 17 or newer (CI uses 21) and the Android SDK with platform 37 and build-tools 37.
No API keys or environment variables are needed.

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # R8 + resource shrinking, about 2.7 MB
./gradlew testDebugUnitTest    # dataset and spot tests, plus JVM screenshot rendering into verify/app*
```

Optional flags:
- `-Pelsewhere.liveNetwork=true` also runs the real Photon/Overpass lookup test.
- `-PcomposeReports=true` writes Compose compiler stability reports.

**Signing with your own key.**
- Locally, set `ELSEWHERE_KEYSTORE`, `ELSEWHERE_KEYSTORE_PASSWORD`, `ELSEWHERE_KEY_ALIAS` and `ELSEWHERE_KEY_PASSWORD`.
- In CI, add the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
- Without them, both use the test key in `signing/`.

**Making a release.** Bump `versionCode` and `versionName` in `app/build.gradle.kts` and push to `main`. The
workflow publishes `v<versionName>` with the APK attached. Pushing a `v*` tag, or running the workflow from the
Actions tab, releases that tag instead.

**Regenerating the bundled data** (the outputs are committed, so this is only needed to update them):

```sh
tools/fetch_sources.sh         # GeoNames, flag-icons and fonts into tools/cache/ (needs curl, npm, fonttools)
python3 tools/build_cities.py  # -> app/src/main/assets/cities.tsv
python3 tools/build_flags.py   # -> res/drawable/flag_*.xml (needs: pip install svgelements)
```

## Credits

- City data © [GeoNames](https://www.geonames.org), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Suggested spots: © OpenStreetMap contributors, ODbL, via [Photon](https://photon.komoot.io) and the
  [Overpass API](https://overpass-api.de). Both are free fair-use services; the app makes one cached request per
  city. Check their usage policies before distributing widely; the endpoints are in `SpotConfig`.
- Map tiles: Esri Canvas World Light/Dark Gray Base. Check Esri's terms before a store release; the tile URL lives in
  `ui/map/MapConfig.kt`.
- Flags: [flag-icons](https://github.com/lipis/flag-icons), MIT.
- Icons: [Lucide](https://lucide.dev), ISC.
- Fonts: Caprasimo and Figtree, SIL Open Font License 1.1.

The license texts are in [`licenses/`](licenses/).

## License

The code is released under the [MIT License](LICENSE). The bundled city data, flags, icons and fonts keep their own
licenses, listed under [Credits](#credits).
