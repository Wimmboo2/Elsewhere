package app.elsewhere.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/** A color that transitions like CSS: from whatever is on screen to the new target, lerped in sRGB. */
@Stable
class CssColor(initial: Color) {
    private var from = initial
    private var to = initial
    private val p = Animatable(1f)
    val value: Color get() = Motion.lerpColor(from, to, p.value)

    suspend fun animateTo(target: Color, spec: AnimationSpec<Float>, instant: Boolean = false) {
        if (target == to && p.value == 1f) return
        from = value
        to = target
        p.snapTo(0f)
        if (instant) p.snapTo(1f) else p.animateTo(1f, spec)
    }
}

/** Color that follows [target] with [spec]; read `.value` in draw/layer lambdas. */
@Composable
fun animatedColor(target: Color, spec: AnimationSpec<Float>, instant: Boolean = false): CssColor {
    val c = remember { CssColor(target) }
    LaunchedEffect(target) { withMotionClock { c.animateTo(target, spec, instant) } }
    return c
}

/** Float that follows [target] with [spec] (CSS transition on a single property). */
@Composable
fun animatedFloat(target: Float, spec: AnimationSpec<Float>, instant: Boolean = false): Animatable<Float, *> {
    val a = remember { Animatable(target) }
    LaunchedEffect(target, instant) {
        withMotionClock { if (instant) a.snapTo(target) else a.animateTo(target, spec) }
    }
    return a
}

/**
 * Runs [block] when [key] changes (not on first composition), starting synchronously in the apply
 * phase so snaps land before the frame that shows the new state is drawn, like the prototype's
 * `componentDidUpdate`. A newer change cancels the previous run.
 */
@Composable
fun <K> ChangeEffect(key: K, block: suspend kotlinx.coroutines.CoroutineScope.(previous: K) -> Unit) {
    val scope = rememberMotionScope()
    val holder = remember { arrayOf<Any?>(key, null) }
    androidx.compose.runtime.SideEffect {
        @Suppress("UNCHECKED_CAST")
        val prev = holder[0] as K
        if (prev != key) {
            holder[0] = key
            (holder[1] as? kotlinx.coroutines.Job)?.cancel()
            holder[1] = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { block(prev) }
        }
    }
}
