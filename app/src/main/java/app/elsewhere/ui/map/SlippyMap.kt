package app.elsewhere.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.elsewhere.data.LatLon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.rememberMotionScope
import app.elsewhere.ui.theme.LocalElsewhereColors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Camera of the spot picker: the map point under the pin (Web Mercator, 0..1) and a fractional zoom. */
@Stable
class MapCamera(at: LatLon, zoom: Double) {
    var mx by mutableDoubleStateOf(lonToX(at.lon))
    var my by mutableDoubleStateOf(latToY(at.lat))
    var zoom by mutableDoubleStateOf(zoom)
    /** True while a finger is down (the pin lifts). */
    var dragging by mutableStateOf(false)
    /** Set once the user has moved the map (the hint fades). */
    var touched by mutableStateOf(false)

    val center: LatLon get() = LatLon(yToLat(my), xToLon(mx))

    fun moveTo(at: LatLon) { mx = lonToX(at.lon); my = latToY(at.lat) }

    companion object {
        const val MIN_ZOOM = 11.0
        /** Esri's Canvas gray basemaps stop at level 16; beyond it the level-16 tiles are enlarged. */
        const val MAX_TILE_ZOOM = 16
        const val MAX_ZOOM = 17.5

        fun lonToX(lon: Double) = (lon + 180.0) / 360.0
        fun latToY(lat: Double): Double {
            val r = lat.coerceIn(-85.0, 85.0) * PI / 180
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
        }
        fun xToLon(x: Double) = x * 360.0 - 180.0
        fun yToLat(y: Double) = atan(sinh(PI * (1 - 2 * y))) * 180 / PI
    }
}

private val PinPath = PathParser().parsePathString(Icons.MapPinPath).toPath()
private val PinShadow = Color(20, 16, 12).copy(alpha = .28f)
private const val TILE = MapConfig.TILE_DP

/**
 * A pan/zoom raster map for picking an exact spot: the pin stays in the middle, the map moves under it.
 * Same tiles, warm filter and pin as the home preview. Tiles one level up fill in while a level loads.
 */
