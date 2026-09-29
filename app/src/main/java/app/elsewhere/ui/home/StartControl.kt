package app.elsewhere.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.layers.HomeAnim
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.animatedColor
import app.elsewhere.ui.motion.rememberMotionScope
import app.elsewhere.ui.motion.withMotionClock
import app.elsewhere.ui.shape.BlobKind
import app.elsewhere.ui.shape.BlobMorph
import app.elsewhere.ui.theme.LocalElsewhereColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The 156dp Start control (`syncActive`, `onPressDown`, `onPressUp`, `onMain`, `nope`):
 * circle <-> 9-cookie morph, fill/ink colors, arrow <-> stop swap, press scale, ripple rings, spin,
 * burst, blocked shake. Everything animated is read in draw or layer lambdas.
 */
@Composable
fun StartControl(
    active: Boolean,
    blocked: Boolean,
    label: String,
    home: HomeAnim,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val scope = rememberMotionScope()

    // Shape: circle <-> cookie, 420 decel (instant in reduced motion).
    val morph = remember { BlobMorph(if (active) BlobKind.Cookie else BlobKind.Circle) }
    LaunchedEffect(active) {
        withMotionClock { morph.morphTo(if (active) BlobKind.Cookie else BlobKind.Circle, Motion.decel(Ms.Morph), instant = rm) }
    }
    // Fill 300 standard; ink 300 CSS ease; both 150 in reduced motion.
    val fill = animatedColor(
        if (blocked) c.blocked else if (active) c.sage else c.accent,
        if (rm) Motion.std(Ms.Reduced) else Motion.std(Ms.ColorShift),
    )
    val ink = animatedColor(
        if (blocked) c.onBlocked else if (active) c.onSage else c.onAccent,
        if (rm) Motion.std(Ms.Reduced) else Motion.css(Ms.ColorShift),
    )
    // Icons: alpha 180 standard, transform on spring pop.
    val navAlpha = remember { Animatable(if (active) 0f else 1f) }
    val navT = remember { Animatable(if (active) 0f else 1f) }
    val sqAlpha = remember { Animatable(if (active) 1f else 0f) }
    val sqT = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) {
        withMotionClock {
            val a = if (rm) Motion.std<Float>(Ms.Reduced) else Motion.std(Ms.IconFade)
            launch { navAlpha.animateTo(if (active) 0f else 1f, a) }
            launch { sqAlpha.animateTo(if (active) 1f else 0f, a) }
            if (rm) { navT.snapTo(if (active) 0f else 1f); sqT.snapTo(if (active) 1f else 0f) } else {
                launch { navT.animateTo(if (active) 0f else 1f, Motion.pop()) }
                launch { sqT.animateTo(if (active) 1f else 0f, Motion.pop()) }
            }
        }
    }

    // Ripple rings (3, one drawBehind) + ring layer opacity 220 standard; cancelled 240ms after stop.
    val ringsOn = active && !rm
    val ringsOp = remember { Animatable(if (ringsOn) 1f else 0f) }
    var rippleT by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(ringsOn) {
        withMotionClock {
            launch { ringsOp.animateTo(if (ringsOn) 1f else 0f, Motion.std(Ms.RingsFade)) }
            if (ringsOn) {
                var t0 = -1L
                while (true) {
                    val now = withFrameMillis { it }
                    if (t0 < 0) t0 = now
                    rippleT = now - t0
                }
            } else {
                // keep the running rings drawing while the layer fades, then drop them
                val start = rippleT
                if (start >= 0) {
                    var t0 = -1L
                    while (true) {
                        val now = withFrameMillis { it }
                        if (t0 < 0) t0 = now
                        if (now - t0 >= Ms.RingsCancel) break
                        rippleT = start + (now - t0)
                    }
                }
                rippleT = -1L
            }
        }
    }
    // Spin: one turn per 24s while active; pauses (not resets) on stop.
    var spinMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ringsOn) {
        if (!ringsOn) return@LaunchedEffect
        withMotionClock {
            var last = -1L
            while (true) {
                val now = withFrameMillis { it }
                if (last >= 0) spinMs = (spinMs + (now - last)) % Ms.Spin
                last = now
            }
        }
    }
    // Reduced-motion halo: 22% sage at scale 1.24, opacity transition 150ms (CSS ease).
    val halo = remember { Animatable(if (active && rm) 1f else 0f) }
    LaunchedEffect(active, rm) { withMotionClock { halo.animateTo(if (active && rm) 1f else 0f, Motion.css(Ms.HaloFade)) } }
    // Burst ring on start.
    val burst = remember { Animatable(1f) }
    var prevActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !prevActive && !rm) withMotionClock { burst.snapTo(0f); burst.animateTo(1f, Motion.decel(Ms.Burst)) }
        prevActive = active
    }
    // Press scale.
    val press = remember { Animatable(1f) }
    val tap by rememberUpdatedState(onTap)
    val reduced by rememberUpdatedState(rm)
    val path = remember { Path() }

    Box(
        modifier
            .size(156.dp)
            .graphicsLayer { translationX = home.shakeX() * density }
            .drawBehind {
                val d = density
                val center = Offset(size.width / 2, size.height / 2)
                val r = 72f * d // inset 6dp of 156
                val t = rippleT
                if (t >= 0 && ringsOp.value > 0f) {
                    for (i in 0 until 3) {
                        val local = t - (Ms.RippleFirst + i * Ms.RippleStep)
                        if (local < 0) continue
                        val e = Motion.Decel.transform((local % Ms.RippleCycle).toFloat() / Ms.RippleCycle)
                        drawCircle(c.sage.copy(alpha = 0.45f * (1 - e) * ringsOp.value), r * (1 + 0.85f * e), center)
                    }
                }
                if (halo.value > 0f) drawCircle(c.sage.copy(alpha = 0.22f * halo.value), r * 1.24f, center)
                if (burst.value < 1f) {
                    val b = burst.value
                    drawCircle(c.accent.copy(alpha = 0.4f * (1 - b)), r * (1 + 0.5f * b), center)
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (active) "On" else "Off"
                onClick { tap(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pressed = !reduced
                    if (pressed) scope.launch { press.animateTo(Motion.Dp.PressScale, Motion.std(Ms.PressIn)) }
                    val up = waitForUpOrCancellation()
                    if (pressed) scope.launch { press.snapTo(Motion.Dp.PressScale); press.animateTo(1f, Motion.press()) }
                    if (up != null) { up.consume(); tap() }
                    down.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(156.dp).graphicsLayer { scaleX = press.value; scaleY = press.value },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(156.dp).drawBehind {
                    morph.build(path, 1.25f, size.width)
                    rotate(spinMs / Ms.Spin * 360f) { drawPath(path, fill.value) }
                },
            )
            Icon(
                Icons.Navigation, 46.dp, { ink.value },
                Modifier.graphicsLayer {
                    alpha = navAlpha.value
                    val s = 0.4f + 0.6f * navT.value
                    scaleX = s; scaleY = s
                    rotationZ = -90f * (1 - navT.value)
                },
            )
            Icon(
                Icons.Stop, 40.dp, { ink.value },
                Modifier.graphicsLayer {
                    alpha = sqAlpha.value
                    val s = 0.4f + 0.6f * sqT.value
                    scaleX = s; scaleY = s
                    rotationZ = 90f * (1 - sqT.value)
                },
            )
        }
    }
}

/** Kept for API symmetry with the prototype's timer; the tick lives in [SubLine]. */
internal suspend fun tickAligned(startedAt: Long, onTick: (Long) -> Unit) {
    while (true) {
        val now = System.currentTimeMillis()
        onTick(now)
        val into = ((now - startedAt) % Ms.Tick + Ms.Tick) % Ms.Tick
        delay(Ms.Tick - into)
    }
}
