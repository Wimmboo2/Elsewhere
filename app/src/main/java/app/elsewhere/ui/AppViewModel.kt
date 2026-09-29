package app.elsewhere.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.elsewhere.data.City
import app.elsewhere.data.CityData
import app.elsewhere.data.CityRepository
import app.elsewhere.data.Country
import app.elsewhere.data.Prefs
import app.elsewhere.data.Recent
import app.elsewhere.data.Stored
import app.elsewhere.data.ThemePref
import app.elsewhere.service.MockEnvironment
import app.elsewhere.service.MockLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Onboarding, Home }
enum class Layer { Country, City, Settings }
enum class SheetTab { Fav, Recent }
enum class Card { Location, Mock, Killed, Notifications }

@Immutable
data class Env(
    val locPerm: Boolean = true,
    val mockSelected: Boolean = true,
    val notifBlocked: Boolean = false,
    val killed: Boolean = false,
)

@Immutable
data class AppState(
    val ready: Boolean = false,
    val screen: Screen = Screen.Home,
    val obStep: Int = 0,
    /** Onboarding was opened from Settings (back returns Home instead of leaving the app). */
    val obReplay: Boolean = false,
    val stack: List<Layer> = emptyList(),
    val sheet: Boolean = false,
    val sheetTab: SheetTab = SheetTab.Fav,
    val pickCode: String = "JP",
    val city: City? = null,
    val country: Country? = null,
    val stored: Stored = Stored(),
    val env: Env = Env(),
) {
    val active get() = stored.active
    val blocked get() = !env.locPerm || !env.mockSelected

    /** Prototype `computeCard`: one card at a time, in priority order. */
    val card: Card?
        get() = when {
            !env.locPerm -> Card.Location
            !env.mockSelected -> Card.Mock
            env.killed -> Card.Killed
            env.notifBlocked && !stored.notifDismissed -> Card.Notifications
            else -> null
        }
}

