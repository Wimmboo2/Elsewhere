package app.elsewhere.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.elsewhere.BuildConfig
import app.elsewhere.R
import app.elsewhere.data.ThemePref
import app.elsewhere.ui.AppState
import app.elsewhere.ui.components.SubAppBar
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.map.MapConfig
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.animatedColor
import app.elsewhere.ui.pickers.layerTop
import app.elsewhere.ui.pickers.listBottom
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type

interface SettingsActions {
    fun onTheme(pref: ThemePref)
    fun onMockRow()
    fun onReplaySetup()
    /** Debug builds only: long-press Version to force reduced motion. */
    fun onToggleReducedMotion()
}

@Composable
fun SettingsScreen(state: AppState, stage: Stage, actions: SettingsActions) {
    val c = LocalElsewhereColors.current
    Column(Modifier.fillMaxSize().padding(top = layerTop())) {
        SubAppBar(onBack = { stage.goBack() }) {
            BasicText(stringResource(R.string.settings), style = Type.Title.copy(color = c.ink))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = listBottom() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Section(stringResource(R.string.appearance)) {
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(28.dp)).padding(16.dp)) {
                    BasicText(stringResource(R.string.theme), style = Type.Label.copy(color = c.ink))
                    BasicText(stringResource(R.string.theme_hint), style = Type.Supporting.copy(color = c.inkMuted))
                    Segmented(
                        options = listOf(
                            ThemePref.System to stringResource(R.string.theme_system),
                            ThemePref.Light to stringResource(R.string.theme_light),
                            ThemePref.Dark to stringResource(R.string.theme_dark),
                        ),
                        selected = state.stored.theme,
                        onSelect = actions::onTheme,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Section(stringResource(R.string.setup)) {
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(28.dp)).padding(6.dp)) {
                    val set = state.env.mockSelected
                    SettingsRow(actions::onMockRow) {
                        Column(Modifier.weight(1f)) {
                            BasicText(stringResource(R.string.mock_app), style = Type.Label.copy(color = c.ink))
                            BasicText(
                                stringResource(if (set) R.string.mock_set_sub else R.string.mock_unset_sub),
                                style = Type.Supporting.copy(color = c.inkMuted),
                            )
                        }
                        Box(
                            Modifier.height(28.dp)
                                .background(if (set) c.sageTint else c.accentTint, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                stringResource(if (set) R.string.mock_tag_set else R.string.mock_tag_unset),
                                style = Type.Tag.copy(color = if (set) c.onSageTint else c.onAccentTint),
                            )
                        }
                    }
                    SettingsRow(actions::onReplaySetup) {
                        Column(Modifier.weight(1f)) {
                            BasicText(stringResource(R.string.setup_guide), style = Type.Label.copy(color = c.ink))
                            BasicText(stringResource(R.string.setup_guide_sub), style = Type.Supporting.copy(color = c.inkMuted))
                        }
                        Icon(Icons.ChevronRight, 20.dp, c.ink)
                    }
                }
            }
            Section(stringResource(R.string.about)) {
                Column(
                    Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column {
                        BasicText(stringResource(R.string.city_data), style = Type.AboutLabel.copy(color = c.ink))
                        UnderlinedLinks(
                            buildAnnotatedString {
                                append(stringResource(R.string.city_data_from)); append(" ")
                                withLink(LinkAnnotation.Url("https://www.geonames.org")) { append("GeoNames") }
                                append(stringResource(R.string.city_data_licensed)); append(" ")
                                withLink(LinkAnnotation.Url("https://creativecommons.org/licenses/by/4.0/")) { append("CC BY 4.0") }
                                append(".")
                            },
                        )
                    }
                    Column {
                        BasicText(stringResource(R.string.map_preview), style = Type.AboutLabel.copy(color = c.ink))
                        BasicText(MapConfig.ATTRIBUTION_LONG, style = Type.AboutBody.copy(color = c.inkMuted))
                    }
                    val versionMod = if (BuildConfig.DEBUG) {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = {}, onLongClick = actions::onToggleReducedMotion,
                        )
                    } else Modifier
                    Row(versionMod.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        BasicText(stringResource(R.string.version), style = Type.AboutLabel.copy(color = c.ink), modifier = Modifier.alignByBaseline())
                        BasicText(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = Type.AboutValue.copy(color = c.inkMuted), modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalElsewhereColors.current
    Column {
        BasicText(title, style = Type.Kicker.copy(color = c.inkMuted), modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun SettingsRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = LocalElsewhereColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .pressable(onClick, shape = RoundedCornerShape(22.dp), pressedFill = c.press)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 56dp track (4dp inset) with 48dp pill segments; selected = accentStrong / onAccent, colors 200ms CSS ease. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, track: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalElsewhereColors.current
    Row(
        modifier.fillMaxWidth().background(track ?: c.bg, RoundedCornerShape(999.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            val bg = animatedColor(if (on) c.accentStrong else c.accentStrong.copy(alpha = 0f), Motion.css(Ms.SegmentColor))
            val ink = animatedColor(if (on) c.onAccent else c.ink, Motion.css(Ms.SegmentColor))
            Box(
                Modifier.weight(1f).height(48.dp)
                    .drawBehind { drawRoundRect(bg.value, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)) }
                    .pressable({ onSelect(key) }),
                contentAlignment = Alignment.Center,
            ) { BasicText(label, style = Type.Pill, color = { ink.value }) }
        }
    }
}

/** Links in the ink color with an underline 3dp below the baseline (`text-underline-offset: 3px`). */
@Composable
private fun UnderlinedLinks(text: AnnotatedString) {
    val c = LocalElsewhereColors.current
    val uri = LocalUriHandler.current
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val styled = remember(text, c) {
        buildAnnotatedString {
            append(text)
            text.getLinkAnnotations(0, text.length).forEach { r ->
                val url = (r.item as LinkAnnotation.Url).url
                addLink(LinkAnnotation.Url(url, linkInteractionListener = { uri.openUri(url) }), r.start, r.end)
                addStyle(androidx.compose.ui.text.SpanStyle(color = c.ink), r.start, r.end)
            }
        }
    }
    BasicText(
        styled,
        style = Type.AboutBody.copy(color = c.inkMuted),
        onTextLayout = { layout[0] = it },
        modifier = Modifier.drawBehind {
            val l = layout[0] ?: return@drawBehind
            val w = 1.dp.toPx()
            styled.getLinkAnnotations(0, styled.length).forEach { r ->
                var i = r.start
                while (i < r.end) {
                    val line = l.getLineForOffset(i)
                    val lineEnd = minOf(r.end, l.getLineEnd(line, visibleEnd = true))
                    val x0 = l.getHorizontalPosition(i, true)
                    val x1 = l.getHorizontalPosition(lineEnd, true)
                    val y = l.getLineBaseline(line) + 3.dp.toPx() + w / 2
                    drawLine(c.ink, Offset(x0, y), Offset(x1, y), w)
                    i = maxOf(lineEnd, i + 1)
                }
            }
        },
    )
}
