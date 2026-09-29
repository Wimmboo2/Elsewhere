package app.elsewhere.ui.pickers

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.elsewhere.R
import app.elsewhere.data.City
import app.elsewhere.data.Country
import app.elsewhere.ui.AppState
import app.elsewhere.ui.Results
import app.elsewhere.ui.components.EmptyState
import app.elsewhere.ui.components.NoResultsIcon
import app.elsewhere.ui.components.SearchField
import app.elsewhere.ui.components.SubAppBar
import app.elsewhere.ui.components.pressable
import app.elsewhere.ui.flags.Flag
import app.elsewhere.ui.flags.FlagSize
import app.elsewhere.ui.home.StarGlyph
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.layers.Keys
import app.elsewhere.ui.layers.Registry
import app.elsewhere.ui.layers.Stage
import app.elsewhere.ui.layers.StaggerClock
import app.elsewhere.ui.motion.LocalReducedMotion
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import app.elsewhere.ui.motion.animatedColor
import app.elsewhere.ui.motion.rememberMotionScope
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type
import kotlinx.coroutines.launch

/** Top inset (32dp minimum) for full-screen layers. */
@Composable
fun layerTop(): Dp = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 32.dp)

/** List bottom padding: the prototype's 32dp, grown when the navigation bar is taller than its 24dp gesture area. */
@Composable
fun listBottom(): Dp = 32.dp + maxOf(0.dp, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() - 24.dp)

/** Rows rise 12dp and fade in, staggered (first 12 rows). */
fun Modifier.stagger(clock: StaggerClock, index: Int): Modifier = graphicsLayer {
    val p = clock.progress(index)
    alpha = p
    translationY = Motion.Dp.StaggerRise * (1 - p) * density
}

/** Sticky search header with the divider that fades in once the list scrolls past 4dp. */
@Composable
private fun SearchHeader(list: LazyListState, content: @Composable () -> Unit) {
    val c = LocalElsewhereColors.current
    val px = with(LocalDensity.current) { 4.dp.toPx() }
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > px } }
    val line = animatedColor(if (scrolled) c.line else Color.Transparent, Motion.css(Ms.Divider))
    Box(
        Modifier.fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(line.value, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp + 1.dp),
    ) { content() }
}

// ---------------------------------------------------------------- country

@Composable
fun CountryPicker(state: AppState, stage: Stage, query: String, results: Results<Country>) {
    val c = LocalElsewhereColors.current
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(top = layerTop())) {
        SubAppBar(onBack = { stage.goBack() }) {
            BasicText(stringResource(R.string.choose_country), style = Type.Title.copy(color = c.ink))
        }
        SearchHeader(list) {
            val ph = stringResource(R.string.search_countries)
            SearchField(query, stage.vm::setCountryQuery, ph, ph)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = listBottom()),
        ) {
            itemsIndexed(results.items, key = { _, it -> it.code }) { i, country ->
                CountryRow(country, current = country.code == state.city?.code, Modifier.stagger(stage.staggerCountry, i)) { flag ->
                    stage.pickCountry(country.code, flag?.let { Registry.rectOf(it) })
                }
            }
            if (results.items.isEmpty() && results.query.isNotBlank()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.country_empty_title, results.query.trim()),
                        body = stringResource(R.string.country_empty_body),
                        action = stringResource(R.string.clear_search),
                        onAction = { stage.vm.setCountryQuery("") },
                        blobColor = c.surface, icon = { NoResultsIcon() }, bodyMaxWidth = 260.dp,
                        topPadding = 56.dp, filledAction = false, iconOffset = 30.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, current: Boolean, modifier: Modifier, onClick: (LayoutCoordinates?) -> Unit) {
    val c = LocalElsewhereColors.current
    val flag = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable({ onClick(flag[0]) }, shape = RoundedCornerShape(20.dp), pressedFill = c.line)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Flag(country.code, FlagSize.Row, Modifier.onGloballyPositioned { flag[0] = it })
        BasicText(country.name, style = Type.Label.copy(color = c.ink), modifier = Modifier.weight(1f))
        if (current) Icon(Icons.Check, 20.dp, c.accent)
        BasicText(
            pluralStringResource(R.plurals.cities_count, country.cities.size, country.cities.size),
            style = Type.Count.copy(color = c.inkMuted),
        )
    }
}

// ---------------------------------------------------------------- city

