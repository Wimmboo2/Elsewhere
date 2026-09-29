package app.elsewhere

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.elsewhere.data.Prefs
import app.elsewhere.data.Recent
import app.elsewhere.data.ThemePref
import app.elsewhere.ui.Env
import app.elsewhere.ui.AppViewModel
import app.elsewhere.ui.SheetTab
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app on the JVM (Robolectric native graphics, Pixel 7 class 412 x 915dp @ 420dpi)
 * through the same states the prototype capture visits, into verify/app/. Not a pass/fail test:
 * tools/verify/compare.py puts each shot next to its prototype reference.
 *
 *   ./gradlew :app:testDebugUnitTest --tests app.elsewhere.ScreenshotTest -Ptheme=light
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-420dpi")
abstract class ScreenshotBase(private val theme: ThemePref) {
    private val seed = object : ExternalResource() {
        override fun before() {
            AppViewModel.envOverride = Env()
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            val p = Prefs.get(ctx)
            val now = System.currentTimeMillis()
            runBlocking {
                p.setTheme(theme)
                p.setFavorites(listOf(LISBON, REYKJAVIK, OAXACA))
                p.setCity(KYOTO, listOf(Recent(MEXICO_CITY, now - 2 * 3600_000L), Recent(PARIS, now - 26 * 3600_000L), Recent(TOKYO, now - 3 * 86400_000L)))
                p.setActive(false, 0L)
            }
        }
    }
    private val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var vm: AppViewModel
    private var stageRef: app.elsewhere.ui.layers.Stage? = null

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    private val out = File(System.getProperty("elsewhere.verifyOut") ?: "build/verify").also { it.mkdirs() }
    private val t = theme.name.lowercase()

    private fun settle(ms: Long = 1500) {
        compose.mainClock.autoAdvance = false
        var left = ms
        while (left > 0) { compose.mainClock.advanceTimeBy(50); org.robolectric.shadows.ShadowLooper.idleMainLooper(); left -= 50 }
    }

    private val noActions = object : app.elsewhere.ui.AppActions {
        override fun onCardAction(card: app.elsewhere.ui.Card) {}
        override fun onCardDismiss() {}
        override fun onTheme(pref: ThemePref) { vm.setTheme(pref) }
        override fun onMockRow() {}
        override fun onReplaySetup() { vm.replaySetup() }
        override fun onToggleReducedMotion() {}
        override fun onPrimary(step: Int) { if (step < 2) vm.obNext() else vm.finishOnboarding() }
        override fun onBack() { vm.obBack() }
        override fun onSkip() { vm.finishOnboarding() }
    }

    private fun waitReady() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            vm = androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java]
            compose.activity.setContent { app.elsewhere.ui.ElsewhereRoot(vm, reduced = false, actions = noActions, stageOut = { stageRef = it }) }
        }
        repeat(1200) { i ->
            if (vm.state.value.ready && stageRef != null) return
            if (i % 100 == 0) println("waiting: ready=${vm.state.value.ready} data=${vm.cityData != null} stage=${stageRef != null}")
            Thread.sleep(50); settle(50)
        }
        error("app never became ready")
    }

    private fun shot(name: String) {
        settle()
        // Tiles load on real threads: give them real time, then let their 200ms fades finish.
        if (name.startsWith("home") || name.startsWith("card") || name.startsWith("sheet")) { Thread.sleep(2500); settle(600) }
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$t-$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ui(block: () -> Unit) { compose.runOnUiThread(block); settle(100) }

    @Test
    fun capture() {
        waitReady()
        val stage = { stageRef!! }
        // Onboarding (first launch)
        ui { vm.replaySetup() }
        shot("onboarding-1")
        ui { vm.obNext() }; shot("onboarding-2")
        ui { vm.obNext() }; shot("onboarding-3")
        ui { vm.finishOnboarding() }; shot("home")
        ui { vm.start() }; shot("home-active")
        ui { vm.stop() }; settle(600)
        for ((name, env) in listOf(
            "card-location" to Env(locPerm = false),
            "card-mock" to Env(mockSelected = false),
            "card-killed" to Env(killed = true),
            "card-notifications" to Env(notifBlocked = true),
        )) {
            ui { vm.overrideEnv(env) }; shot(name)
            ui { vm.overrideEnv(Env()) }; settle(900)
        }
        ui { stage().openSheet() }; shot("sheet-favorites")
        ui { stage().setSheetTab(SheetTab.Recent) }; shot("sheet-recent")
        ui { stage().closeSheet() }; settle(600)
        val favs = vm.state.value.stored.favorites
        favs.forEach { id -> ui { vm.toggleFavorite(id) } }
        shot("home-nofavorites")
        ui { stage().openSheet() }; shot("sheet-favorites-empty")
        ui { stage().closeSheet() }; settle(600)
        favs.forEach { id -> ui { vm.toggleFavorite(id) } }
        ui { stage().openCountry() }; shot("country")
        ui { vm.setCountryQuery("Atlantis") }; shot("country-noresults")
        ui { stage().goBack() }; settle(600)
        ui { stage().openCity() }; shot("city")
        ui { vm.setCityQuery("zzz") }; shot("city-noresults")
        ui { stage().goBack() }; settle(600)
        ui { stage().openSettings() }; shot("settings")
    }

    companion object {
        const val KYOTO = 1857910
        const val LISBON = 2267057
        const val REYKJAVIK = 3413829
        const val OAXACA = 3522507
        const val MEXICO_CITY = 3530597
        const val PARIS = 2988507
        const val TOKYO = 1850147
    }
}

class LightScreenshots : ScreenshotBase(ThemePref.Light)
class DarkScreenshots : ScreenshotBase(ThemePref.Dark)
