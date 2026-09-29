package app.elsewhere.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.elsewhere.R
import app.elsewhere.data.City
import app.elsewhere.data.Country
import app.elsewhere.ui.AppState
import app.elsewhere.ui.Card
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.flags.Flag
import app.elsewhere.ui.flags.FlagSize
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.layers.Keys
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.map.MapPreview
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.ChangeEffect
import app.elsewhere.ui.motion.animatedColor
import app.elsewhere.ui.motion.animatedFloat
import app.elsewhere.ui.motion.rememberMotionScope
import app.elsewhere.ui.motion.withMotionClock
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Callbacks that need the Activity (permissions, Settings intents). */
interface HomeActions {
    fun onCardAction(card: Card)
    fun onCardDismiss()
}

/** Insets as the prototype's paddings: real system bars, with 32dp top and 24dp bottom as minimums. */
@Composable
fun screenInsets(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = maxOf(top, 32.dp), bottom = maxOf(bottom, 24.dp))
}

@Composable
fun HomeScreen(state: AppState, stage: Stage, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = LocalElsewhereColors.current
    val city = state.city ?: return
    val country = state.country ?: return
    val insets = screenInsets()
    val home = stage.home

    Column(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = home.enterAlpha.value
                scaleX = home.enterScale.value; scaleY = home.enterScale.value
            }
            .padding(insets),
    ) {
        // App bar
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(stringResource(R.string.app_name), style = Type.Wordmark.copy(color = c.ink))
            Box(
                Modifier.size(48.dp).pressable(stage::openSettings, pressedFill = c.line, label = stringResource(R.string.settings)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Settings2, 24.dp, c.ink) }
        }

        LocationCard(state, city, country, stage)

        // The 148dp slot shared by the quick-switch strip and the status card.
        StatusSlot(state, stage, actions, Modifier.padding(top = 12.dp))

        // Start
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            val label = stringResource(if (state.active) R.string.stop else R.string.start)
            StartControl(
                active = state.active, blocked = state.blocked, label = label, home = home,
                onTap = {
                    when {
                        state.blocked -> stage.nope()
                        state.active -> stage.vm.stop()
                        else -> stage.vm.start()
                    }
                },
            )
            MainText(label, state.active, state.blocked, city, state.stored.startedAt)
        }
    }
}

@Composable
private fun LocationCard(state: AppState, city: City, country: Country, stage: Stage) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val home = stage.home
    val active = state.active
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier
                .then(stage.registry.modifier(Keys.HomeCard))
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(32.dp))
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                // Country chip
                Row(
                    Modifier
                        .graphicsLayer {
                            val p = home.chip.value
                            alpha = p
                            if (!home.titleReduced.value) translationY = Motion.Dp.TitleRise * (1 - p) * density
                        }
                        .then(stage.registry.modifier(Keys.HomeChip))
                        .height(40.dp)
                        .pressable(stage::openCountry, fill = c.bg, pressedFill = c.surfaceHigh)
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Flag(
                        country.code, FlagSize.Chip,
                        stage.registry.modifier(Keys.HomeFlag, country.code)
                            .graphicsLayer { alpha = if (stage.hidden[Keys.HomeFlag] == true) 0f else 1f },
                    )
                    BasicText(country.name, style = Type.Pill.copy(color = c.ink), maxLines = 1)
                    Icon(Icons.ChevronDown, 16.dp, c.ink)
                }
                LiveTag(active, rm)
            }
            Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp)) {
                Kicker(active)
                // City title button
                Row(
                    Modifier
                        .then(stage.registry.modifier(Keys.CityTitle))
                        .heightIn(min = 48.dp)
                        .pressable(stage::openCity, shape = RoundedCornerShape(0.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(
                        city.name,
                        style = Type.Display.copy(color = c.ink),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(stage.registry.modifier(Keys.CityTitleText, city.id))
                            .graphicsLayer {
                                val p = home.title.value
                                alpha = if (stage.hidden[Keys.CityTitleText] == true) 0f else p
                                if (!home.titleReduced.value) translationY = Motion.Dp.TitleRise * (1 - p) * density
                            },
                    )
                    Box(Modifier.size(28.dp).background(c.bg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.ChevronDown, 16.dp, c.ink)
                    }
                }
                BasicText(
                    coords(city),
                    style = Type.Coords.copy(color = c.inkMuted),
                    modifier = Modifier.height(18.dp).graphicsLayer { alpha = home.coords.value },
                )
            }
            MapPreview(stage.map, active, Modifier.padding(top = 12.dp), forceLoading = stage.map.forceLoading)
        }
    }
}

