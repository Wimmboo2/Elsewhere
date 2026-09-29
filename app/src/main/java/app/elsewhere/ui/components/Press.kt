package app.elsewhere.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.withMotionClock

/**
 * A touch target with the prototype's pressed look and no ripple.
 * On a phone a press shows both the `style-hover` and `style-active` values of the element,
 * so [pressedFill] comes from whichever of the two sets a background, and [pressedScale] from `style-active`.
 * [scaleTransition] is true when the element declares `transition: transform 160ms` (otherwise the scale is instant).
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    shape: Shape = CircleShape,
    fill: Color = Color.Transparent,
    pressedFill: Color? = null,
    pressedScale: Float = 1f,
    scaleTransition: Boolean = false,
    label: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    if (pressedScale != 1f) {
        LaunchedEffect(pressed) {
            val target = if (pressed) pressedScale else 1f
            if (scaleTransition) withMotionClock { scale.animateTo(target, Motion.std(Motion.Ms.PillPress)) } else scale.snapTo(target)
        }
    }
    return this
        .then(if (pressedScale != 1f) Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value } else Modifier)
        .drawBehind {
            val c = if (pressed && pressedFill != null) pressedFill else fill
            if (c.alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), c)
        }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}
