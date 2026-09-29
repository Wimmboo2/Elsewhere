package app.elsewhere.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.elsewhere.R
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.home.screenInsets
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.animatedColor
import app.elsewhere.ui.motion.withMotionClock
import app.elsewhere.ui.shape.BlobKind
import app.elsewhere.ui.shape.BlobMorph
import app.elsewhere.ui.theme.ElsewhereColors
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type
import kotlinx.coroutines.launch

/** The prototype's `OB` + `OBV` tables. */
private class Step(
    val title: Int, val body: Int, val primary: Int, val chips: List<Int>?,
    val shape: BlobKind, val rot: Float, val satX: Float, val satY: Float, val satScale: Float,
    val fill: (ElsewhereColors) -> Color, val ink: (ElsewhereColors) -> Color, val sat: (ElsewhereColors) -> Color,
)

private val Steps = listOf(
    Step(
        R.string.ob1_title, R.string.ob1_body, R.string.ob1_primary, null,
        BlobKind.Sun, 0f, 190f, 14f, 1f, { it.accent }, { it.onAccent }, { it.sage },
    ),
    Step(
        R.string.ob2_title, R.string.ob2_body, R.string.ob2_primary, listOf(R.string.ob2_c1, R.string.ob2_c2, R.string.ob2_c3),
        BlobKind.Squircle, -8f, 10f, 192f, 0.8f, { it.sage }, { it.onSage }, { it.accent },
    ),
    Step(
        R.string.ob3_title, R.string.ob3_body, R.string.ob3_primary, listOf(R.string.ob3_c1, R.string.ob3_c2, R.string.ob3_c3),
        BlobKind.Cookie, 20f, 16f, 20f, 1.1f, { it.peach }, { it.onPeach }, { it.sage },
    ),
)

