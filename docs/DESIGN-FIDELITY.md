# Design fidelity

Elsewhere was rebuilt from an approved HTML prototype (`design/project/Elsewhere Prototype.dc.html`) with the goal
of matching it exactly: layout, colors in both themes, copy, and every animation's duration, easing and spring.
This file records how that was checked and every place where the app deliberately differs.
`MOTION.md` maps each animation to the code that implements it.

## Verification

No KVM in the build container, so no emulator could run. Instead, the real app is rendered by Robolectric with
native graphics (real Skia/Minikin, 412 x 915dp at 420dpi, Pixel 7 class) and compared with the prototype rendered in
headless Chromium at the same density (`tools/verify/capture-prototype.js`, `ScreenshotTest.kt`, `compare.py`).
The prototype's fake status bar and gesture bar are masked. Chromium does not trust the sandbox's TLS proxy, so the
capture script serves React from npm, the same font files the app bundles, and tiles fetched with curl.

Screens (`verify/compare/<state>.png` shows prototype | app | diff):

| state | pixels differing > 24 levels (map masked) | notes |
|---|---|---|
| onboarding 1 / 2 / 3 | 1.1 to 1.7% | text antialiasing |
| home, home no favorites | 1.4 to 1.5% | coordinates differ by data (GeoNames vs sample) |
| home active | 2.0% light, 8.2% dark | ripple rings are captured at different phases |
| status cards (4) | 1.9 to 2.3% | |
| map loading | 1.4 to 1.5% (unmasked) | skeleton blocks match |
| country, country no results | 4.3% / 0.6% | list content is the real dataset |
| city, city no results | 1.9% / 0.4% | |
| settings | 2.6% | |
| sheet favorites / recents / empty | 1.3 to 1.9% | |

Motion (`verify/compare-motion/`): both renderers frozen mid-transition at the same timestamps (Chromium: every
running animation paused at `currentTime = t`; Compose: paused test clock, +1 frame):
container open at 190ms, container return with name/flag flights at 200ms, country to city at 170ms, container close
at 160ms, sheet at 180ms, Start morph at 200ms. All line up; the only differences are map tiles (they do not load in
the JVM renderer) and one frame of timing quantization on the sheet.

Other checks: the 244 generated flags against the source SVGs rendered at 128 x 96 (mean difference 1.4/255, worst
cases are coats of arms simplified below visible size, `tools/verify/check-flags.js`); the map filter port against
Chromium's CSS filter on real tiles (0 error in both themes, `tools/verify/check-filter.js`).

Unresolved / not measurable here:
- Vertical positions drift by up to ~1.5dp toward the bottom of Home: Compose lays text out on whole pixels while Chrome
  keeps fractions. The home card's 18dp lines are pinned; the rest stays within the 2dp tolerance.
- Line breaks can differ by one word where a line sits within a pixel of its max width
  (sheet empty state: "…and it'll wait / for you here." in the prototype, "…wait for / you here." in the app).
- Map tiles were verified by math and the filter check, not by a live screenshot.
- Real-device behavior (test providers, foreground service, Samsung quirks) needs a device run; see above.

## Prototype vs Spec conflicts (the prototype wins)

1. Display line height: the Spec says Caprasimo 34/40; the home city title uses 34/44 (onboarding titles use 34/40).
2. Killed-service card: the Spec calls it blocking; in the prototype only location or mock app block Start (`blocked = !locPerm || !mockSelected`). Start stays enabled and starting clears it.
3. Search field focus: the Spec shows a 2dp accent ring; the prototype input has `outline: none` and no ring.
4. Status card buttons: the Spec's sample is 40dp tall; the prototype is 48dp. Search clear button: Spec sample 40dp, prototype 44dp.
5. Favorite off: the Spec says the fill drains over 150ms; in the prototype `fill` goes between a color and `none`, which CSS cannot interpolate, so the fill switches at once and only the stroke color transitions (150ms).
6. Color transitions: the Spec says colors run on the standard curve; the prototype leaves many transitions without a timing function (kicker color, Start ink, pin fill and halo, segment colors, progress-dot color, onboarding ink and satellite color, header divider, star stroke), so they run on CSS `ease`. `Motion.CssEase` reproduces that.
7. Quick-switch strip: the Spec lists 250 standard; the prototype transitions opacity 250 standard and transform 320 decel, in both directions.
8. Container return target: the Spec says pickers shrink into the location card; in the prototype Back shrinks into the source (country chip, radius 999 clamped; city title, radius 20 from `parseFloat('0px') || 20`), and only picking a city shrinks into the card (radius 32).
9. List filtering: the Spec perf notes mention `animateItem()` at 200ms; the prototype re-renders without animation, so the app does too.
10. Blocked tap under reduced motion: the Spec skips only the card nudge; the prototype skips the shake too.
11. Sheet scrim under reduced motion: the Spec says a 150ms fade; the prototype keeps the 200ms standard scrim fade-out.
12. Durations cap: the Spec says nothing is longer than 420ms; the spring-driven CSS transitions settle in 440ms (pop) and the pin in 480ms. They are springs here, so they map 1:1.
13. Spec Compose mapping (ModalBottomSheet, SharedTransitionLayout, AnimatedContent, RoundedPolygon): replaced by custom code as requested, because those APIs cannot reproduce the prototype's exact clip radii, keyframes and offsets.
14. Reduced motion where the prototype has no branch (CSS transitions for the morph, colors, Live tag, strip): the Spec's reduced-motion column is implemented (instant shape, 150ms crossfades, no springs).
15. Prototype quirk, not copied: `pickCountry` measures the city header flag while its layer is still at +48dp, so the flag clone lands 48dp right of the header and snaps back when it is removed. The app lands the flag on the header's resting position.
16. Prototype detail copied although it looks accidental: the onboarding icon `<svg>` sits inline on a text baseline, so it is 3.19dp above the shape's center. The app offsets it the same way.
17. Drag-down to dismiss is in the Spec, not in the prototype: added, settling with the prototype's sheet tweens.