@Composable
private fun LiveTag(active: Boolean, rm: Boolean) {
    val c = LocalElsewhereColors.current
    val alpha = animatedFloat(if (active) 1f else 0f, if (rm) Motion.std(Ms.Reduced) else Motion.std(Ms.LiveFade))
    val scale = animatedFloat(if (active) 1f else 0.6f, Motion.pop(), instant = rm)
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha.value; scaleX = scale.value; scaleY = scale.value }
            .height(28.dp)
            .background(c.sageTint, RoundedCornerShape(999.dp))
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(c.sageStrong, CircleShape))
        BasicText(stringResource(R.string.live), style = Type.Tag.copy(color = c.onSageTint))
    }
}

@Composable
private fun Kicker(active: Boolean) {
    val c = LocalElsewhereColors.current
    val color = animatedColor(if (active) c.sageStrong else c.inkMuted, Motion.css(Ms.ColorShift))
    val fade = remember { Animatable(1f) }
    ChangeEffect(active) { fade.snapTo(0f); fade.animateTo(1f, Motion.std(Ms.Kicker)) }
    BasicText(
        stringResource(if (active) R.string.kicker_active else R.string.kicker_idle),
        style = Type.Kicker,
        color = { color.value },
        // Fixed line box: Compose rounds text heights up to whole pixels, Chrome keeps fractions.
        modifier = Modifier.height(18.dp).graphicsLayer { alpha = fade.value },
    )
}

/** `35.0116° N, 135.7681° E` */
fun coords(city: City): String {
    fun fmt(v: Double, p: String, n: String) = String.format(Locale.US, "%.4f", abs(v)) + "° " + if (v >= 0) p else n
    return fmt(city.lat, "N", "S") + ", " + fmt(city.lon, "E", "W")
}

@Composable
private fun MainText(label: String, active: Boolean, blocked: Boolean, city: City, startedAt: Long) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val p = remember { Animatable(1f) }
    var reducedRun by remember { mutableStateOf(false) }
    ChangeEffect(active) {
        reducedRun = rm
        p.snapTo(0f); p.animateTo(1f, Motion.decel(if (rm) Ms.Reduced else Ms.MainText))
    }
    Column(
        Modifier.graphicsLayer {
            alpha = p.value
            if (!reducedRun) translationY = Motion.Dp.MainTextRise * (1 - p.value) * density
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.Action.copy(color = c.ink))
        SubLine(active, blocked, city, startedAt)
    }
}

/** The only place the 1s tick is read, so only this Text recomposes each second. */
@Composable
private fun SubLine(active: Boolean, blocked: Boolean, city: City, startedAt: Long) {
    val c = LocalElsewhereColors.current
    val style = Type.Sub.copy(color = c.inkMuted)
    when {
        blocked -> BasicText(stringResource(R.string.sub_blocked), style = style)
        active -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(startedAt) { tickAligned(startedAt) { now = it } }
            val el = maxOf(0L, (now - startedAt) / 1000)
            val clock = String.format(Locale.US, "%02d:%02d", el / 60, el % 60)
            BasicText(stringResource(R.string.sub_active, clock), style = style)
        }
        else -> BasicText(stringResource(R.string.sub_idle, city.name), style = style)
    }
}

// ---------------------------------------------------------------- status slot

