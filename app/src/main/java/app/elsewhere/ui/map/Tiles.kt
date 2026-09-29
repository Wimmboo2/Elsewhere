package app.elsewhere.ui.map

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.elsewhere.ui.theme.MapFilter
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import okio.Path.Companion.toOkioPath

/** One raster tile placed in the map box (the prototype's `tiles()` output). */
data class TileSpot(val url: String, val x: Float, val y: Float)

/** The prototype's `tiles(id)`: the 3x3 tiles around the city, positioned so it sits at 50% x / 54% y. */
fun tilesFor(lat: Double, lon: Double, dark: Boolean, w: Float, h: Float): List<TileSpot> {
    val z = MapConfig.ZOOM
    val n = 1 shl z
    val xt = (lon + 180) / 360 * n
    val lr = lat * PI / 180
    val yt = (1 - ln(tan(lr) + 1 / cos(lr)) / PI) / 2 * n
    val t = MapConfig.TILE_DP
    val cx = xt * t
    val cy = yt * t
    val x0 = floor(xt).toInt()
    val y0 = floor(yt).toInt()
    val out = ArrayList<TileSpot>(9)
    for (j in -1..1) for (i in -1..1) {
        val tx = x0 + i
        val ty = y0 + j
        val x = (tx * t - cx + w / 2).roundJs()
        val y = (ty * t - cy + h * 0.54).roundJs()
        if (x > w || y > h || x + t < 0 || y + t < 0) continue
        out += TileSpot(MapConfig.tileUrl(dark, z, ty, ((tx % n) + n) % n), x.toFloat(), y.toFloat())
    }
    return out
}

/** JavaScript Math.round (halves round toward +infinity). */
private fun Double.roundJs(): Int = floor(this + 0.5).toInt()

/** Coil for network + disk cache; a small LRU of filtered bitmaps so cached tiles show on the first frame. */
object TileStore {
    private var loader: ImageLoader? = null
    private val memory = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("tiles").toOkioPath())
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build().also { loader = it }
    }

    fun cached(url: String): ImageBitmap? = memory.get(url)

    /** Loads, filters and caches a tile; null when it failed (offline). */
    suspend fun load(context: Context, url: String, filter: MapFilter): ImageBitmap? {
        memory.get(url)?.let { return it }
        val req = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .transformations(MapFilterTransformation(filter))
            .build()
        val res = loader(context).execute(req)
        if (res !is SuccessResult) return null
        val bmp = res.image.toBitmap().asImageBitmap()
        bmp.prepareToDraw()
        memory.put(url, bmp)
        return bmp
    }
}