## Design gaps (smallest choices that fit)

- **App icon**: adaptive icon, terracotta `#c67139` background with the cream `#fff8f0` navigation arrow from the Start button; monochrome layer for themed icons.
- **Splash**: SplashScreen API, the theme's bg color with the arrow in the accent color. On Android 12+ the in-app Light/Dark choice is passed to `UiModeManager.setApplicationNightMode` so the next splash matches; below 12 the splash follows the system theme.
- **Notification**: low-importance channel "Trip in progress"; "Elsewhere is on" / "You're in {city}, {country}", Stop action, sage accent, silent and ongoing.
- **Permission rationale**: no extra screens; the status cards are the rationale (their copy is reused). If location or notifications are permanently denied, the card action opens the app's settings page.
- **COARSE location**: Android 12+ only grants FINE when COARSE is requested in the same prompt, so both are declared and requested; "Approximate" is accepted as enough for mocking.
- **Tile-less first launch / offline**: the skeleton stays and the prototype's "Map preview needs a connection" chip appears once every visible tile failed. Location mocking still works.
- **"1 city"**: the prototype template is always "N cities"; the sample data never has one city, real data does, so it is pluralized.
- **Long names**: the prototype never has names long enough to overflow; city/sheet names and the picker title ellipsize instead of overlapping the star.
- **No admin1 region**: the region line falls back to the country name.
- **Back on onboarding step 1**: leaves the app on first run; returns Home when the guide was replayed from Settings.
- **Spot picker and hotel fallback** (see above): new screen and a "Move pin" chip on the home map, both added on request.
- **Debug reduced-motion switch**: long-press "Version" (debug builds only) or the adb extra above.

## Things that cannot be matched 1:1 natively (closest equivalent used)

- `text-wrap: balance` / `pretty` -> `LineBreak.Heading` / `LineBreak.Paragraph` (balanced breaking needs Android 13+; older versions break greedily).
- Hover styles do not exist on touch: pressed states show the element's `style-hover` background together with its `style-active` values, which is what a mobile browser shows on a tap.
- Start control hit area is its 156dp square; Chrome hit-tests the rounded `border-radius`.
- Progress pills animate CSS `width`; here the three pills are drawn in one draw pass at their animated widths (no relayout).
- The theme crossfade uses a bitmap of the old frame (`GraphicsLayer.toImageBitmap`) drawn over the new one, the same result as a view transition's old/new crossfade.
- The map filter is baked into each tile per pixel (the four W3C matrices with clamping between steps, as Chrome does) instead of one `ColorMatrix`, because a single matrix skips the intermediate clamps and brightens near-white tiles by a few levels. Verified identical to Chromium.
- Sheet shadow: CSS blur 28px is converted to Compose's BlurMaskFilter radius so the Gaussian sigma matches (14px).

## Performance notes

- Home at rest draws ground, card, map, strip and button; ripples exist only while active (3 circles in one `drawBehind`); the active ground is one alpha layer.
- Every animated value is read in `graphicsLayer` / draw lambdas; the 1s timer is read only by its own `Text`.
- Compose compiler reports (`./gradlew assembleRelease -PcomposeReports=true`): all 40 composables are restartable and skippable.
- Dataset: 1.4 MB TSV (deflated to ~0.7 MB in the APK), parsed on a background thread at process start; search runs on `Dispatchers.Default`, debounced 150ms.
- A baseline profile for the app's own code ships via ProfileInstaller (`app/src/main/baseline-prof.txt`). It is hand-written because generating one needs a device; with a device, a Macrobenchmark `BaselineProfileGenerator` can replace it.

