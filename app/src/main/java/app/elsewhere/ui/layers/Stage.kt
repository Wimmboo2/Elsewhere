package app.elsewhere.ui.layers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import app.elsewhere.data.City
import app.elsewhere.ui.AppViewModel
import app.elsewhere.ui.Layer
import app.elsewhere.ui.SheetTab
import app.elsewhere.ui.map.MapState
import app.elsewhere.ui.motion.Motion
import app.elsewhere.ui.motion.Motion.Ms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where things are on screen, for container transforms and flights (the prototype's `rel()`). */
class Registry {
    private val coords = HashMap<String, LayoutCoordinates>()
    private val tags = HashMap<String, Any?>()

    fun modifier(key: String, tag: Any? = null): Modifier = Modifier.onGloballyPositioned {
        coords[key] = it
        tags[key] = tag
    }

    fun tag(key: String): Any? = tags[key]

    /** Unclipped bounds in root (window) pixels. */
    fun rect(key: String): Rect? = coords[key]?.takeIf { it.isAttached }?.let { rectOf(it) }

    companion object {
        fun rectOf(c: LayoutCoordinates): Rect = Rect(c.positionInRoot(), c.size.toSize())
    }
}

/** The prototype's `stagger()`: one clock per list, read by the first 12 rows in their draw layer. */
@Stable
class StaggerClock {
    /** ms since the entrance started; -1 when idle (rows fully shown). */
    var elapsed by mutableLongStateOf(-1L)
        private set
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        elapsed = 0L
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val end = (Motion.staggerDelay(Motion.Dp.StaggerCap) + Ms.StaggerRow).toLong()
            var t0 = -1L
            while (true) {
                val now = withFrameMillis { it }
                if (t0 < 0) t0 = now
                elapsed = now - t0
                if (elapsed >= end) break
            }
            elapsed = -1L
        }
    }

    fun stop() { job?.cancel(); elapsed = -1L }

    /** Row entrance progress in [0, 1], already eased (emphasized decelerate). */
    fun progress(index: Int): Float {
        val e = elapsed
        if (e < 0 || index >= Motion.Dp.StaggerRows) return 1f
        val t = (e - Motion.staggerDelay(index)).toFloat() / Ms.StaggerRow
        return if (t <= 0f) 0f else if (t >= 1f) 1f else Motion.Decel.transform(t)
    }
}

/** A container-transform / shared-axis layer's animated values (`openLayer`, `containerIn/Out`, `axisIn`). */
@Stable
class LayerAnim {
    enum class Mode { None, In, Out }

    var mode by mutableStateOf(Mode.None)
    var from: Rect = Rect.Zero
    var fromRadius = 0f
    var to: Rect = Rect.Zero
    var toRadius = 0f
    /** Eased progress of the container transform. */
    val t = Animatable(1f)
    val content = Animatable(1f)
    val alpha = Animatable(1f)
    /** Horizontal shift in dp (shared axis X). */
    val tx = Animatable(0f)
    /** While leaving, the layer lets touches through (`pointer-events: none`). */
    var exiting by mutableStateOf(false)
    /** `visibility: hidden` for the country layer while a city collapses home. */
    var hidden by mutableStateOf(false)
    var job: Job? = null

    suspend fun reset() {
        mode = Mode.None; exiting = false; hidden = false
        t.snapTo(1f); content.snapTo(1f); alpha.snapTo(1f); tx.snapTo(0f)
    }
}

@Stable
class SheetAnim {
    val scrim = Animatable(0f)
    /** 0 = open, 1 = off screen (fraction of the sheet height). */
    val offset = Animatable(1f)
    val alpha = Animatable(1f)
    var closing by mutableStateOf(false)
}

/** A shared-element flight drawn in the overlay (`fly()`): moves a copy from [from] to [to]. */
@Stable
class Flight(
    val key: String,
    val from: Rect,
    val to: Rect,
    val flagCode: String?,
    val flagRadiusDp: Float,
    val text: String?,
) {
    val p = Animatable(0f)
}

