package app.elsewhere.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lucide icons rebuilt from the exact SVG path data in the prototype markup
 * (24 x 24 viewBox, stroke 2.75, round caps and joins). No icon library.
 */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} ${rx}" +
            "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    private fun stroked(name: String, vararg d: String, width: Float = 2.75f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(
                    pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Settings2 = stroked("settings-2", "M20 7h-9", "M14 17H5", circle(17f, 17f, 3f), circle(7f, 7f, 3f))
    val ChevronDown = stroked("chevron-down", "m6 9 6 6 6-6")
    val ChevronRight = stroked("chevron-right", "m9 18 6-6-6-6")
    val ArrowLeft = stroked("arrow-left", "m12 19-7-7 7-7", "M19 12H5")
    val Search = stroked("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3")
    val SearchX = stroked("search-x", "m13.5 8.5-5 5", "m8.5 8.5 5 5", circle(11f, 11f, 8f), "m21 21-4.3-4.3")
    val X = stroked("x", "M18 6 6 18", "m6 6 12 12")
    val Check = stroked("check", "M20 6 9 17l-5-5")
    const val MapPinPath = "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"
    val MapPin = stroked("map-pin", MapPinPath, circle(12f, 10f, 3f))
    val Wrench = stroked(
        "wrench",
        "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
    )
    val List = stroked("list", "M3 12h.01", "M3 18h.01", "M3 6h.01", "M8 12h13", "M8 18h13", "M8 6h13")
    val LocateOff = stroked(
        "locate-off", "M2 12h3", "M19 12h3", "M12 2v3", "M12 19v3",
        "M7.11 7.11C5.83 8.39 5 10.1 5 12c0 3.87 3.13 7 7 7 1.9 0 3.61-.83 4.89-2.11",
        "M18.71 13.96c.19-.63.29-1.29.29-1.96 0-3.87-3.13-7-7-7-.67 0-1.33.1-1.96.29", "m2 2 20 20",
    )
    val Smartphone = stroked("smartphone", rect(5f, 2f, 14f, 20f, 2f), "M12 18h.01")
    val TriangleAlert = stroked(
        "triangle-alert",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01",
    )
    val BellOff = stroked(
        "bell-off", "M8.7 3A6 6 0 0 1 18 8a21.3 21.3 0 0 0 .6 5", "M17 17H3s3-2 3-9a4.67 4.67 0 0 1 .3-1.7",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0", "m2 2 20 20",
    )

    /** Lucide star polygon; filled/stroked per use, so it is exposed as path data. */
    const val StarPath = "M12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2z"

    /** Start glyph: filled navigation arrow, stroke 2, round join. */
    val Navigation: ImageVector = ImageVector.Builder("navigation", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M12 2 19 21 12 17 5 21 12 2z"), fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    /** Stop glyph: rect 4,4 16x16 rx 4.5, filled. */
    val Stop: ImageVector = ImageVector.Builder("stop", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(pathData = addPathNodes(rect(4f, 4f, 16f, 16f, 4.5f)), fill = SolidColor(Color.Black))
    }.build()

    fun star(fill: Boolean, strokeWidth: Float): ImageVector =
        ImageVector.Builder("star", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes(StarPath), fill = if (fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black), strokeLineWidth = strokeWidth, strokeLineJoin = StrokeJoin.Round,
                strokeLineCap = StrokeCap.Butt,
            )
        }.build()
}

/** Draws [icon] tinted with [color]; the color is read in the draw phase so animating it only redraws. */
@Composable
fun Icon(icon: ImageVector, size: Dp, color: () -> Color, modifier: Modifier = Modifier) {
    val painter = rememberVectorPainter(icon)
    Box(
        modifier.size(size).drawBehind {
            with(painter) { draw(this@drawBehind.size, colorFilter = ColorFilter.tint(color())) }
        },
    )
}

@Composable
fun Icon(icon: ImageVector, size: Dp, color: Color, modifier: Modifier = Modifier) =
    Icon(icon, size, { color }, modifier)