interface OnboardingActions {
    fun onPrimary(step: Int)
    fun onBack()
    fun onSkip()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(step: Int, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val s = Steps[step]
    var prevStep by remember { mutableIntStateOf(step) }
    val dir = if (step >= prevStep) 1 else -1

    // Shape morph 400 decel, rotation 420 decel, fill 300 standard.
    val morph = remember { BlobMorph(s.shape) }
    val rot = remember { Animatable(s.rot) }
    val fill = animatedColor(s.fill(c), if (rm) Motion.std(Ms.Reduced) else Motion.std(Ms.ColorShift))
    val ink = animatedColor(s.ink(c), if (rm) Motion.std(Ms.Reduced) else Motion.css(Ms.ColorShift))
    val satColor = animatedColor(s.sat(c), if (rm) Motion.std(Ms.Reduced) else Motion.css(Ms.ColorShift))
    val sat = remember { Animatable(Offset(s.satX, s.satY), Offset.VectorConverter) }
    val satScale = remember { Animatable(s.satScale) }
    val text = remember { Animatable(1f) }
    var textDir by remember { mutableIntStateOf(1) }
    var textReduced by remember { mutableStateOf(false) }
    val iconAlpha = remember { List(3) { i -> Animatable(if (i == step) 1f else 0f) } }
    val iconPop = remember { Animatable(1f) }

    LaunchedEffect(step) {
        if (step == prevStep) return@LaunchedEffect
        val d = if (step > prevStep) 1 else -1
        prevStep = step
        withMotionClock {
            launch { morph.morphTo(s.shape, Motion.decel(Ms.ObMorph), instant = rm) }
            launch { if (rm) rot.snapTo(s.rot) else rot.animateTo(s.rot, Motion.decel(Ms.ObRotate)) }
            launch { if (rm) sat.snapTo(Offset(s.satX, s.satY)) else sat.animateTo(Offset(s.satX, s.satY), Motion.gentle()) }
            launch { if (rm) satScale.snapTo(s.satScale) else satScale.animateTo(s.satScale, Motion.gentle()) }
            iconAlpha.forEachIndexed { i, a -> launch { a.animateTo(if (i == step) 1f else 0f, Motion.css(Ms.ObIconFade)) } }
            if (!rm) launch { iconPop.snapTo(0f); iconPop.animateTo(1f, Motion.pop()) }
            launch {
                textDir = d; textReduced = rm
                text.snapTo(0f)
                text.animateTo(1f, Motion.decel(if (rm) Ms.Reduced else Ms.ObText))
            }
        }
    }

    val path = remember { Path() }
    val insets = screenInsets()
    Column(modifier.fillMaxSize().zIndex(10f).background(c.bg).padding(insets)) {
        // Progress + Skip
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..3f) }) {
                ProgressDots(step, c)
            }
            Box(
                Modifier.height(48.dp).pressable(actions::onSkip, pressedFill = c.press).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(R.string.skip), style = Type.Skip.copy(color = c.ink)) }
        }

        // Shape
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(250.dp)) {
                Box(
                    Modifier.size(250.dp).drawBehind {
                        morph.build(path, 1.3f, size.width)
                        rotate(rot.value) { drawPath(path, fill.value) }
                    },
                )
                Box(
                    Modifier.size(44.dp).graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        translationX = sat.value.x * density
                        translationY = sat.value.y * density
                        scaleX = satScale.value; scaleY = satScale.value
                    }.drawBehind { drawCircle(satColor.value) },
                )
                Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                    val icons = listOf(Icons.MapPin to 72, Icons.Wrench to 68, Icons.Check to 72)
                    icons.forEachIndexed { i, (icon, size) ->
                        Icon(
                            icon, size.dp, { ink.value },
                            Modifier.graphicsLayer {
                                val p = if (i == step) iconPop.value else 1f
                                alpha = if (i == step && iconPop.value < 1f) p.coerceIn(0f, 1f) else iconAlpha[i].value
                                val sc = 0.4f + 0.6f * p
                                scaleX = sc; scaleY = sc
                                rotationZ = -20f * (1 - p)
                            },
                        )
                    }
                }
            }
        }

        // Text
        Column(
            Modifier.fillMaxWidth().heightIn(min = 238.dp).padding(horizontal = 28.dp)
                .graphicsLayer {
                    alpha = text.value
                    if (!textReduced) translationX = textDir * Motion.Dp.ObText * (1 - text.value) * density
                },
        ) {
            BasicText(
                stringResource(s.title),
                style = Type.ObTitle.copy(color = c.ink, lineBreak = LineBreak.Heading),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            BasicText(
                stringResource(s.body),
                style = Type.BodyLarge.copy(color = c.inkMuted, lineBreak = LineBreak.Paragraph),
                modifier = Modifier.widthIn(max = 340.dp),
            )
            s.chips?.let { chips ->
                FlowRow(
                    Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    chips.forEachIndexed { i, res ->
                        val last = i == chips.lastIndex
                        Box(
                            Modifier.height(32.dp)
                                .background(if (last) c.accentTint else c.surface, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { BasicText(stringResource(res), style = Type.Crumb.copy(color = if (last) c.onAccentTint else c.ink), maxLines = 1, softWrap = false) }
                        if (!last) Icon(Icons.ChevronRight, 14.dp, c.inkMuted)
                    }
                }
            }
        }

        // Actions
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                Box(
                    Modifier.size(56.dp).pressable(actions::onBack, fill = c.surface, pressedScale = 0.94f, label = stringResource(R.string.back)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.ArrowLeft, 22.dp, c.ink) }
            }
            Box(
                Modifier.weight(1f).height(56.dp)
                    .pressable({ actions.onPrimary(step) }, fill = c.accentStrong, pressedScale = 0.97f, scaleTransition = true),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(s.primary), style = Type.Button.copy(color = c.onAccent)) }
        }
    }
}

/**
 * Progress pills (8dp, current 24dp, 6dp gaps). CSS animates `width`; here the three pills are drawn in
 * one draw pass at their animated widths and positions, so nothing re-measures while they move.
 */
@Composable
private fun ProgressDots(step: Int, c: ElsewhereColors) {
    val widths = remember { List(3) { i -> Animatable(if (i == step) 24f else 8f) } }
    LaunchedEffect(step) {
        withMotionClock { widths.forEachIndexed { i, w -> launch { w.animateTo(if (i == step) 24f else 8f, Motion.decel(Ms.ObPill)) } } }
    }
    val colors = List(3) { i -> animatedColor(if (i <= step) c.accentStrong else c.blocked, Motion.css(Ms.ObDotColor)) }
    Box(
        Modifier.size(width = (24 + 8 + 8 + 6 * 2).dp, height = 8.dp).drawBehind {
            var x = 0f
            val h = size.height
            for (i in 0..2) {
                val w = widths[i].value * density
                drawRoundRect(colors[i].value, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
                x += w + 6 * density
            }
        },
    )
}
