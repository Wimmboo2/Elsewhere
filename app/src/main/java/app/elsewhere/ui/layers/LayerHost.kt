package app.elsewhere.ui.layers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.theme.LocalElsewhereColors

/** The prototype's `clip-path: inset(... round r)`: CSS scales radii down to fit the box. */
private class InsetClip(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = minOf(radius, rect.width / 2, rect.height / 2).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(rect, CornerRadius(r)))
    }
}

private fun lerp(a: Rect, b: Rect, t: Float) = Rect(
    Motion.lerp(a.left, b.left, t), Motion.lerp(a.top, b.top, t),
    Motion.lerp(a.right, b.right, t), Motion.lerp(a.bottom, b.bottom, t),
)

/** Share of the return timeline spent shrinking; the rest fades the layer (keyframe offset 0.85). */
private const val OUT_SHRINK = 0.85f

/**
 * A full-screen picker/settings layer. Container transforms clip it to a rounded rect lerped between
 * the source element and the window, with its background lerped from the home card surface to bg.
 */
@Composable
fun LayerHost(anim: LayerAnim, z: Float, content: @Composable BoxScope.() -> Unit) {
    val c = LocalElsewhereColors.current
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(z)
            .graphicsLayer {
                val full = Rect(0f, 0f, size.width, size.height)
                var a = anim.alpha.value
                when (anim.mode) {
                    LayerAnim.Mode.In -> {
                        val t = anim.t.value
                        clip = true
                        shape = InsetClip(lerp(anim.from, full, t), Motion.lerp(anim.fromRadius, 0f, t))
                    }
                    LayerAnim.Mode.Out -> {
                        val e = anim.t.value
                        val k = (e / OUT_SHRINK).coerceIn(0f, 1f)
                        clip = true
                        shape = InsetClip(lerp(full, anim.to, k), Motion.lerp(0f, anim.toRadius, k))
                        if (e > OUT_SHRINK) a *= (1f - (e - OUT_SHRINK) / (1f - OUT_SHRINK)).coerceIn(0f, 1f)
                    }
                    LayerAnim.Mode.None -> { clip = false; shape = RectangleShape }
                }
                alpha = if (anim.hidden) 0f else a
                translationX = anim.tx.value * density
            }
            .drawBehind {
                val bg = when (anim.mode) {
                    LayerAnim.Mode.In -> Motion.lerpColor(c.surface, c.bg, anim.t.value)
                    LayerAnim.Mode.Out -> Motion.lerpColor(c.bg, c.surface, (anim.t.value / OUT_SHRINK).coerceIn(0f, 1f))
                    LayerAnim.Mode.None -> c.bg
                }
                drawRect(bg)
            }
            .then(
                // Opaque to touches while shown; a leaving layer lets them through (pointer-events: none).
                if (anim.exiting || anim.hidden) Modifier
                else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            ),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = anim.content.value }, content = content)
    }
}
