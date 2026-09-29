package app.elsewhere.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every motion value in the app, copied from the prototype's `EASE`, `SPR` and `class Component`.
 * Call sites read names from here; no durations or curves are written inline anywhere else.
 */
object Motion {
    // ---- Easing (prototype EASE) ----
    /** Fades, colors, glide, container return. cubic-bezier(0.2, 0, 0, 1) */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Anything entering. cubic-bezier(0.05, 0.7, 0.1, 1) */
    val Decel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Anything leaving. cubic-bezier(0.3, 0, 0.8, 0.15) */
    val Accel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /**
     * CSS `ease`, the browser default. The prototype leaves several CSS transitions without a timing
     * function (text/icon colors, pin fill, halo opacity, segment colors, progress dot color,
     * onboarding icon opacity, divider color, star fill), so they run on this curve.
     */
    val CssEase: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    /** WAAPI default when an `animate()` call names no easing (coords fade, reduced-motion fades). */
    val Linear: Easing = LinearEasing

    // ---- Springs (prototype SPR, same mass-1 model as springEase) ----
    object Spring {
        const val PressDamping = 0.55f; const val PressStiffness = 700f
        const val PinDamping = 0.45f; const val PinStiffness = 600f
        const val PopDamping = 0.4f; const val PopStiffness = 900f
        const val GentleDamping = 0.85f; const val GentleStiffness = 300f
    }

    fun <T> press(): AnimationSpec<T> = spring(Spring.PressDamping, Spring.PressStiffness)
    fun <T> pin(): AnimationSpec<T> = spring(Spring.PinDamping, Spring.PinStiffness)
    fun <T> pop(): AnimationSpec<T> = spring(Spring.PopDamping, Spring.PopStiffness)
    fun <T> gentle(): AnimationSpec<T> = spring(Spring.GentleDamping, Spring.GentleStiffness)

    // ---- Durations (ms), named after what uses them in the prototype ----
    object Ms {
        const val Reduced = 150            // every reduced-motion crossfade
        const val PressIn = 120            // Start press scale 1 -> 0.92
        const val PillPress = 160          // pill / chip press scale transition
        const val IconFade = 180           // arrow <-> stop alpha
        const val RingsFade = 220          // ripple layer opacity
        const val RingsCancel = 240        // ripple animations cancelled after stop
        const val LiveFade = 220           // Live tag alpha
        const val MainText = 260           // label + sub-line rise
        const val Kicker = 200             // kicker fade
        const val ColorShift = 300         // fills, ink, pin color
        const val Ground = 320             // active ground tint
        const val Morph = 420              // circle <-> cookie
        const val Burst = 400              // Start burst ring
        const val RippleCycle = 1800       // one ripple ring
        const val RippleFirst = 260        // first ring delay
        const val RippleStep = 600         // ring offset
        const val Spin = 24000             // one full turn of the cookie
        const val Shake = 320              // blocked tap shake
        const val CardNudge = 300          // blocked tap card pulse
        const val CardOut = 200
        const val StripFade = 250
        const val StripMove = 320
        const val Glide = 360
        const val PinAfterGlide = 200
        const val FlightGlideDelay = 240   // glide waits for the city flight
        const val SkeletonPulse = 900
        const val SkeletonOut = 250
        const val TileFade = 200
        const val TitleSwap = 280
        const val CoordsFade = 200
        const val CoordsDelay = 60
        const val ContainerIn = 380
        const val ContainerContentIn = 200
        const val ContainerContentDelay = 100
        const val ContainerOut = 320
        const val ContainerContentOut = 120
        const val AxisIn = 340             // city in from +48, country back from -48
        const val AxisCountryOut = 300     // country slides to -48 (standard)
        const val AxisCityOut = 260        // city leaves to +48 (accel)
        const val SettingsIn = 320
        const val SettingsOut = 240
        const val Flight = 400
        const val StaggerRow = 280
        const val StaggerLead = 90
        const val StaggerStep = 30
        const val StarBurst = 300
        const val StarOff = 200
        const val StarFill = 150
        const val SheetIn = 360
        const val SheetOut = 240
        const val ScrimIn = 250
        const val ScrimOut = 200
        const val SheetRowDelay = 120      // sheet row tap: select after the sheet starts closing
        const val ObMorph = 400
        const val ObRotate = 420
        const val ObText = 320
        const val ObPill = 320
        const val ObDotColor = 250
        const val ObIconFade = 150
        const val HomeEnter = 360
        const val HomePin = 220
        const val Theme = 300
        const val HaloFade = 150
        const val SegmentColor = 200
        const val Divider = 200
        const val Tick = 1000L
    }

    // ---- Distances (dp) ----
    object Dp {
        const val PressScale = 0.92f
        const val MainTextRise = 8f
        const val CardInRise = 16f
        const val CardOutDrop = 8f
        const val StripDrop = 12f
        const val TitleRise = 12f
        const val StaggerRise = 12f
        const val Axis = 48f
        const val SettingsAxis = 56f
        const val ObText = 32f
        const val PinDrop = 40f
        const val GlideX = 64f
        const val GlideY = 40f
        const val StaggerRows = 12
        const val StaggerCap = 7
    }

    fun <T> std(ms: Int, delay: Int = 0): AnimationSpec<T> = tween(ms, delay, Standard)
    fun <T> decel(ms: Int, delay: Int = 0): AnimationSpec<T> = tween(ms, delay, Decel)
    fun <T> accel(ms: Int, delay: Int = 0): AnimationSpec<T> = tween(ms, delay, Accel)
    fun <T> css(ms: Int): AnimationSpec<T> = tween(ms, 0, CssEase)
    fun <T> linear(ms: Int, delay: Int = 0): AnimationSpec<T> = tween(ms, delay, Linear)
    /** Reduced-motion crossfade. */
    fun <T> reduced(easing: Easing = Standard): AnimationSpec<T> = tween(Ms.Reduced, 0, easing)

    /** Stagger delay for list row [index] (prototype `stagger`). */
    fun staggerDelay(index: Int): Int = Ms.StaggerLead + minOf(index, Dp.StaggerCap) * Ms.StaggerStep

    /** CSS-style color interpolation: premultiplied sRGB, not Oklab (Compose's default lerp). */
    fun lerpColor(a: Color, b: Color, t: Float): Color {
        val aa = a.alpha; val ba = b.alpha
        val alpha = aa + (ba - aa) * t
        if (alpha <= 0f) return Color.Transparent
        val r = (a.red * aa + (b.red * ba - a.red * aa) * t) / alpha
        val g = (a.green * aa + (b.green * ba - a.green * aa) * t) / alpha
        val bl = (a.blue * aa + (b.blue * ba - a.blue * aa) * t) / alpha
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), bl.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** True when animations are off (ANIMATOR_DURATION_SCALE == 0) or forced from the debug switch. */
val LocalReducedMotion = staticCompositionLocalOf { false }
