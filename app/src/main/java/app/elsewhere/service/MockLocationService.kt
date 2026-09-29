package app.elsewhere.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.elsewhere.MainActivity
import app.elsewhere.R
import app.elsewhere.data.City
import app.elsewhere.data.CityRepository
import app.elsewhere.data.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Foreground service that feeds the GPS and network test providers with the selected city,
 * about once a second, until it is stopped from the app or the notification.
 */
class MockLocationService : LifecycleService() {

    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var loop: Job? = null
    private var target: City? = null
    private lateinit var prefs: Prefs
    private lateinit var lm: LocationManager

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        lm = getSystemService(LocationManager::class.java)
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopTrip(clearFlag = true)
            return START_NOT_STICKY
        }
        // Must call startForeground promptly; the notification text is refreshed once the city is known.
        if (!enterForeground(null)) {
            lifecycleScope.launch { prefs.setActive(false, 0L); stopSelf() }
            return START_NOT_STICKY
        }
        _running.value = true
        if (loop == null) startLoop(restarted = intent == null)
        return START_STICKY
    }

    private fun enterForeground(city: City?): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(city), type)
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException, missing permission (SecurityException), ...
        false
    }

    private fun startLoop(restarted: Boolean) {
        loop = lifecycleScope.launch {
            val stored = prefs.data.first()
            if (restarted && !stored.active) { stopTrip(clearFlag = false); return@launch }
            val data = CityRepository.load(this@MockLocationService, this).await()
            if (!installProviders()) { stopTrip(clearFlag = true); return@launch }
            // Follow city changes immediately while active.
            launch {
                prefs.data.map { it.cityId }.distinctUntilChanged().collect { id ->
                    val c = data.city(id) ?: return@collect
                    target = c
                    runCatching { enterForeground(c) }
                    push(c)
                }
            }
            while (isActive) {
                target?.let { if (!push(it)) { stopTrip(clearFlag = true); return@launch } }
                delay(1000)
            }
        }
    }

    private fun installProviders(): Boolean = try {
        providers.forEach { p ->
            runCatching { lm.removeTestProvider(p) }
            MockEnvironment.addTestProvider(lm, p)
            lm.setTestProviderEnabled(p, true)
        }
        true
    } catch (e: SecurityException) {
        false // no longer the selected mock location app
    }

    private fun push(city: City): Boolean {
        val now = System.currentTimeMillis()
        val nanos = SystemClock.elapsedRealtimeNanos()
        return try {
            for (p in providers) {
                val gps = p == LocationManager.GPS_PROVIDER
                val loc = Location(p).apply {
                    latitude = city.lat
                    longitude = city.lon
                    altitude = city.elevation.toDouble()
                    accuracy = if (gps) 3f + Random.nextFloat() * 2f else 12f + Random.nextFloat() * 4f
                    speed = 0f
                    bearing = 0f
                    time = now
                    elapsedRealtimeNanos = nanos
                    if (Build.VERSION.SDK_INT >= 26) {
                        verticalAccuracyMeters = if (gps) 4f else 20f
                        speedAccuracyMetersPerSecond = 0.1f
                        bearingAccuracyDegrees = 180f
                    }
                }
                lm.setTestProviderLocation(p, loc)
            }
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            // provider vanished (mock app changed); try to re-install once
            installProviders()
        }
    }

    private fun stopTrip(clearFlag: Boolean) {
        loop?.cancel(); loop = null
        providers.forEach { p ->
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
        if (clearFlag) lifecycleScope.launch { prefs.setActive(false, 0L) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _running.value = false
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        providers.forEach { p -> runCatching { lm.removeTestProvider(p) } }
        _running.value = false
        super.onDestroy()
    }

    private fun notification(city: City?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = city?.let { getString(R.string.notif_text, it.name, app.elsewhere.data.countryName(it.code)) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_elsewhere)
            .setColor(ContextCompat.getColor(this, R.color.notification_sage))
            .setContentTitle(getString(R.string.notif_title))
            .apply { if (text != null) setContentText(text) }
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "app.elsewhere.STOP"

        private val _running = MutableStateFlow(false)
        /** In-process truth: the service lives in the app process, so this is false after process death. */
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MockLocationService::class.java).setAction(ACTION_STOP))
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                },
            )
        }
    }
}