@Composable
fun SlippyMap(camera: MapCamera, pinColor: Color, modifier: Modifier = Modifier) {
    val c = LocalElsewhereColors.current
    val context = LocalContext.current
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val loading = remember { HashSet<String>() }
    val loadScope = rememberCoroutineScope()
    val gate = remember { Semaphore(6) }
    val motion = rememberMotionScope()
    val lift = remember { Animatable(0f) }
    val dark = c.isDark

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wDp = maxWidth.value.toDouble()
        val hDp = maxHeight.value.toDouble()

        // Load the tiles in view (and one level up for fill-in), a few at a time, never cancelled mid-flight.
        LaunchedEffect(dark, wDp, hDp) {
            snapshotFlow { visibleTiles(camera, wDp, hDp, dark) }.collect { wanted ->
                for (t in wanted) {
                    if (tiles.containsKey(t.url) || !loading.add(t.url)) continue
                    loadScope.launch {
                        gate.withPermit {
                            TileStore.load(context, t.url, c.mapFilter)?.let { tiles[t.url] = it }
                        }
                        loading.remove(t.url)
                    }
                }
                if (tiles.size > 120) {
                    val keep = wanted.mapTo(HashSet()) { it.url }
                    tiles.keys.filter { it !in keep }.take(tiles.size - 80).forEach { tiles.remove(it) }
                }
            }
        }
        LaunchedEffect(camera.dragging) {
            motion.launch {
                if (camera.dragging) lift.animateTo(1f, Motion.decel(Ms.StarOff))
                else lift.animateTo(0f, Motion.pin())
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        camera.touched = true
                        val d = density.toDouble()
                        val px = centroid.x / d - wDp / 2
                        val py = centroid.y / d - hDp / 2
                        val before = TILE * 2.0.pow(camera.zoom)
                        val nx = camera.mx + px / before
                        val ny = camera.my + py / before
                        camera.zoom = (camera.zoom + log2(zoomChange.toDouble())).coerceIn(MapCamera.MIN_ZOOM, MapCamera.MAX_ZOOM)
                        val after = TILE * 2.0.pow(camera.zoom)
                        camera.mx = (nx - px / after - pan.x / d / after).let { it - floor(it) }
                        camera.my = (ny - py / after - pan.y / d / after).coerceIn(0.02, 0.98)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        camera.dragging = true
                        do {
                            val e = awaitPointerEvent()
                        } while (e.changes.any { it.pressed })
                        camera.dragging = false
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { at ->
                        camera.touched = true
                        val d = density.toDouble()
                        val px = at.x / d - wDp / 2
                        val py = at.y / d - hDp / 2
                        val z0 = camera.zoom
                        val z1 = (z0 + 1).coerceAtMost(MapCamera.MAX_ZOOM)
                        val nx = camera.mx + px / (TILE * 2.0.pow(z0))
                        val ny = camera.my + py / (TILE * 2.0.pow(z0))
                        motion.launch {
                            val a = Animatable(0f)
                            a.animateTo(1f, Motion.decel(Ms.Glide)) {
                                val z = z0 + (z1 - z0) * value
                                val k = TILE * 2.0.pow(z)
                                // keep the tapped point under the finger while zooming in
                                camera.zoom = z
                                camera.mx = nx - px / k
                                camera.my = (ny - py / k).coerceIn(0.02, 0.98)
                            }
                        }
                    })
                }
                .drawBehind {
                    drawRect(c.skeleton)
                    val d = density.toDouble()
                    val z = camera.zoom
                    val zi = zoomLevel(z)
                    val s = 2.0.pow(z - zi)
                    val world = TILE * 2.0.pow(zi)
                    val cx = camera.mx * world
                    val cy = camera.my * world
                    val n = 1 shl zi
                    val (i0, i1, j0, j1) = range(cx, cy, s, wDp, hDp, n)
                    for (j in j0..j1) for (i in i0..i1) {
                        val x = ((i * TILE - cx) * s + wDp / 2) * d
                        val y = ((j * TILE - cy) * s + hDp / 2) * d
                        val size = TILE * s * d
                        val xi = ((i % n) + n) % n
                        val img = tiles[MapConfig.tileUrl(dark, zi, j, xi)]
                        if (img != null) {
                            drawImage(img, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(size.roundToInt() + 1, size.roundToInt() + 1))
                        } else if (zi > 0) {
                            // Fill in with the matching quarter of the parent tile.
                            val parent = tiles[MapConfig.tileUrl(dark, zi - 1, j / 2, xi / 2)] ?: continue
                            val half = parent.width / 2
                            drawImage(
                                parent,
                                srcOffset = IntOffset((xi % 2) * half, (j % 2) * half), srcSize = IntSize(half, half),
                                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(size.roundToInt() + 1, size.roundToInt() + 1),
                            )
                        }
                    }
                    // Pin in the middle; lifts while the map is being dragged, drops on spring pin.
                    val ax = size.width / 2
                    val ay = size.height / 2
                    val l = lift.value
                    val dp = density
                    val sh = 1f - 0.4f * l
                    scale(sh, sh, Offset(ax, ay)) {
                        drawOval(PinShadow, Offset(ax - 9f * dp, ay - 3f * dp), Size(18f * dp, 6f * dp))
                    }
                    translate(ax - 18f * dp, ay - 33f * dp - 10f * dp * l) {
                        scale(1.5f * dp, 1.5f * dp, Offset.Zero) {
                            drawPath(PinPath, pinColor)
                            drawPath(PinPath, c.surfaceHigh, style = Stroke(1.6f))
                            drawCircle(c.surfaceHigh, 3f, Offset(12f, 10f))
                        }
                    }
                },
        )
    }
}

private data class Range(val i0: Int, val i1: Int, val j0: Int, val j1: Int)

private fun zoomLevel(z: Double) = floor(z + 0.5).toInt().coerceIn(0, MapCamera.MAX_TILE_ZOOM)

private fun range(cx: Double, cy: Double, s: Double, w: Double, h: Double, n: Int) = Range(
    floor((cx - w / 2 / s) / TILE).toInt(), floor((cx + w / 2 / s) / TILE).toInt(),
    floor((cy - h / 2 / s) / TILE).toInt().coerceAtLeast(0), floor((cy + h / 2 / s) / TILE).toInt().coerceAtMost(n - 1),
)

private data class Wanted(val url: String)

/** Tiles needed for the current view, plus their parents for fill-in. */
private fun visibleTiles(camera: MapCamera, w: Double, h: Double, dark: Boolean): List<Wanted> {
    val zi = zoomLevel(camera.zoom)
    val s = 2.0.pow(camera.zoom - zi)
    val world = TILE * 2.0.pow(zi)
    val n = 1 shl zi
    val (i0, i1, j0, j1) = range(camera.mx * world, camera.my * world, s, w, h, n)
    val out = ArrayList<Wanted>()
    val parents = LinkedHashSet<String>()
    for (j in j0..j1) for (i in i0..i1) {
        val xi = ((i % n) + n) % n
        out += Wanted(MapConfig.tileUrl(dark, zi, j, xi))
        if (zi > 0) parents += MapConfig.tileUrl(dark, zi - 1, j / 2, xi / 2)
    }
    parents.forEach { out += Wanted(it) }
    return out
}