/** Home-screen animations triggered by the controller (`swapTitle`, `nope`, `animHome`). */
@Stable
class HomeAnim {
    val title = Animatable(1f)
    val titleReduced = mutableStateOf(false)
    val chip = Animatable(1f)
    val coords = Animatable(1f)
    /** Shake progress 0..1 (keyframes x 0, -6, 5, -3, 0). */
    val shake = Animatable(1f)
    /** Card nudge progress 0..1 (scale 1, 1.03, 1). */
    val nudge = Animatable(1f)
    val enterAlpha = Animatable(1f)
    val enterScale = Animatable(1f)
    var nudgeTick by mutableLongStateOf(0L)

    fun shakeX(): Float = keyframes(shake.value, floatArrayOf(0f, -6f, 5f, -3f, 0f))
    fun nudgeScale(): Float = keyframes(nudge.value, floatArrayOf(1f, 1.03f, 1f))

    private fun keyframes(p: Float, v: FloatArray): Float {
        if (p >= 1f) return v.last()
        val seg = p * (v.size - 1)
        val i = seg.toInt().coerceIn(0, v.size - 2)
        return v[i] + (v[i + 1] - v[i]) * (seg - i)
    }
}

/**
 * The prototype's `class Component` choreography: container transforms, shared axis, flights,
 * the sheet and list staggers. Business state lives in [AppViewModel]; this only moves pixels.
 */
