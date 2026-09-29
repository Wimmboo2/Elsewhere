package app.elsewhere.ui.sheet

import android.content.res.Resources
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.elsewhere.R
import app.elsewhere.data.Recent
import app.elsewhere.ui.AppState
import app.elsewhere.ui.SheetTab
import app.elsewhere.ui.components.EmptyState
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.flags.Flag
import app.elsewhere.ui.flags.FlagSize
import app.elsewhere.ui.home.StarGlyph
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.pickers.CurrentTag
import app.elsewhere.ui.pickers.StarButton
import app.elsewhere.ui.pickers.stagger
import app.elsewhere.ui.settings.Segmented
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type
import kotlin.math.roundToLong

/** Scrim + Quick switch sheet. Tap the scrim, the close button, or drag down to dismiss. */
@Composable
fun BoxScope.QuickSwitchSheet(state: AppState, stage: Stage) {
    val c = LocalElsewhereColors.current
    val anim = stage.sheet
    val data = stage.vm.cityData ?: return
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val heightPx = remember { floatArrayOf(1f) }

    // Scrim
    Box(
        Modifier.fillMaxSize().zIndex(40f)
            .graphicsLayer { alpha = anim.scrim.value }
            .drawBehind { drawRect(c.scrim) }
            .clickable(remember { MutableInteractionSource() }, indication = null) { stage.closeSheet() },
    )

    val drag = rememberDraggableState { dy -> stage.dragSheet(dy / heightPx[0]) }
    val nested = remember(stage) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Sheet pulled down: scrolling back up first closes the gap.
                if (available.y < 0 && anim.offset.value > 0f) {
                    stage.dragSheet(available.y / heightPx[0]); return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    stage.dragSheet(available.y / heightPx[0]); return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (anim.offset.value > 0f) { stage.settleSheet(available.y / heightPx[0]); return available }
                return Velocity.Zero
            }
        }
    }

    val shadow = c.sheetShadow
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val top = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .zIndex(41f)
            .fillMaxWidth()
            .heightIn(max = 640.dp + maxOf(0.dp, navBottom - 24.dp))
            .graphicsLayer {
                heightPx[0] = size.height.coerceAtLeast(1f)
                translationY = anim.offset.value * size.height
                alpha = anim.alpha.value
            }
            .then(if (shadow.alpha > 0f) Modifier.dropShadow(top, Shadow(radius = cssBlurToShadowRadius(28f, density), color = shadow, offset = androidx.compose.ui.unit.DpOffset(0.dp, (-8).dp))) else Modifier)
            .drawBehind {
                val r = 32.dp.toPx()
                drawRoundRect(c.sheet, size = Size(size.width, size.height + r), cornerRadius = CornerRadius(r))
                if (c.edge.alpha > 0f) {
                    // border-top: 1px on a rounded box: a crescent that thins out along the corners
                    val one = 1.dp.toPx()
                    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height + r, CornerRadius(r))) }
                    val inner = Path().apply { addRoundRect(RoundRect(0f, one, size.width, size.height + r, CornerRadius(r, r - one))) }
                    drawPath(Path.combine(PathOperation.Difference, outer, inner), c.edge)
                }
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .nestedScroll(nested)
            .padding(top = 8.dp, bottom = maxOf(navBottom, 24.dp)),
    ) {
        // Handle + header are the drag area.
        Column(
            Modifier.draggable(
                drag, Orientation.Vertical,
                onDragStopped = { v -> stage.settleSheet(v / heightPx[0]) },
            ),
        ) {
            Box(
                Modifier.padding(top = 4.dp, bottom = 10.dp).align(Alignment.CenterHorizontally)
                    .width(36.dp).height(4.dp)
                    .drawBehind { drawRoundRect(c.inkMuted.copy(alpha = c.inkMuted.alpha * 0.45f), cornerRadius = CornerRadius(size.height / 2)) },
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(stringResource(R.string.quick_switch), style = Type.Title.copy(color = c.ink))
                Box(
                    Modifier.size(48.dp).pressable({ stage.closeSheet() }, pressedFill = c.press, label = stringResource(R.string.close)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.X, 22.dp, c.ink) }
            }
            Segmented(
                options = listOf(SheetTab.Fav to stringResource(R.string.tab_favorites), SheetTab.Recent to stringResource(R.string.tab_recent)),
                selected = state.sheetTab,
                onSelect = stage::setSheetTab,
                track = c.surface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val fav = state.sheetTab == SheetTab.Fav
        val rows: List<Pair<Int, Recent?>> = if (fav) state.stored.favorites.map { it to null } else state.stored.recents.map { it.id to it }
        val shown = rows.filter { data.city(it.first) != null }
        val res = LocalContext.current.resources
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 300.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
            itemsIndexed(shown, key = { _, it -> "${state.sheetTab}:${it.first}" }) { i, (id, recent) ->
                val city = data.city(id)!!
                val country = data.country(city.code)
                Row(
                    Modifier.stagger(stage.staggerSheet, i).fillMaxWidth().height(64.dp)
                        .pressable({ stage.pickFromSheet(id) }, shape = RoundedCornerShape(20.dp), pressedFill = c.line)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Flag(city.code, FlagSize.Header)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicText(
                                city.name, style = Type.Label.copy(color = c.ink), maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (id == state.city?.id) CurrentTag()
                        }
                        BasicText(country?.name ?: city.code, style = Type.Supporting.copy(color = c.inkMuted), maxLines = 1)
                    }
                    if (fav) {
                        StarButton(fav = true, label = stringResource(R.string.fav_remove_short), onToggle = { stage.vm.toggleFavorite(id) }, alwaysFilled = true)
                    } else if (recent != null) {
                        BasicText(ago(res, recent.time), style = Type.Count.copy(color = c.inkMuted), maxLines = 1, modifier = Modifier.padding(end = 14.dp))
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty:${state.sheetTab}") {
                    EmptyState(
                        title = stringResource(if (fav) R.string.sheet_fav_empty_title else R.string.sheet_recent_empty_title),
                        body = stringResource(if (fav) R.string.sheet_fav_empty_body else R.string.sheet_recent_empty_body),
                        action = stringResource(R.string.browse_cities),
                        onAction = { stage.browseFromSheet() },
                        blobColor = c.accentTint,
                        icon = { StarGlyph(38.dp, fill = null, stroke = c.onAccentTint, strokeWidth = 2.75f) },
                        bodyMaxWidth = 270.dp, topPadding = 28.dp, filledAction = true, iconOffset = 29.dp,
                    )
                }
            }
        }
    }
}

/**
 * CSS `box-shadow` blur B means a Gaussian with sigma B/2. Compose hands its shadow radius to
 * BlurMaskFilter, which Skia turns into sigma = 0.57735 * r + 0.5 (in px). Solve for r.
 */
private fun cssBlurToShadowRadius(blurDp: Float, density: Float): androidx.compose.ui.unit.Dp {
    val sigmaPx = blurDp / 2f * density
    return ((sigmaPx - 0.5f) / 0.57735f / density).dp
}

/** Prototype `ago()`. */
fun ago(res: Resources, t: Long, now: Long = System.currentTimeMillis()): String {
    val m = jsRound((now - t) / 60000.0)
    if (m < 1) return res.getString(R.string.ago_now)
    if (m < 60) return res.getString(R.string.ago_min, m.toInt())
    val h = jsRound(m / 60.0)
    if (h < 24) return res.getString(R.string.ago_h, h.toInt())
    if (h < 48) return res.getString(R.string.ago_yesterday)
    return res.getString(R.string.ago_days, jsRound(h / 24.0).toInt())
}

private fun jsRound(v: Double): Long = kotlin.math.floor(v + 0.5).roundToLong()
