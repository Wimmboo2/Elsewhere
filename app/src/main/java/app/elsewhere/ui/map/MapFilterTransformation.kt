package app.elsewhere.ui.map

import android.graphics.Bitmap
import app.elsewhere.ui.theme.MapFilter
import coil3.size.Size
import coil3.transform.Transformation

/**
 * `--e-map-filter` baked into each tile once, off the main thread. The four W3C filter-effects
 * matrices are applied in CSS order on non-premultiplied sRGB with a clamp after every step,
 * which is what Chrome does; a single composed ColorMatrix would skip the intermediate clamps and
 * brighten near-white tiles by a few levels.
 */
class MapFilterTransformation(private val filter: MapFilter) : Transformation() {
    override val cacheKey: String = "mapFilter:" + filter.steps.joinToString { "${it.first}${it.second}" }

    private val matrices: List<FloatArray> = filter.steps.map { (kind, v) -> matrixFor(kind, v) }

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val src = if (input.config == Bitmap.Config.ARGB_8888 && input.isMutable) input else input.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = apply(px[i])
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    private fun apply(c: Int): Int {
        val a = c ushr 24
        var r = ((c shr 16) and 0xFF) / 255f
        var g = ((c shr 8) and 0xFF) / 255f
        var b = (c and 0xFF) / 255f
        for (m in matrices) {
            val nr = m[0] * r + m[1] * g + m[2] * b + m[3]
            val ng = m[4] * r + m[5] * g + m[6] * b + m[7]
            val nb = m[8] * r + m[9] * g + m[10] * b + m[11]
            r = nr.coerceIn(0f, 1f); g = ng.coerceIn(0f, 1f); b = nb.coerceIn(0f, 1f)
        }
        return (a shl 24) or ((r * 255f + .5f).toInt() shl 16) or ((g * 255f + .5f).toInt() shl 8) or (b * 255f + .5f).toInt()
    }

    companion object {
        /** 3x4 row-major matrices (last column is the offset), from the Filter Effects spec. */
        fun matrixFor(kind: MapFilter.Kind, v: Float): FloatArray = when (kind) {
            MapFilter.Kind.Sepia -> {
                val s = (1 - v.coerceIn(0f, 1f))
                floatArrayOf(
                    0.393f + 0.607f * s, 0.769f - 0.769f * s, 0.189f - 0.189f * s, 0f,
                    0.349f - 0.349f * s, 0.686f + 0.314f * s, 0.168f - 0.168f * s, 0f,
                    0.272f - 0.272f * s, 0.534f - 0.534f * s, 0.131f + 0.869f * s, 0f,
                )
            }
            MapFilter.Kind.Saturate -> floatArrayOf(
                0.213f + 0.787f * v, 0.715f - 0.715f * v, 0.072f - 0.072f * v, 0f,
                0.213f - 0.213f * v, 0.715f + 0.285f * v, 0.072f - 0.072f * v, 0f,
                0.213f - 0.213f * v, 0.715f - 0.715f * v, 0.072f + 0.928f * v, 0f,
            )
            MapFilter.Kind.Contrast -> {
                val o = 0.5f - 0.5f * v
                floatArrayOf(v, 0f, 0f, o, 0f, v, 0f, o, 0f, 0f, v, o)
            }
            MapFilter.Kind.Brightness -> floatArrayOf(v, 0f, 0f, 0f, 0f, v, 0f, 0f, 0f, 0f, v, 0f)
        }
    }
}
