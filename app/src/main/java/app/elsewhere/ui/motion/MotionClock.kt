package app.elsewhere.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Compose scales animations by ANIMATOR_DURATION_SCALE and runs them instantly when it is 0.
 * Reduced motion in this app is a 150ms crossfade (Spec), not "no animation", so animations run on a
 * clock that uses the system scale when it is non-zero and 1 when the system turned animations off.
 */
object MotionClock : MotionDurationScale {
    @Volatile var systemScale: Float = 1f
    override val scaleFactor: Float get() = if (systemScale == 0f) 1f else systemScale
}

suspend fun <T> withMotionClock(block: suspend CoroutineScope.() -> T): T = withContext(MotionClock, block)

/** A coroutine scope whose animations use [MotionClock]. */
@Composable
fun rememberMotionScope(): CoroutineScope = rememberCoroutineScope { MotionClock }