@Immutable
data class Results<T>(val query: String, val items: List<T>)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val prefs = Prefs.get(app)
    private val _state = MutableStateFlow(
        AppState(
            screen = saved.get<String>(K_SCREEN)?.let { Screen.valueOf(it) } ?: Screen.Home,
            obStep = saved[K_OB] ?: 0,
            obReplay = saved[K_OB_REPLAY] ?: false,
            stack = saved.get<ArrayList<String>>(K_STACK)?.map { Layer.valueOf(it) } ?: emptyList(),
            sheet = saved[K_SHEET] ?: false,
            sheetTab = saved.get<String>(K_TAB)?.let { SheetTab.valueOf(it) } ?: SheetTab.Fav,
            pickCode = saved[K_PICK] ?: "JP",
            env = Env(killed = saved[K_KILLED] ?: false),
        ),
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var data: CityData? = null
    val cityData: CityData? get() = data

    val countryQuery = MutableStateFlow(saved[K_QC] ?: "")
    val cityQuery = MutableStateFlow(saved[K_QI] ?: "")

    init {
        viewModelScope.launch {
            val d = CityRepository.load(app, viewModelScope).await()
            data = d
            val first = prefs.data.first()
            val restoring = saved.contains(K_SCREEN)
            _state.update {
                it.copy(
                    screen = if (restoring) it.screen else if (first.onboardingDone) Screen.Home else Screen.Onboarding,
                )
            }
            refreshEnvironment()
            prefs.data.collect { s -> applyStored(s) }
        }
    }

    private fun applyStored(s: Stored) {
        val d = data ?: return
        val city = d.city(s.cityId) ?: d.city(CityData.DEFAULT_CITY_ID)
        _state.update {
            it.copy(ready = true, stored = s, city = city, country = city?.let { c -> d.country(c.code) })
        }
    }

    // ---------- Search (debounced, off the main thread) ----------
    val countryResults: StateFlow<Results<Country>> = countryQuery
        .transformLatest { q ->
            val d = awaitData()
            if (q.isBlank()) emit(Results(q, d.countries)) else { delay(SEARCH_DEBOUNCE); emit(Results(q, d.searchCountries(q))) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Results("", emptyList()))

    val cityResults: StateFlow<Results<City>> = combine(cityQuery, _state) { q, s -> q to s.pickCode }
        .transformLatest { (q, code) ->
            val d = awaitData()
            if (q.isBlank()) emit(Results(q, d.searchCities(code, ""))) else { delay(SEARCH_DEBOUNCE); emit(Results(q, d.searchCities(code, q))) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Results("", emptyList()))

    private suspend fun awaitData(): CityData = data ?: CityRepository.load(getApplication(), viewModelScope).await()

    fun setCountryQuery(q: String) { countryQuery.value = q; saved[K_QC] = q }
    fun setCityQuery(q: String) { cityQuery.value = q; saved[K_QI] = q }

    // ---------- Environment detection ----------
    /** Called on start and every resume (coming back from Settings). */
    fun refreshEnvironment() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            envOverride?.let { o -> _state.update { it.copy(env = o) }; return@launch }
            val s = prefs.data.first()
            val loc = MockEnvironment.hasLocationPermission(app)
            val mock = MockEnvironment.isMockAppSelected(app)
            val notif = MockEnvironment.notificationsBlocked(app)
            var killed = _state.value.env.killed
            if (s.active && !MockLocationService.running.value) {
                // Give a service that is still starting (or being restarted by the system) a moment.
                delay(KILL_GRACE_MS)
            }
            if (prefs.data.first().active && !MockLocationService.running.value) {
                // The trip was active but Android (or the user) killed the process: say so, go idle.
                killed = true
                prefs.setActive(false, 0L)
            }
            saved[K_KILLED] = killed
            _state.update { it.copy(env = Env(locPerm = loc, mockSelected = mock, notifBlocked = notif, killed = killed)) }
            // Setup problems stop an active trip (the prototype's toggles do the same).
            if ((!loc || !mock) && s.active && MockLocationService.running.value) stop()
        }
    }

    // ---------- Start / Stop ----------
    fun start() {
        val now = System.currentTimeMillis()
        saved[K_KILLED] = false
        _state.update { it.copy(stored = it.stored.copy(active = true, startedAt = now), env = it.env.copy(killed = false)) }
        viewModelScope.launch {
            prefs.setActive(true, now)
            MockLocationService.start(getApplication())
        }
    }

    fun stop() {
        _state.update { it.copy(stored = it.stored.copy(active = false)) }
        viewModelScope.launch {
            prefs.setActive(false, 0L)
            MockLocationService.stop(getApplication())
        }
    }

    // ---------- City ----------
    /** Prototype `selectCity`: returns false when the city is already selected. */
    fun selectCity(id: Int): Boolean {
        val s = _state.value
        val d = data ?: return false
        val prev = s.city ?: return false
        if (id == prev.id) return false
        val city = d.city(id) ?: return false
        val recents = (listOf(Recent(prev.id, System.currentTimeMillis())) +
            s.stored.recents.filter { it.id != prev.id && it.id != id }).take(MAX_RECENTS)
        _state.update {
            it.copy(city = city, country = d.country(city.code), stored = it.stored.copy(cityId = id, recents = recents))
        }
        viewModelScope.launch { prefs.setCity(id, recents) }
        return true
    }

    fun toggleFavorite(id: Int): Boolean {
        val favs = _state.value.stored.favorites
        val on = id !in favs
        val next = if (on) favs + id else favs - id
        _state.update { it.copy(stored = it.stored.copy(favorites = next)) }
        viewModelScope.launch { prefs.setFavorites(next) }
        return on
    }

    // ---------- Layers ----------
    fun pushLayer(layer: Layer, pickCode: String? = null) {
        _state.update { s -> s.copy(stack = s.stack.filter { it != layer } + layer, pickCode = pickCode ?: s.pickCode) }
        persistNav()
    }

    fun popLayer(layer: Layer) {
        _state.update { s -> s.copy(stack = s.stack.filter { it != layer }) }
        persistNav()
    }

    fun clearLayers() {
        _state.update { it.copy(stack = emptyList()) }
        persistNav()
    }

    fun setSheet(open: Boolean) {
        _state.update { it.copy(sheet = open, sheetTab = if (open) SheetTab.Fav else it.sheetTab) }
        persistNav()
    }

    fun setSheetTab(tab: SheetTab) { _state.update { it.copy(sheetTab = tab) }; persistNav() }

    // ---------- Onboarding ----------
    fun obNext() {
        val s = _state.value
        if (s.obStep < 2) _state.update { it.copy(obStep = it.obStep + 1) } else finishOnboarding()
        persistNav()
    }

    fun obBack() { _state.update { it.copy(obStep = maxOf(0, it.obStep - 1)) }; persistNav() }

    fun finishOnboarding() {
        _state.update { it.copy(screen = Screen.Home, stack = emptyList(), obReplay = false) }
        viewModelScope.launch { prefs.setOnboardingDone() }
        persistNav()
    }

    fun replaySetup() {
        _state.update { it.copy(screen = Screen.Onboarding, obStep = 0, stack = emptyList(), sheet = false, obReplay = true) }
        persistNav()
    }

    // ---------- Settings / cards ----------
    fun setTheme(t: ThemePref) {
        _state.update { it.copy(stored = it.stored.copy(theme = t)) }
        viewModelScope.launch { prefs.setTheme(t) }
    }

    fun dismissNotifCard() {
        _state.update { it.copy(stored = it.stored.copy(notifDismissed = true)) }
        viewModelScope.launch { prefs.setNotifDismissed() }
    }

    fun markLocationAsked() = viewModelScope.launch { prefs.setLocationAsked() }
    fun markNotificationsAsked() = viewModelScope.launch { prefs.setNotificationsAsked() }

    private fun persistNav() {
        val s = _state.value
        saved[K_SCREEN] = s.screen.name
        saved[K_OB] = s.obStep
        saved[K_OB_REPLAY] = s.obReplay
        saved[K_STACK] = ArrayList(s.stack.map { it.name })
        saved[K_SHEET] = s.sheet
        saved[K_TAB] = s.sheetTab.name
        saved[K_PICK] = s.pickCode
    }

    /** Screenshot tests only: pins the detected device conditions. */
    @androidx.annotation.VisibleForTesting
    fun overrideEnv(env: Env) {
        envOverride = env
        _state.update { it.copy(env = env) }
    }

    companion object {
        @androidx.annotation.VisibleForTesting
        @Volatile var envOverride: Env? = null
        const val MAX_RECENTS = 8
        const val SEARCH_DEBOUNCE = 150L
        private const val KILL_GRACE_MS = 600L
        val notificationsRelevant = Build.VERSION.SDK_INT >= 33
        private const val K_SCREEN = "screen"
        private const val K_OB = "ob"
        private const val K_OB_REPLAY = "obReplay"
        private const val K_STACK = "stack"
        private const val K_SHEET = "sheet"
        private const val K_TAB = "tab"
        private const val K_PICK = "pick"
        private const val K_QC = "qc"
        private const val K_QI = "qi"
        private const val K_KILLED = "killed"
    }
}
