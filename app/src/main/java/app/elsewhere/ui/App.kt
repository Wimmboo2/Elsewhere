package app.elsewhere.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.elsewhere.data.ThemePref
import app.elsewhere.ui.flags.flagDraw
import app.elsewhere.ui.home.HomeActions
import app.elsewhere.ui.home.HomeScreen
import app.elsewhere.ui.layers.Flight
import app.elsewhere.ui.layers.LayerHost
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.rememberMotionScope
import app.elsewhere.ui.motion.withMotionClock
import app.elsewhere.ui.onboarding.Onboarding
import app.elsewhere.ui.onboarding.OnboardingActions
import app.elsewhere.ui.pickers.CityPicker
import app.elsewhere.ui.pickers.CountryPicker
import app.elsewhere.ui.settings.SettingsActions
import app.elsewhere.ui.settings.SettingsScreen
import app.elsewhere.ui.sheet.QuickSwitchSheet
import app.elsewhere.ui.theme.DarkColors
import app.elsewhere.ui.theme.LightColors
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type
import app.elsewhere.ui.theme.toColorScheme

/** Actions that need the Activity. */
interface AppActions : HomeActions, SettingsActions, OnboardingActions

/** Captures the current frame so a theme change can crossfade from it (`document.startViewTransition`). */
@Stable
class ThemeSnapshot(val layer: GraphicsLayer) {
    var image by mutableStateOf<ImageBitmap?>(null)
    val fade = Animatable(0f)

    suspend fun crossfade(reduced: Boolean, apply: () -> Unit) {
        val img = runCatching { layer.toImageBitmap() }.getOrNull()
        image = img
        fade.snapTo(1f)
        apply()
        withMotionClock { fade.animateTo(0f, Motion.std(if (reduced) Ms.Reduced else Ms.Theme)) }
        image = null
    }
}

val LocalThemeSnapshot = staticCompositionLocalOf<ThemeSnapshot?> { null }

@Composable
fun resolveDark(pref: ThemePref): Boolean = when (pref) {
    ThemePref.System -> isSystemInDarkTheme()
    ThemePref.Light -> false
    ThemePref.Dark -> true
}

@Composable
fun ElsewhereRoot(vm: AppViewModel, reduced: Boolean, actions: AppActions, stageOut: (Stage) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dark = resolveDark(state.stored.theme)
    val colors = if (dark) DarkColors else LightColors
    val scope = rememberMotionScope()
    val stage = remember { Stage(vm, scope) }
    stage.reduced = reduced
    stage.density = LocalDensity.current
    stageOut(stage)
    val snapshotLayer = rememberGraphicsLayer()
    val snapshot = remember { ThemeSnapshot(snapshotLayer) }
    val wrapped = remember(actions, snapshot) {
        object : AppActions by actions {
            override fun onTheme(pref: ThemePref) {
                if (pref == vm.state.value.stored.theme) return
                scope.launch { snapshot.crossfade(stage.reduced) { actions.onTheme(pref) } }
            }
        }
    }

    CompositionLocalProvider(
        LocalElsewhereColors provides colors,
        LocalReducedMotion provides reduced,
        LocalThemeSnapshot provides snapshot,
    ) {
        MaterialTheme(colorScheme = colors.toColorScheme()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { stage.rootSize = it.toSize() }
                    .drawWithContent {
                        snapshotLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(snapshotLayer)
                        snapshot.image?.let { drawImage(it, alpha = snapshot.fade.value) }
                    },
            ) {
                if (state.ready) AppContent(state, stage, wrapped)
            }
        }
    }
}

@Composable
private fun AppContent(state: AppState, stage: Stage, actions: AppActions) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val city = state.city ?: return
    val countryQuery by stage.vm.countryQuery.collectAsStateWithLifecycle()
    val cityQuery by stage.vm.cityQuery.collectAsStateWithLifecycle()
    val countryResults by stage.vm.countryResults.collectAsStateWithLifecycle()
    val cityResults by stage.vm.cityResults.collectAsStateWithLifecycle()

    stage.map.sync(city)
    BackHandler(enabled = state.sheet || state.stack.isNotEmpty() || (state.screen == Screen.Onboarding && (state.obStep > 0 || state.obReplay))) {
        if (!stage.goBack()) {
            if (state.screen == Screen.Onboarding) {
                if (state.obStep > 0) actions.onBack() else stage.vm.finishOnboarding()
            }
        }
    }

    // Home enter after onboarding (`animHome`).
    app.elsewhere.ui.motion.ChangeEffect(state.screen) { if (state.screen == Screen.Home) stage.enterHome() }

    Box(Modifier.fillMaxSize().graphicsLayer { }) {
        // Background + active ground tint (one alpha layer).
        Box(Modifier.fillMaxSize().drawWithContent { drawRect(c.bg) })
        val ground = app.elsewhere.ui.motion.animatedFloat(
            if (state.active) 1f else 0f, Motion.std(if (rm) Ms.Reduced else Ms.Ground),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ground.value }.drawWithContent { drawRect(c.activeGround) })

        if (state.screen == Screen.Home) HomeScreen(state, stage, actions)
        if (state.screen == Screen.Onboarding) Onboarding(state.obStep, actions)

        if (Layer.Country in state.stack) {
            LayerHost(stage.layers.getValue(Layer.Country), 20f) { CountryPicker(state, stage, countryQuery, countryResults) }
        }
        if (Layer.City in state.stack) {
            LayerHost(stage.layers.getValue(Layer.City), 21f) { CityPicker(state, stage, cityQuery, cityResults) }
        }
        if (Layer.Settings in state.stack) {
            LayerHost(stage.layers.getValue(Layer.Settings), 22f) { SettingsScreen(state, stage, actions) }
        }
        if (state.sheet) QuickSwitchSheet(state, stage)

        // Shared-element flights.
        Box(Modifier.fillMaxSize().zIndex(60f)) {
            stage.flights.forEach { f -> FlightView(f) }
        }
    }
}

@Composable
private fun FlightView(f: Flight) {
    val c = LocalElsewhereColors.current
    val d = LocalDensity.current
    val w = with(d) { f.to.width.toDp() }
    val h = with(d) { f.to.height.toDp() }
    val mod = Modifier
        .offset { IntOffset(f.to.left.toInt(), f.to.top.toInt()) }
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            val p = f.p.value
            translationX = (f.from.left - f.to.left) * (1 - p) + (f.to.left - f.to.left.toInt())
            translationY = (f.from.top - f.to.top) * (1 - p) + (f.to.top - f.to.top.toInt())
            if (f.text != null) {
                val k = f.from.height / f.to.height
                val s = k + (1 - k) * p
                scaleX = s; scaleY = s
            } else {
                scaleX = f.from.width / f.to.width + (1 - f.from.width / f.to.width) * p
                scaleY = f.from.height / f.to.height + (1 - f.from.height / f.to.height) * p
            }
        }
    if (f.text != null) {
        BasicText(f.text, style = Type.Display.copy(color = c.ink), maxLines = 1, softWrap = false, modifier = mod)
    } else if (f.flagCode != null) {
        Box(mod.requiredSize(w, h).flagDraw(f.flagCode, f.flagRadiusDp.dp))
    }
}