@Composable
private fun StatusSlot(state: AppState, stage: Stage, actions: HomeActions, modifier: Modifier) {
    val rm = LocalReducedMotion.current
    val rmNow by rememberUpdatedState(rm)
    val want = state.card
    val wantState = remember { mutableStateOf(want) }
    wantState.value = want
    var shown by remember { mutableStateOf<Card?>(null) }
    val cardIn = remember { Animatable(0f) }
    val cardOut = remember { Animatable(0f) }
    var cardReduced by remember { mutableStateOf(false) }
    val scope = rememberMotionScope()

    // syncCard / animCardIn: out (200 accel), then the next card in on spring gentle.
    LaunchedEffect(Unit) {
        var inJob: Job? = null
        snapshotFlow { wantState.value }.collect {
            while (wantState.value != shown) {
                val reduced = rmNow
                if (shown != null) {
                    inJob?.cancel()
                    cardReduced = reduced
                    cardOut.snapTo(0f)
                    withMotionClock { cardOut.animateTo(1f, if (reduced) Motion.accel(Ms.Reduced) else Motion.accel(Ms.CardOut)) }
                }
                shown = wantState.value
                cardOut.snapTo(0f)
                if (shown != null) {
                    cardReduced = reduced
                    cardIn.snapTo(0f)
                    inJob = scope.launch { cardIn.animateTo(1f, if (reduced) Motion.std(Ms.Reduced) else Motion.gentle()) }
                }
            }
        }
    }

    val hasCard = shown != null
    val stripAlpha = animatedFloat(if (hasCard) 0f else 1f, if (rm) Motion.std(Ms.Reduced) else Motion.std(Ms.StripFade))
    val stripY = animatedFloat(if (hasCard && !rm) Motion.Dp.StripDrop else 0f, Motion.decel(Ms.StripMove))

    Box(modifier.fillMaxWidth().height(148.dp)) {
        QuickSwitchStrip(
            state, stage, enabled = !hasCard,
            modifier = Modifier.graphicsLayer { alpha = stripAlpha.value; translationY = stripY.value * density },
        )
        val card = shown
        if (card != null) {
            StatusCard(
                card, actions,
                Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        val i = cardIn.value
                        val o = cardOut.value
                        var a = i.coerceIn(0f, 1f)
                        var ty = 0f
                        var s = 1f
                        if (!cardReduced) { ty = Motion.Dp.CardInRise * (1 - i); s = 0.98f + 0.02f * i }
                        if (o > 0f) {
                            a = 1f - o
                            if (!cardReduced) { ty = Motion.Dp.CardOutDrop * o; s = 1f - 0.02f * o }
                        }
                        val n = stage.home.nudgeScale()
                        alpha = a
                        translationY = ty * density
                        scaleX = s * n; scaleY = s * n
                    },
            )
        }
    }
}

@Composable
private fun StatusCard(card: Card, actions: HomeActions, modifier: Modifier) {
    val c = LocalElsewhereColors.current
    val sage = card == Card.Notifications
    val bg = if (sage) c.sageTint else c.accentTint
    val ink = if (sage) c.onSageTint else c.onAccentTint
    val btnBg = if (sage) c.sageBtn else c.accentStrong
    val btnInk = if (sage) c.onSageBtn else c.onAccent
    val (icon, title, body, action) = when (card) {
        Card.Location -> Quad(Icons.LocateOff, R.string.card_loc_title, R.string.card_loc_body, R.string.card_loc_action)
        Card.Mock -> Quad(Icons.Smartphone, R.string.card_mock_title, R.string.card_mock_body, R.string.card_mock_action)
        Card.Killed -> Quad(Icons.TriangleAlert, R.string.card_kill_title, R.string.card_kill_body, R.string.card_kill_action)
        Card.Notifications -> Quad(Icons.BellOff, R.string.card_notif_title, R.string.card_notif_body, R.string.card_notif_action)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(28.dp))
            .padding(start = 14.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).background(c.surfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, 20.dp, ink)
            }
            Column(Modifier.weight(1f).padding(top = 1.dp)) {
                BasicText(stringResource(title), style = Type.CardTitle.copy(color = ink))
                Spacer(Modifier.height(2.dp))
                BasicText(stringResource(body), style = Type.Supporting.copy(color = ink, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
            if (sage) {
                Box(
                    Modifier.height(48.dp).pressable(actions::onCardDismiss, pressedFill = c.press).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(stringResource(R.string.not_now), style = Type.Pill.copy(color = ink)) }
            }
            Box(
                Modifier.height(48.dp)
                    .pressable({ actions.onCardAction(card) }, fill = btnBg, pressedScale = 0.96f, scaleTransition = true)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(action), style = Type.Pill.copy(color = btnInk)) }
        }
    }
}

