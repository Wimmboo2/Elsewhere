package app.elsewhere.ui.shape

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The prototype's `shape(kind)`: 90 points of a polar curve starting at -90 degrees, rounded to
 * 3 decimals like its `toFixed(3)`. Every shape has the same point count, so a morph is a
 * point-by-point lerp, frame for frame what the browser does when it interpolates `d`.
 */
enum class BlobKind { Circle, Cookie, Sun, Squircle }

object Blobs {
    const val N = 90

    private val cache = HashMap<BlobKind, FloatArray>()

    fun points(kind: BlobKind): FloatArray = cache.getOrPut(kind) {
        FloatArray(N * 2) { 0f }.also { out ->
            for (i in 0 until N) {
                val a = i.toDouble() / N * PI * 2 - PI / 2
                val r = when (kind) {
                    BlobKind.Circle -> 1.0
                    BlobKind.Cookie -> 0.93 * (1 + 0.075 * cos(9 * a))
                    BlobKind.Sun -> 0.95 * (1 + 0.04 * cos(12 * a))
                    BlobKind.Squircle -> 0.86 * (abs(cos(a)).pow(4) + abs(sin(a)).pow(4)).pow(-0.25)
                }
                out[i * 2] = round3(r * cos(a))
                out[i * 2 + 1] = round3(r * sin(a))
            }
        }
    }

    private fun round3(v: Double): Float = ((v * 1000).roundToInt() / 1000.0).toFloat()

    /**
     * Fills [path] with the lerp of [from] and [to] at [t], mapped from unit space
     * [-extent, extent] into a square of [sizePx].
     */
    fun buildPath(path: Path, from: FloatArray, to: FloatArray, t: Float, extent: Float, sizePx: Float) {
        path.rewind()
        val k = sizePx / (2 * extent)
        val c = sizePx / 2
        for (i in 0 until N) {
            val x = from[i * 2] + (to[i * 2] - from[i * 2]) * t
            val y = from[i * 2 + 1] + (to[i * 2 + 1] - from[i * 2 + 1]) * t
            if (i == 0) path.moveTo(c + x * k, c + y * k) else path.lineTo(c + x * k, c + y * k)
        }
        path.close()
    }

    /** Snapshot of the lerp, used as the new start when a morph is retargeted mid-flight (CSS transition semantics). */
    fun lerp(from: FloatArray, to: FloatArray, t: Float, out: FloatArray = FloatArray(N * 2)): FloatArray {
        for (i in out.indices) out[i] = from[i] + (to[i] - from[i]) * t
        return out
    }
}

/** A blob that morphs to [target] like a CSS `d` transition: retargeting starts from the current shape. */
class BlobMorph(initial: BlobKind) {
    var from: FloatArray = Blobs.points(initial)
        private set
    var to: FloatArray = Blobs.points(initial)
        private set
    val progress = androidx.compose.animation.core.Animatable(1f)
    private var kind = initial

    suspend fun morphTo(target: BlobKind, spec: androidx.compose.animation.core.AnimationSpec<Float>, instant: Boolean) {
        if (target == kind) return
        kind = target
        from = Blobs.lerp(from, to, progress.value)
        to = Blobs.points(target)
        progress.snapTo(0f)
        if (instant) progress.snapTo(1f) else progress.animateTo(1f, spec)
    }

    fun build(path: Path, extent: Float, sizePx: Float) =
        Blobs.buildPath(path, from, to, progress.value, extent, sizePx)
}
