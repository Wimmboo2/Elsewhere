# Motion map

One row per row of the Spec's motion table (22), plus the prototype-only behaviors. Every duration,
easing and spring comes from `Motion` (`app/src/main/java/app/elsewhere/ui/motion/Motion.kt`); call sites
only name them. Reduced motion = `LocalReducedMotion` (ANIMATOR_DURATION_SCALE == 0, or the debug switch).
Animations run on `MotionClock`, so a 150ms reduced-motion crossfade is still 150ms when the system scale is 0
(Compose would otherwise skip it).

"std" = standard `(0.2, 0, 0, 1)`, "decel" = emphasized decelerate, "accel" = emphasized accelerate,
"CSS ease" = the browser default the prototype falls back to when a transition names no curve.

| # | Spec row | Implemented in | Values used | Reduced motion |
|---|---|---|---|---|
| 1 | Start · press | `home/StartControl.kt` `StartControl` (pointerInput) | scale 1 → 0.92 in 120 std; release restarts from 0.92 on spring press (0.55 / 700) | no scale, state change only |
| 2 | Start → Active | `StartControl` (`BlobMorph`, `animatedColor`, icon Animatables), `HomeScreen.kt` `MainText`, `Kicker`, `LiveTag`, `App.kt` ground layer | morph circle → cookie 420 decel; fill 300 std; ink 300 CSS ease; arrow out (0.4, −90°) / stop in (0.4, +90°) on spring pop with 180 std alpha; label + sub rise 8dp 260 decel; kicker fade 200 std + color 300 CSS ease; ground alpha 320 std; Live tag scale 0.6 → 1 spring pop + alpha 220 std; burst ring 1 → 1.5, 0.4 → 0, 400 decel | shape swaps instantly; fill/ink/icons/label/ground/Live alpha crossfade 150; no burst, no springs |
| 3 | Active ripple + spin | `StartControl` (one `drawBehind` for 3 rings; frame loops) | rings scale 1 → 1.85, alpha .45 → 0, 1800 decel, delays 260 / 860 / 1460, infinite; spin 24000 linear, paused (not reset) on stop | rings and spin off; static sage halo at 22%, scale 1.24, alpha transition 150 (CSS ease) |
| 4 | Active → Idle | `StartControl` | reverse morph 420 decel; ring layer alpha 220 std; ring loop cancelled 240ms later; spin pauses | instant shape, 150 crossfades |
| 5 | Blocked tap | `layers/Stage.kt` `nope()`, `HomeAnim.shakeX/nudgeScale` | Start wrap x 0, −6, 5, −3, 0 over 320 std; card scale 1 → 1.03 → 1 over 300 std | both skipped (prototype `rm()` skips the shake too) |
| 6 | Map · camera glide | `map/MapState.kt` `glide()`, drawn in `map/MapPreview.kt` | both layers 360 std; old: alpha 1 → 0, translate −(64, 40)dp along the bearing, scale 1.04; new: from +(64, 40)dp, 0.96, alpha 0; longitude wrap as in `glide`; delayed 240 after a picker flight | 150 linear crossfade |
| 7 | Map · pin drop | `MapState.dropPin()`, `MapPreview` pin draw | 200ms after glide start; pin y −40 → 0 and alpha on spring pin (0.45 / 600); shadow scale 0.2 → 1 | 150 linear fade |
| 8 | Map · skeleton | `MapPreview` | blocks alpha 1 ↔ 0.45, 900 std, alternate; each tile alpha 0 → 1 200 std; skeleton layer out 250 std; offline chip when every front tile failed | static skeleton (no pulse) |
| 9 | Container transform | `Stage.openLayer()` + `layers/LayerHost.kt` | clip rounded rect from source bounds/radius (chip 999 clamped, title 20) to window, radius → 0, 380 decel; background surface → bg; content alpha 200 std @100 | 150 std fade |
| 10 | Container return | `Stage.containerOut()` + `LayerHost` | full → target rect (chip / title on Back, home card r32 after a pick) in 320 std, shrink over the first 85% then layer fades; content out 120 std | 150 linear fade |
| 11 | Shared element | `Stage.flyWhenPlaced()` + `App.kt` `FlightView` | country flag → city header flag; city name → home title (uniform scale by height ratio), header flag → home chip flag (non-uniform); 400 decel; targets hidden until landing | no flight; title crossfade 150 decel (`swapTitle`) |
| 12 | Shared axis X | `Stage.pickCountry()`, `Stage.goBack()`, `Stage.openLayer()` (settings) | city in from +48dp 340 decel, country out to −48dp 300 std; back: city → +48dp 260 accel, country from −48dp 340 decel; settings in +56dp 320 decel, out +56dp 240 accel | 150 fades (linear / decel / accel as in the prototype branches) |
| 13 | List entrance | `Stage.StaggerClock`, `pickers/Pickers.kt` `Modifier.stagger` | first 12 rows, delay 90 + 30·min(i, 7), 280 decel, rise 12dp + fade; on picker open, sheet open and tab change | rows appear at once |
| 14 | Favorite · pop | `pickers/Pickers.kt` `StarButton` | star scale 0.5 → 1 spring pop; burst ring 0.4 → 1.5, alpha .45 → 0, 300 decel | fill change only |
| 15 | Favorite · off | `StarButton` | scale 0.8 → 1, 200 std; fill switches at once, stroke color 150 CSS ease (see README conflicts) | fill change only |
| 16 | Status card · in | `home/HomeScreen.kt` `StatusSlot` | rise 16dp from 0.98 + fade on spring gentle (0.85 / 300); strip alpha 250 std + drop 12dp 320 decel | 150 std crossfade; strip alpha only |
| 17 | Status card · out | `StatusSlot` | fade + drop 8dp + 0.98, 200 accel; next card or strip follows | 150 accel fade |
| 18 | Bottom sheet | `Stage.openSheet/closeSheet/settleSheet`, `sheet/QuickSwitchSheet.kt` | sheet y 100% → 0 360 decel, out 240 accel; scrim 250 std in, 200 std out; drag down to dismiss (past 25% or fast fling closes with the 240 accel tween, else settles with 360 decel) | sheet 150 fade (decel in, accel out); scrim 150 in / 200 out as in the prototype |
| 19 | City swap | `Stage.swapTitle()` | title rise 12dp + fade 280 decel; country chip too when the country changes; coords fade 200 linear @60; glide + pin run | 150 decel fade |
| 20 | Onboarding step | `onboarding/Onboarding.kt` | shape morph sun → squircle → cookie 400 decel; rotation 0 / −8 / 20° 420 decel; fill 300 std; satellite position/scale spring gentle, color 300 CSS ease; text slides 32dp in travel direction 320 decel; icon pops (0.4, −20°) on spring pop; icon alpha 150 CSS ease; pills 8 → 24dp 320 decel, color 250 CSS ease | shape/rotation/satellite snap; colors and text 150 |
| 21 | Home enter | `Stage.enterHome()` | home scale 0.97 → 1 + fade 360 decel; pin drops at 220 | 150 decel fade, pin fade |
| 22 | Theme crossfade | `App.kt` `ThemeSnapshot.crossfade` | old frame captured from the root GraphicsLayer, drawn on top fading 1 → 0, 300 std | 150 std |

## Prototype-only behaviors

| Behavior | Where |
|---|---|
| Recents push (previous city, max 8, deduped), quick-switch chip list (favorites, then recents, current excluded, max 6) | `AppViewModel.selectCity`, `HomeScreen.QuickSwitchStrip` |
| Sheet row: close, then select 120ms later | `Stage.pickFromSheet` |
| "Browse cities": close sheet, then open the country picker from the chip | `Stage.browseFromSheet` |
| Sticky header divider fades in (200 CSS ease) past 4dp of scroll | `Pickers.SearchHeader` |
| Pill press scales (0.94 back, 0.97 primary, 0.96 card action, 0.95 chips; 160 std where the prototype declares a transform transition, instant otherwise) | `components/Press.kt` `pressable` |
| Segment and tab colors 200 CSS ease | `settings/SettingsScreen.kt` `Segmented` |
| Pin color accent → sageStrong and halo alpha 0 → 0.3, 300 CSS ease | `MapPreview` |
