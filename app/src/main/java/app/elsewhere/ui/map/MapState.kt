package app.elsewhere.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.elsewhere.data.City
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Two stacked tile layers A/B and the pin (`selectCity`, `glide`, `dropPin`). Each animation is one
 * progress value with absolute keyframes, like a WAAPI `animate()`: a newer glide simply takes over.
 */
@Stable
class MapState {
    val slots = arrayOfNulls<City>(2).let { mutableStateOf(it.toList()) }
    var front by mutableIntStateOf(0)
        private set

    /** Glide progress (standard easing already applied); 1 = at rest. */
    val glide = Animatable(1f)
    var vx by mutableFloatStateOf(0f)
    var vy by mutableFloatStateOf(0f)
    var glideReduced by mutableStateOf(false)
    private var glideJob: Job? = null

    /** Pin drop progress on spring pin (may overshoot 1); 1 = landed. */
    val pin = Animatable(1f)
    var pinReduced by mutableStateOf(false)
    private var pinJob: Job? = null

    /** Screenshot tests: the prototype's "Map tiles: Loading" toggle. */
    @androidx.annotation.VisibleForTesting
    var forceLoading by mutableStateOf(false)

    fun city(slot: Int): City? = slots.value[slot]

    /** First composition, or a city change nobody animated (state restore). */
    fun sync(city: City) {
        if (slots.value[front]?.id == city.id) return
        slots.value = listOf(city, city)
        front = 0
    }

    fun glide(scope: CoroutineScope, old: City, new: City, delayMs: Int, reduced: Boolean) {
        val back = 1 - front
        slots.value = slots.value.toMutableList().also { it[back] = new }
        front = back
        var dx = new.lon - old.lon
        if (dx > 180) dx -= 360
        if (dx < -180) dx += 360
        val dy = -(new.lat - old.lat)
        val m = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
        vx = ((dx / m) * Motion.Dp.GlideX).toFloat().round1()
        vy = ((dy / m) * Motion.Dp.GlideY).toFloat().round1()
        glideReduced = reduced
        glideJob?.cancel()
        glideJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            glide.snapTo(0f)
            if (delayMs > 0) delay(delayMs.toLong())
            glide.animateTo(1f, if (reduced) Motion.linear(Ms.Reduced) else Motion.std(Ms.Glide))
        }
        dropPin(scope, delayMs + if (reduced) 0 else Ms.PinAfterGlide, reduced)
    }

    fun dropPin(scope: CoroutineScope, delayMs: Int, reduced: Boolean) {
        pinReduced = reduced
        pinJob?.cancel()
        pinJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            pin.snapTo(0f)
            if (delayMs > 0) delay(delayMs.toLong())
            pin.animateTo(1f, if (reduced) Motion.linear(Ms.Reduced) else Motion.pin())
        }
    }

    private fun Float.round1() = (this * 10f).let { kotlin.math.round(it) } / 10f
}
