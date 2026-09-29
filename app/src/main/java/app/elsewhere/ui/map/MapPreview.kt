package app.elsewhere.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.withMotionClock
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.MapFilter
import app.elsewhere.ui.theme.Type
import kotlin.math.roundToInt

private const val LOADED = 1
private const val FAILED = -1
private val PinShadow = Color(20, 16, 12).copy(alpha = .28f)
private val PinPath = PathParser().parsePathString(Icons.MapPinPath).toPath()

/**
 * The home card's map: skeleton, two tile layers, pin with halo and shadow, attribution.
 * [active] turns the pin sage with a halo. [forceLoading] exists for screenshots of the loading state.
 */
@Composable
fun MapPreview(state: MapState, active: Boolean, modifier: Modifier = Modifier, forceLoading: Boolean = false) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val status = remember { mutableStateMapOf<String, Int>() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.skeleton),
    ) {
        val w = maxWidth.value
        val h = 156f
        val dark = c.isDark
        val frontCity = state.city(state.front)
        val frontTiles = remember(frontCity?.id, dark, w) {
            frontCity?.let { tilesFor(it.lat, it.lon, dark, w, h) } ?: emptyList()
        }
        val loaded = frontTiles.any { status[it.url] == LOADED }
        val errors = frontTiles.count { status[it.url] == FAILED }
        val showSkeleton = forceLoading || !loaded
        val offline = !forceLoading && !loaded && frontTiles.isNotEmpty() && errors >= frontTiles.size

        // Skeleton: blocks pulse while shown, the whole layer fades out once tiles arrive.
        val skelOp = remember { Animatable(1f) }
        LaunchedEffect(showSkeleton) {
            withMotionClock { skelOp.animateTo(if (showSkeleton) 1f else 0f, Motion.std(Ms.SkeletonOut)) }
        }
        var pulse by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(showSkeleton, rm) {
            pulse = 1f
            if (!showSkeleton || rm) return@LaunchedEffect
            withMotionClock {
                var t0 = -1L
                while (true) {
                    val now = withFrameMillis { it }
                    if (t0 < 0) t0 = now
                    val cycle = (now - t0) / Ms.SkeletonPulse
                    val f = ((now - t0) % Ms.SkeletonPulse).toFloat() / Ms.SkeletonPulse
                    val dir = if (cycle % 2 == 0L) f else 1f - f
                    pulse = 1f - 0.55f * Motion.Standard.transform(dir)
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = skelOp.value }) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = pulse }.drawBehind {
                    val d = density
                    val block = c.skeleton2
                    fun bar(l: Float, t: Float, bw: Float, bh: Float, deg: Float) {
                        rotate(deg, Offset((l + bw / 2) * d, (t + bh / 2) * d)) {
                            drawRoundRect(block, Offset(l * d, t * d), Size(bw * d, bh * d), CornerRadius(minOf(bw, bh) / 2 * d))
                        }
                    }
                    bar(-30f, 62f, 300f, 14f, -8f)
                    bar(210f, -20f, 14f, 210f, 16f)
                    drawRoundRect(block, Offset(34f * d, 98f * d), Size(76f * d, 40f * d), CornerRadius(20f * d))
                    drawRoundRect(block, Offset(size.width - (26f + 66f) * d, 18f * d), Size(66f * d, 38f * d), CornerRadius(20f * d))
                },
            )
            if (offline) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp).height(28.dp)
                        .background(c.surfaceHigh, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(androidx.compose.ui.res.stringResource(app.elsewhere.R.string.map_offline), style = Type.Chip12.copy(color = c.inkMuted)) }
            }
        }

        // Tiles (hidden entirely while forced loading, like `tilesOp`).
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (forceLoading) 0f else 1f }) {
            for (slot in 0..1) {
                val city = state.city(slot) ?: continue
                val tiles = remember(city.id, dark, w) { tilesFor(city.lat, city.lon, dark, w, h) }
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val p = state.glide.value
                        val isFront = slot == state.front
                        transformOrigin = TransformOrigin.Center
                        if (state.glideReduced) {
                            alpha = if (isFront) p else 1f - p
                        } else if (isFront) {
                            alpha = p.coerceIn(0f, 1f)
                            translationX = state.vx * (1 - p) * density
                            translationY = state.vy * (1 - p) * density
                            val s = 0.96f + 0.04f * p
                            scaleX = s; scaleY = s
                        } else {
                            alpha = (1f - p).coerceIn(0f, 1f)
                            translationX = -state.vx * p * density
                            translationY = -state.vy * p * density
                            val s = 1f + 0.04f * p
                            scaleX = s; scaleY = s
                        }
                    },
                ) {
                    tiles.forEach { spot -> Tile(spot, c.mapFilter, status) }
                }
            }
        }

        // Pin, anchored at 50% / 54%.
        val pinColor = remember { Animatable(0f) }
        LaunchedEffect(active) { withMotionClock { pinColor.animateTo(if (active) 1f else 0f, Motion.css(Ms.ColorShift)) } }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = density
                val ax = size.width * 0.5f
                val ay = size.height * 0.54f
                val p = state.pin.value
                // halo (opacity transition 300ms, CSS ease)
                if (pinColor.value > 0f) drawCircle(c.sage.copy(alpha = 0.3f * pinColor.value), 30f * d, Offset(ax, ay))
                val shadowAlpha = p.coerceIn(0f, 1f)
                val pinAlpha = p.coerceIn(0f, 1f)
                if (state.pinReduced) {
                    drawOval(PinShadow, Offset(ax - 9f * d, ay - 3f * d), Size(18f * d, 6f * d))
                } else {
                    val s = 0.2f + 0.8f * p
                    scale(s, s, Offset(ax, ay)) {
                        drawOval(PinShadow.copy(alpha = PinShadow.alpha * shadowAlpha), Offset(ax - 9f * d, ay - 3f * d), Size(18f * d, 6f * d))
                    }
                }
                val dy = if (state.pinReduced) 0f else -40f * (1 - p) * d
                val fill = Motion.lerpColor(c.accent, c.sageStrong, pinColor.value)
                translate(ax - 18f * d, ay - 33f * d + dy) {
                    scale(1.5f * d, 1.5f * d, Offset.Zero) {
                        val a = pinAlpha
                        drawPath(PinPath, fill.copy(alpha = fill.alpha * a))
                        drawPath(PinPath, c.surfaceHigh.copy(alpha = a), style = Stroke(1.6f))
                        drawCircle(c.surfaceHigh.copy(alpha = a), 3f, Offset(12f, 10f))
                    }
                }
            },
        )

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).height(20.dp)
                .background(c.surfaceHigh, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(MapConfig.ATTRIBUTION_SHORT, style = Type.Attribution.copy(color = c.inkMuted)) }
    }
}

@Composable
private fun Tile(spot: TileSpot, filter: MapFilter, status: SnapshotStateMap<String, Int>) {
    val context = LocalContext.current
    val cached = remember(spot.url) { TileStore.cached(spot.url) }
    var bmp by remember(spot.url) { mutableStateOf<ImageBitmap?>(cached) }
    val alpha = remember(spot.url) { Animatable(if (cached != null) 1f else 0f) }
    LaunchedEffect(spot.url) {
        if (bmp != null) { status[spot.url] = LOADED; return@LaunchedEffect }
        val b = TileStore.load(context, spot.url, filter)
        if (b == null) { status[spot.url] = FAILED; return@LaunchedEffect }
        bmp = b
        status[spot.url] = LOADED
        withMotionClock { alpha.animateTo(1f, Motion.std(Ms.TileFade)) }
    }
    val px = with(LocalDensity.current) { MapConfig.TILE_DP.dp.roundToPx() }
    Canvas(
        Modifier
            .offset { IntOffset((spot.x * density).roundToInt(), (spot.y * density).roundToInt()) }
            .size(MapConfig.TILE_DP.dp)
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        bmp?.let { drawImage(it, dstSize = IntSize(px, px)) }
    }
}