private data class Quad(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val body: Int, val action: Int)

// ---------------------------------------------------------------- quick switch

@Composable
private fun QuickSwitchStrip(state: AppState, stage: Stage, enabled: Boolean, modifier: Modifier) {
    val c = LocalElsewhereColors.current
    val data = stage.vm.cityData ?: return
    val favs = state.stored.favorites
    val favSet = favs.toHashSet()
    val chips = remember(favs, state.stored.recents, state.city?.id) {
        (favs.map { it to true } + state.stored.recents.filter { it.id !in favSet }.map { it.id to false })
            .filter { (id, _) -> id != state.city?.id && data.city(id) != null }
            .take(6)
            .map { (id, fav) -> data.city(id)!! to fav }
    }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.height(22.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(stringResource(R.string.quick_switch), style = Type.StripLabel.copy(color = c.inkMuted))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = enabled)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEach { (city, fav) ->
                Row(
                    Modifier.height(48.dp)
                        .pressable(
                            { stage.selectCity(city.id, swap = true) }, fill = c.surface, pressedFill = c.surfaceHigh,
                            pressedScale = 0.95f, scaleTransition = true, enabled = enabled,
                        )
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Flag(city.code, FlagSize.Strip)
                    BasicText(city.name, style = Type.Pill.copy(color = c.ink), maxLines = 1, softWrap = false)
                    if (fav) StarGlyph(14.dp, fill = c.accent, stroke = c.accent, strokeWidth = 2f)
                }
            }
            if (chips.isEmpty()) {
                Box(Modifier.height(48.dp).widthIn(max = 220.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    BasicText(stringResource(R.string.strip_empty), style = Type.Hint.copy(color = c.inkMuted))
                }
            }
            val allLabel = stringResource(R.string.all_label)
            Row(
                Modifier.height(48.dp)
                    .border(1.5.dp, c.line, RoundedCornerShape(999.dp))
                    .pressable(stage::openSheet, pressedFill = c.press, pressedScale = 0.95f, label = allLabel, enabled = enabled)
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.List, 18.dp, c.ink)
                BasicText(stringResource(R.string.all), style = Type.Pill.copy(color = c.ink))
            }
        }
    }
}

/** Lucide star with separate fill and stroke (prototype `<polygon>` stars). */
@Composable
fun StarGlyph(size: Dp, fill: Color?, stroke: Color, strokeWidth: Float, modifier: Modifier = Modifier) =
    StarGlyph(size, { fill }, { stroke }, strokeWidth, modifier)

@Composable
fun StarGlyph(size: Dp, fill: () -> Color?, stroke: () -> Color, strokeWidth: Float, modifier: Modifier = Modifier) {
    val path = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(Icons.StarPath).toPath() }
    Box(
        modifier.size(size).then(
            Modifier.graphicsLayer { }.then(
                Modifier.drawBehindStar(path, fill, stroke, strokeWidth),
            ),
        ),
    )
}

private fun Modifier.drawBehindStar(
    path: androidx.compose.ui.graphics.Path,
    fill: () -> Color?,
    stroke: () -> Color,
    strokeWidth: Float,
) = this.then(
    Modifier.drawBehind {
        val k = size.width / 24f
        scale(k, k, androidx.compose.ui.geometry.Offset.Zero) {
            fill()?.let { drawPath(path, it) }
            drawPath(
                path, stroke(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
    },
)
