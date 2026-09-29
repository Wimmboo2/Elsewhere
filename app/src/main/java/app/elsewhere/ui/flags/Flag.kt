package app.elsewhere.ui.flags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The four flag sizes in the prototype: width, height, corner radius. */
enum class FlagSize(val w: Dp, val h: Dp, val radius: Dp) {
    Row(32.dp, 22.dp, 5.dp),      // country rows
    Header(30.dp, 20.dp, 5.dp),   // city picker title, sheet rows
    Chip(24.dp, 16.dp, 4.dp),     // home country chip
    Strip(20.dp, 14.dp, 3.dp),    // quick-switch chips
}

/** Flat fill for a country without a flag (the prototype's `#999`). */
private val UnknownFlag = Color(0xFF999999)
/** `box-shadow: inset 0 0 0 1px rgba(0,0,0,.1)` */
private val Hairline = Color.Black.copy(alpha = .1f)

@Composable
fun Flag(code: String, size: FlagSize, modifier: Modifier = Modifier) {
    Box(modifier.size(size.w, size.h).flagDraw(code, size.radius))
}

@Composable
fun Modifier.flagDraw(code: String, radius: Dp): Modifier {
    val res = remember(code) { flagRes(code) }
    val painter: Painter? = if (res != 0) painterResource(res) else null
    return this.drawWithCache {
        val r = radius.toPx()
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
        val one = 1.dp.toPx()
        onDrawBehind {
            clipPath(clip) {
                if (painter == null) {
                    drawRect(UnknownFlag)
                } else {
                    // background-size: cover on a 4:3 flag
                    val ps = painter.intrinsicSize
                    val scale = maxOf(size.width / ps.width, size.height / ps.height)
                    val dw = ps.width * scale
                    val dh = ps.height * scale
                    translate((size.width - dw) / 2, (size.height - dh) / 2) {
                        with(painter) { draw(Size(dw, dh)) }
                    }
                }
                drawRoundRect(
                    Hairline, topLeft = Offset(one / 2, one / 2), size = Size(size.width - one, size.height - one),
                    cornerRadius = CornerRadius(r - one / 2), style = Stroke(one),
                )
            }
        }
    }
}
