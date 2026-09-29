package app.elsewhere.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Real device conditions behind the prototype's "Setup problems" toggles. */
object MockEnvironment {
    private const val PROBE = "elsewhere_probe"

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Notifications only matter on Android 13+, where they are a runtime permission. */
    fun notificationsBlocked(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Elsewhere is the selected mock location app when LocationManager lets it add a test provider.
     * A throwaway provider is used so an active trip is never disturbed.
     */
    fun isMockAppSelected(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return try {
            addTestProvider(lm, PROBE)
            runCatching { lm.removeTestProvider(PROBE) }
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            // Provider already exists (left over from a crash): we were allowed to add it before.
            runCatching { lm.removeTestProvider(PROBE) }
            true
        }
    }

    @Suppress("DEPRECATION")
    fun addTestProvider(lm: LocationManager, name: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            lm.addTestProvider(
                name,
                ProviderProperties.Builder()
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build(),
            )
        } else {
            lm.addTestProvider(
                name, false, false, false, false, true, true, true,
                android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE,
            )
        }
    }
}