@Composable
fun CityPicker(state: AppState, stage: Stage, query: String, results: Results<City>) {
    val c = LocalElsewhereColors.current
    val data = stage.vm.cityData ?: return
    val country = data.country(state.pickCode) ?: state.country ?: return
    val list = rememberLazyListState()
    LaunchedEffect(state.pickCode) { list.scrollToItem(0) }
    val favs = state.stored.favorites.toHashSet()
    Column(Modifier.fillMaxSize().padding(top = layerTop())) {
        SubAppBar(onBack = { stage.goBack() }) {
            Flag(
                country.code, FlagSize.Header,
                Modifier
                    .padding(start = 4.dp, end = 8.dp)
                    .then(stage.registry.modifier(Keys.CityHeaderFlag, country.code))
                    .graphicsLayer { alpha = if (stage.hidden[Keys.CityHeaderFlag] == true) 0f else 1f },
            )
            BasicText(
                country.name, style = Type.Title.copy(color = c.ink),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
        SearchHeader(list) {
            SearchField(
                query, stage.vm::setCityQuery,
                stringResource(R.string.search_cities_in, country.name), stringResource(R.string.search_cities),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = listBottom()),
        ) {
            itemsIndexed(results.items, key = { _, it -> it.id }) { i, city ->
                CityRow(
                    city, current = city.id == state.city?.id, fav = city.id in favs,
                    modifier = Modifier.stagger(stage.staggerCity, i),
                    onClick = { name -> stage.finishCity(city.id, name?.let { Registry.rectOf(it) }) },
                    onStar = { stage.vm.toggleFavorite(city.id) },
                )
            }
            if (results.items.isEmpty() && results.query.isNotBlank()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.city_empty_title, results.query.trim()),
                        body = stringResource(R.string.city_empty_body),
                        action = stringResource(R.string.clear_search),
                        onAction = { stage.vm.setCityQuery("") },
                        blobColor = c.surface, icon = { NoResultsIcon() }, bodyMaxWidth = 270.dp,
                        topPadding = 56.dp, filledAction = false, iconOffset = 30.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CityRow(
    city: City,
    current: Boolean,
    fav: Boolean,
    modifier: Modifier,
    onClick: (LayoutCoordinates?) -> Unit,
    onStar: () -> Boolean,
) {
    val c = LocalElsewhereColors.current
    val name = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable({ onClick(name[0]) }, shape = RoundedCornerShape(20.dp), pressedFill = c.line)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    city.name, style = Type.Label.copy(color = c.ink), maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).onGloballyPositioned { name[0] = it },
                )
                if (current) CurrentTag()
            }
            BasicText(city.region.ifEmpty { app.elsewhere.data.countryName(city.code) }, style = Type.Supporting.copy(color = c.inkMuted), maxLines = 1)
        }
        StarButton(
            fav = fav,
            label = stringResource(if (fav) R.string.fav_remove else R.string.fav_add, city.name),
            onToggle = onStar,
        )
    }
}

@Composable
fun CurrentTag() {
    val c = LocalElsewhereColors.current
    Box(
        Modifier.height(22.dp).background(c.accentTint, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(stringResource(R.string.current), style = Type.TagSmall.copy(color = c.onAccentTint)) }
}

/**
 * 48dp star button (`toggleFav`): on = star scale 0.5 -> 1 on spring pop + accent burst ring;
 * off = scale 0.8 -> 1 in 200 standard. Fill switches at once (CSS cannot interpolate
 * `none` <-> color), the stroke color runs its 150ms transition.
 */
@Composable
fun StarButton(fav: Boolean, label: String, onToggle: () -> Boolean, alwaysFilled: Boolean = false) {
    val c = LocalElsewhereColors.current
    val rm = LocalReducedMotion.current
    val scope = rememberMotionScope()
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val filled = fav || alwaysFilled
    val stroke = animatedColor(if (filled) c.accent else c.inkMuted, Motion.css(Ms.StarFill))
    Box(
        Modifier.size(48.dp)
            .pressable(
                {
                    val on = onToggle()
                    if (!rm) scope.launch {
                        if (on) {
                            launch { scale.snapTo(0.5f); scale.animateTo(1f, Motion.pop()) }
                            launch { burst.snapTo(0f); burst.animateTo(1f, Motion.decel(Ms.StarBurst)) }
                        } else {
                            scale.snapTo(0.8f); scale.animateTo(1f, Motion.std(Ms.StarOff))
                        }
                    }
                },
                pressedFill = c.press, label = label,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).drawBehind {
                val b = burst.value
                if (b < 1f) drawCircle(c.accent.copy(alpha = 0.45f * (1 - b)), size.width / 2 * (0.4f + 1.1f * b))
            },
        )
        StarGlyph(
            24.dp, fill = { if (filled) c.accent else null }, stroke = { stroke.value }, strokeWidth = 2.4f,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}