@Stable
class Stage(
    val vm: AppViewModel,
    val scope: CoroutineScope,
) {
    var density: Density = Density(1f)
    var rootSize: Size = Size.Zero
    var reduced = false

    val registry = Registry()
    val layers = mapOf(Layer.Country to LayerAnim(), Layer.City to LayerAnim(), Layer.Settings to LayerAnim())
    val sheet = SheetAnim()
    val home = HomeAnim()
    val map = MapState()
    val staggerCountry = StaggerClock()
    val staggerCity = StaggerClock()
    val staggerSheet = StaggerClock()
    val flights = mutableStateListOf<Flight>()
    /** Flight targets hidden until the flight lands (`target.style.visibility = 'hidden'`). */
    val hidden = mutableStateMapOf<String, Boolean>()

    private fun dp(v: Float) = v * density.density
    private val full get() = Rect(Offset.Zero, rootSize)

    private fun launchNow(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)

    // ------------------------------------------------------------------ layers

    fun openCountry() {
        vm.setCountryQuery("")
        openLayer(Layer.Country, Keys.HomeChip, CHIP_RADIUS_DP)
    }

    fun openCity() {
        val code = vm.state.value.city?.code ?: return
        vm.setCityQuery("")
        openLayer(Layer.City, Keys.CityTitle, TITLE_RADIUS_DP, pickCode = code)
    }

    fun openSettings() = openLayer(Layer.Settings, null, 0f)

    private fun openLayer(layer: Layer, srcKey: String?, radiusDp: Float, pickCode: String? = null) {
        val anim = layers.getValue(layer)
        val src = srcKey?.let { registry.rect(it) }
        anim.job?.cancel()
        anim.job = launchNow {
            anim.reset()
            if (reduced) {
                anim.alpha.snapTo(0f)
                vm.pushLayer(layer, pickCode)
                stagger(layer)
                anim.alpha.animateTo(1f, Motion.reduced())
                return@launchNow
            }
            if (src == null) {
                // axisIn: settings slides in from +56dp
                anim.tx.snapTo(Motion.Dp.SettingsAxis); anim.alpha.snapTo(0f)
                vm.pushLayer(layer, pickCode)
                stagger(layer)
                coroutineScope {
                    launch { anim.tx.animateTo(0f, Motion.decel(Ms.SettingsIn)) }
                    launch { anim.alpha.animateTo(1f, Motion.decel(Ms.SettingsIn)) }
                }
                return@launchNow
            }
            // containerIn
            anim.mode = LayerAnim.Mode.In
            anim.from = src; anim.fromRadius = dp(radiusDp)
            anim.t.snapTo(0f); anim.content.snapTo(0f)
            vm.pushLayer(layer, pickCode)
            stagger(layer)
            coroutineScope {
                launch { anim.t.animateTo(1f, Motion.decel(Ms.ContainerIn)) }
                launch { anim.content.animateTo(1f, Motion.std(Ms.ContainerContentIn, Ms.ContainerContentDelay)) }
            }
            anim.mode = LayerAnim.Mode.None
        }
    }

    private fun stagger(layer: Layer?) {
        if (reduced) return
        when (layer) {
            Layer.Country -> staggerCountry.start(scope)
            Layer.City -> staggerCity.start(scope)
            null -> staggerSheet.start(scope)
            else -> Unit
        }
    }

    /** `containerOut`: shrink into [target] (radius [radiusDp]), content out first, last 15% fades. */
    private suspend fun containerOut(anim: LayerAnim, target: Rect?, radiusDp: Float) {
        anim.exiting = true
        if (reduced || target == null) {
            anim.alpha.animateTo(0f, Motion.linear(Ms.Reduced))
            return
        }
        anim.mode = LayerAnim.Mode.Out
        anim.to = target; anim.toRadius = dp(radiusDp)
        anim.t.snapTo(0f)
        coroutineScope {
            launch { anim.content.animateTo(0f, Motion.std(Ms.ContainerContentOut)) }
            anim.t.animateTo(1f, Motion.std(Ms.ContainerOut))
        }
    }

    fun goBack(): Boolean {
        val s = vm.state.value
        if (s.sheet) { closeSheet(); return true }
        val top = s.stack.lastOrNull() ?: return false
        val anim = layers.getValue(top)
        if (anim.exiting) return true
        anim.job?.cancel()
        when {
            top == Layer.Settings -> anim.job = launchNow {
                anim.exiting = true
                if (reduced) anim.alpha.animateTo(0f, Motion.reduced(Motion.Accel))
                else coroutineScope {
                    launch { anim.tx.animateTo(Motion.Dp.SettingsAxis, Motion.accel(Ms.SettingsOut)) }
                    launch { anim.alpha.animateTo(0f, Motion.accel(Ms.SettingsOut)) }
                }
                vm.popLayer(top); anim.reset()
            }
            top == Layer.City && Layer.Country in s.stack -> {
                val below = layers.getValue(Layer.Country)
                below.job?.cancel()
                below.job = launchNow {
                    below.reset()
                    if (reduced) { below.alpha.snapTo(0f); below.alpha.animateTo(1f, Motion.reduced(Motion.Decel)) }
                    else {
                        below.tx.snapTo(-Motion.Dp.Axis); below.alpha.snapTo(0f)
                        coroutineScope {
                            launch { below.tx.animateTo(0f, Motion.decel(Ms.AxisIn)) }
                            launch { below.alpha.animateTo(1f, Motion.decel(Ms.AxisIn)) }
                        }
                    }
                }
                anim.job = launchNow {
                    anim.exiting = true
                    if (reduced) anim.alpha.animateTo(0f, Motion.reduced(Motion.Accel))
                    else coroutineScope {
                        launch { anim.tx.animateTo(Motion.Dp.Axis, Motion.accel(Ms.AxisCityOut)) }
                        launch { anim.alpha.animateTo(0f, Motion.accel(Ms.AxisCityOut)) }
                    }
                    vm.popLayer(top); anim.reset()
                }
            }
            else -> {
                val (key, radius) = if (top == Layer.Country) Keys.HomeChip to CHIP_RADIUS_DP else Keys.CityTitle to TITLE_RADIUS_DP
                val target = registry.rect(key)
                anim.job = launchNow {
                    containerOut(anim, target, radius)
                    vm.popLayer(top); anim.reset()
                }
            }
        }
        return true
    }

    /** `pickCountry`: city layer in from +48dp, country out to -48dp, flag flies into the header. */
    fun pickCountry(code: String, flagRect: Rect?) {
        val city = layers.getValue(Layer.City)
        val country = layers.getValue(Layer.Country)
        vm.setCityQuery("")
        city.job?.cancel()
        city.job = launchNow {
            city.reset()
            if (reduced) {
                city.alpha.snapTo(0f)
                vm.pushLayer(Layer.City, code)
                stagger(Layer.City)
                city.alpha.animateTo(1f, Motion.linear(Ms.Reduced))
                return@launchNow
            }
            city.tx.snapTo(Motion.Dp.Axis); city.alpha.snapTo(0f)
            vm.pushLayer(Layer.City, code)
            country.job?.cancel()
            country.job = launchNow {
                coroutineScope {
                    launch { country.tx.animateTo(-Motion.Dp.Axis, Motion.std(Ms.AxisCountryOut)) }
                    launch { country.alpha.animateTo(0f, Motion.std(Ms.AxisCountryOut)) }
                }
            }
            // Land on the header's resting place: the layer is still sliding in from +48dp when it is measured.
            if (flagRect != null) launch { flyWhenPlaced(Keys.CityHeaderFlag, code, flagRect, FLAG_HEADER_RADIUS, null, code) { -city.tx.value * density.density } }
            stagger(Layer.City)
            coroutineScope {
                launch { city.tx.animateTo(0f, Motion.decel(Ms.AxisIn)) }
                launch { city.alpha.animateTo(1f, Motion.decel(Ms.AxisIn)) }
            }
        }
    }

    /** `finishCity`: select, collapse the picker into the home card, fly name and flag home. */
    fun finishCity(id: Int, nameRect: Rect?) {
        val flagFrom = registry.rect(Keys.CityHeaderFlag)
        val cityLayer = layers.getValue(Layer.City)
        val country = layers.getValue(Layer.Country)
        val newCity = vm.cityData?.city(id)
        val willChange = newCity != null && newCity.id != vm.state.value.city?.id
        if (willChange && !reduced) {
            hidden[Keys.CityTitleText] = true
            hidden[Keys.HomeFlag] = true
        }
        selectCity(id, swap = false, delayMs = if (reduced) 0 else Ms.FlightGlideDelay) { city ->
            if (reduced) { swapTitle(false); return@selectCity }
            launchNow { flyWhenPlaced(Keys.CityTitleText, city.id, nameRect, 0f, city.name, null) }
            launchNow { flyWhenPlaced(Keys.HomeFlag, city.code, flagFrom, FLAG_CHIP_RADIUS, null, city.code) }
        }
        if (Layer.Country in vm.state.value.stack) country.hidden = true
        val card = registry.rect(Keys.HomeCard)
        cityLayer.job?.cancel()
        cityLayer.job = launchNow {
            containerOut(cityLayer, card, CARD_RADIUS_DP)
            vm.clearLayers()
            cityLayer.reset(); country.reset(); country.job?.cancel()
        }
    }

    /** Waits until the target composable has laid out with [tag], then flies a copy from [from]. */
    private suspend fun flyWhenPlaced(key: String, tag: Any?, from: Rect?, radiusDp: Float, text: String?, flag: String?, shiftX: () -> Float = { 0f }) {
        if (from == null) { hidden.remove(key); return }
        hidden[key] = true
        var to: Rect? = null
        var frames = 0
        while (to == null && frames++ < 12) {
            withFrameMillis { }
            val r = registry.rect(key)
            if (r != null && registry.tag(key) == tag && r.width > 0f) to = r.translate(shiftX(), 0f)
        }
        val target = to ?: run { hidden.remove(key); return }
        val f = Flight(key, from, target, flag, radiusDp, text)
        flights += f
        try {
            f.p.animateTo(1f, Motion.decel(Ms.Flight))
        } finally {
            flights.remove(f)
            hidden.remove(key)
        }
    }

    // ------------------------------------------------------------------ city

    /** Prototype `selectCity` + `glide` + optional `swapTitle`. */
    fun selectCity(id: Int, swap: Boolean, delayMs: Int = 0, after: ((City) -> Unit)? = null): Boolean {
        val old = vm.state.value.city ?: return false
        if (!vm.selectCity(id)) return false
        val new = vm.state.value.city ?: return false
        map.glide(scope, old, new, delayMs, reduced)
        if (swap) swapTitle(old.code != new.code)
        after?.invoke(new)
        return true
    }

    fun swapTitle(countryChanged: Boolean) {
        val h = home
        launchNow {
            h.titleReduced.value = reduced
            h.title.snapTo(0f)
            if (countryChanged) h.chip.snapTo(0f)
            h.coords.snapTo(0f)
            val spec: AnimationSpec<Float> = if (reduced) Motion.decel(Ms.Reduced) else Motion.decel(Ms.TitleSwap)
            coroutineScope {
                launch { h.title.animateTo(1f, spec) }
                if (countryChanged) launch { h.chip.animateTo(1f, spec) }
                launch { h.coords.animateTo(1f, Motion.linear(Ms.CoordsFade, Ms.CoordsDelay)) }
            }
        }
    }

    // ------------------------------------------------------------------ start control

    /** `nope()`: shake the Start control and pulse the status card. */
    fun nope() {
        if (reduced) return
        launchNow { home.shake.snapTo(0f); home.shake.animateTo(1f, Motion.std(Ms.Shake)) }
        launchNow { home.nudge.snapTo(0f); home.nudge.animateTo(1f, Motion.std(Ms.CardNudge)) }
    }

    /** `animHome()`: home scales in and the pin drops. */
    fun enterHome() {
        launchNow {
            home.enterAlpha.snapTo(0f)
            if (reduced) { home.enterAlpha.animateTo(1f, Motion.decel(Ms.Reduced)); return@launchNow }
            home.enterScale.snapTo(0.97f)
            coroutineScope {
                launch { home.enterAlpha.animateTo(1f, Motion.decel(Ms.HomeEnter)) }
                launch { home.enterScale.animateTo(1f, Motion.decel(Ms.HomeEnter)) }
            }
        }
        map.dropPin(scope, if (reduced) 0 else Ms.HomePin, reduced)
    }

    // ------------------------------------------------------------------ sheet

    fun openSheet() {
        vm.setSheet(true)
        launchNow {
            sheet.closing = false
            sheet.scrim.snapTo(0f)
            if (reduced) {
                sheet.offset.snapTo(0f); sheet.alpha.snapTo(0f)
                coroutineScope {
                    launch { sheet.scrim.animateTo(1f, Motion.std(Ms.Reduced)) }
                    launch { sheet.alpha.animateTo(1f, Motion.decel(Ms.Reduced)) }
                }
            } else {
                sheet.alpha.snapTo(1f); sheet.offset.snapTo(1f)
                stagger(null)
                coroutineScope {
                    launch { sheet.scrim.animateTo(1f, Motion.std(Ms.ScrimIn)) }
                    launch { sheet.offset.animateTo(0f, Motion.decel(Ms.SheetIn)) }
                }
            }
        }
    }

    fun closeSheet(then: (() -> Unit)? = null) {
        if (sheet.closing) return
        sheet.closing = true
        launchNow {
            coroutineScope {
                launch { sheet.scrim.animateTo(0f, Motion.std(Ms.ScrimOut)) }
                if (reduced) sheet.alpha.animateTo(0f, Motion.reduced(Motion.Accel))
                else sheet.offset.animateTo(1f, Motion.accel(Ms.SheetOut))
            }
            vm.setSheet(false)
            sheet.closing = false
            then?.invoke()
        }
    }

    /** Drag released: dismiss past a quarter of the height or on a downward fling, else settle back. */
    fun settleSheet(velocityFraction: Float) {
        val v = sheet.offset.value
        if (v > 0.25f || velocityFraction > 2.5f) closeSheet()
        else launchNow { sheet.offset.animateTo(0f, Motion.decel(Ms.SheetIn)) }
    }

    fun dragSheet(deltaFraction: Float) {
        if (sheet.closing) return
        launchNow { sheet.offset.snapTo((sheet.offset.value + deltaFraction).coerceIn(0f, 1f)) }
    }

    fun setSheetTab(tab: SheetTab) {
        vm.setSheetTab(tab)
        stagger(null)
    }

    /** Sheet row tap: close, then switch 120ms later. */
    fun pickFromSheet(id: Int) {
        val current = vm.state.value.city?.id
        closeSheet()
        if (id == current) return
        scope.launch {
            delay(Ms.SheetRowDelay.toLong())
            selectCity(id, swap = true)
        }
    }

    fun browseFromSheet() = closeSheet { openCountry() }

    companion object {
        const val CHIP_RADIUS_DP = 999f        // border-radius: 999px, clamped to the rect
        const val TITLE_RADIUS_DP = 20f        // parseFloat('0px') || 20
        const val CARD_RADIUS_DP = 32f
        const val FLAG_HEADER_RADIUS = 5f
        const val FLAG_CHIP_RADIUS = 4f
    }
}

object Keys {
    const val HomeChip = "homeChip"
    const val HomeFlag = "homeFlag"
    const val HomeCard = "homeCard"
    const val CityTitle = "cityTitle"
    const val CityTitleText = "cityTitleText"
    const val CityHeaderFlag = "cityHeaderFlag"
}
