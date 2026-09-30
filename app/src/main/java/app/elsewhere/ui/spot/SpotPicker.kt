package app.elsewhere.ui.spot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.elsewhere.R
import app.elsewhere.data.LatLon
import app.elsewhere.data.SpotSource
import app.elsewhere.ui.AppState
import app.elsewhere.ui.components.Pill
import app.elsewhere.ui.components.SubAppBar
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.home.coords
import app.elsewhere.ui.home.screenInsets
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.map.MapCamera
import app.elsewhere.ui.map.MapConfig
import app.elsewhere.ui.map.SlippyMap
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.animatedFloat
import app.elsewhere.ui.pickers.layerTop
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type

/**
 * "Pick your spot": drag the map so the pin sits on the exact building, then Set here.
 * Not in the prototype (added on request); built from its parts: sub app bar, 24dp map, pin, pills.
 */
@Composable
fun SpotPicker(state: AppState, stage: Stage) {
    val c = LocalElsewhereColors.current
    val city = state.city ?: return
    val spot = state.spot ?: return
    val camera = remember(city.id) { MapCamera(LatLon(spot.lat, spot.lon), 15.0) }
    val hint = animatedFloat(if (camera.touched) 0f else 1f, Motion.std(Ms.SkeletonOut))
    val insets = screenInsets()

    Column(Modifier.fillMaxSize().padding(top = layerTop(), bottom = insets.calculateBottomPadding())) {
        SubAppBar(onBack = { stage.goBack() }) {
            BasicText(stringResource(R.string.pick_spot), style = Type.Title.copy(color = c.ink))
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
        ) {
            SlippyMap(camera, pinColor = if (state.active) c.sageStrong else c.accent)
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = hint.value }
                    .height(28.dp).background(c.surfaceHigh, Pill).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(R.string.pick_hint), style = Type.Chip12.copy(color = c.inkMuted)) }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).height(20.dp)
                    .background(c.surfaceHigh, Pill).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(MapConfig.ATTRIBUTION_SHORT, style = Type.Attribution.copy(color = c.inkMuted)) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
            val here = camera.center
            BasicText(coords(here.lat, here.lon), style = Type.Coords.copy(color = c.ink))
            BasicText(
                stringResource(
                    when (spot.source) {
                        SpotSource.Custom -> R.string.spot_custom
                        SpotSource.Hotel -> R.string.spot_hotel
                        SpotSource.City -> R.string.spot_city
                    },
                ),
                style = Type.Supporting.copy(color = c.inkMuted),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (spot.source == SpotSource.Custom) {
                Box(
                    Modifier.height(56.dp).border(1.5.dp, c.line, Pill)
                        .pressable({ stage.resetSpot(camera) }, pressedFill = c.press, pressedScale = 0.97f, scaleTransition = true)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(stringResource(R.string.use_suggested), style = Type.Pill.copy(color = c.ink)) }
            }
            Box(
                Modifier.weight(1f).height(56.dp)
                    .pressable({ stage.setSpot(camera.center) }, fill = c.accentStrong, pressedScale = 0.97f, scaleTransition = true),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(R.string.set_here), style = Type.Button.copy(color = c.onAccent)) }
        }
    }
}
