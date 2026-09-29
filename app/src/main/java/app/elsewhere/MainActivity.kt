package app.elsewhere

import android.Manifest
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.elsewhere.data.ThemePref
import app.elsewhere.ui.AppActions
import app.elsewhere.ui.AppViewModel
import app.elsewhere.ui.Card
import app.elsewhere.ui.ElsewhereRoot
import app.elsewhere.ui.resolveDark
import app.elsewhere.ui.motion.MotionClock

class MainActivity : ComponentActivity(), AppActions {
    private val vm: AppViewModel by viewModels()
    private var systemReduced by mutableStateOf(false)
    private var forcedReduced by mutableStateOf(false)

    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshEnvironment()
    }
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshEnvironment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.state.value.ready }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_REDUCED_MOTION, false) == true) forcedReduced = true
        readAnimatorScale()
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = resolveDark(state.stored.theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ElsewhereRoot(vm, reduced = systemReduced || forcedReduced, actions = this)
        }
    }

    override fun onResume() {
        super.onResume()
        readAnimatorScale()
        vm.refreshEnvironment()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_REDUCED_MOTION)) forcedReduced = intent.getBooleanExtra(EXTRA_REDUCED_MOTION, false)
    }

    /** LocalReducedMotion: ANIMATOR_DURATION_SCALE == 0 ("Remove animations"). */
    private fun readAnimatorScale() {
        val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        MotionClock.systemScale = scale
        systemReduced = scale == 0f
    }

    // ---------------------------------------------------------------- HomeActions

    override fun onCardAction(card: Card) {
        when (card) {
            Card.Location -> requestLocation()
            Card.Mock -> openDeveloperOptions()
            Card.Killed -> vm.start()
            Card.Notifications -> requestNotifications()
        }
    }

    override fun onCardDismiss() = vm.dismissNotifCard()

    private fun requestLocation() {
        val asked = vm.state.value.stored.locationAsked
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Permanently denied: the system will not show the prompt again.
            openAppSettings()
            return
        }
        vm.markLocationAsked()
        locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val asked = vm.state.value.stored.notificationsAsked
        if (granted || (asked && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            // Granted but switched off in Settings, or permanently denied.
            launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) { openAppSettings() }
            return
        }
        vm.markNotificationsAsked()
        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openAppSettings() {
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) {}
    }

    private fun openDeveloperOptions() {
        launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) { launch(Intent(Settings.ACTION_SETTINGS)) {} }
    }

    private fun launch(intent: Intent, fallback: () -> Unit) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            fallback()
        } catch (e: SecurityException) {
            fallback()
        }
    }

    // ---------------------------------------------------------------- SettingsActions

    override fun onTheme(pref: ThemePref) {
        vm.setTheme(pref)
        // Lets the system splash follow the in-app theme next launch (Android 12+).
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                when (pref) {
                    ThemePref.System -> UiModeManager.MODE_NIGHT_AUTO
                    ThemePref.Light -> UiModeManager.MODE_NIGHT_NO
                    ThemePref.Dark -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }

    override fun onMockRow() = openDeveloperOptions()
    override fun onReplaySetup() = vm.replaySetup()
    override fun onToggleReducedMotion() { if (BuildConfig.DEBUG) forcedReduced = !forcedReduced }

    // ---------------------------------------------------------------- OnboardingActions

    override fun onPrimary(step: Int) {
        when (step) {
            0 -> vm.obNext()
            1 -> {
                launch(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) { launch(Intent(Settings.ACTION_SETTINGS)) {} }
                vm.obNext()
            }
            else -> {
                openDeveloperOptions()
                vm.finishOnboarding()
            }
        }
    }

    override fun onBack() = vm.obBack()
    override fun onSkip() = vm.finishOnboarding()

    companion object {
        /** Debug builds: `adb shell am start -n app.elsewhere/.MainActivity --ez reducedMotion true`. */
        const val EXTRA_REDUCED_MOTION = "reducedMotion"
    }
}
