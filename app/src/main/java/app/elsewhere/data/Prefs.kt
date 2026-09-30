package app.elsewhere.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePref { System, Light, Dark }

@Immutable
data class Recent(val id: Int, val time: Long)

@Immutable
data class Stored(
    val theme: ThemePref = ThemePref.System,
    val cityId: Int = CityData.DEFAULT_CITY_ID,
    val favorites: List<Int> = emptyList(),
    val recents: List<Recent> = emptyList(),
    val onboardingDone: Boolean = false,
    val notifDismissed: Boolean = false,
    val active: Boolean = false,
    val startedAt: Long = 0L,
    val locationAsked: Boolean = false,
    val notificationsAsked: Boolean = false,
    /** Spots the user picked on the map, per city id. */
    val customSpots: Map<Int, LatLon> = emptyMap(),
    /** Nearest-hotel lookups per city id; a null value means "looked, none nearby". */
    val hotelLookups: Map<Int, LatLon?> = emptyMap(),
)

private fun Map<Int, LatLon?>.mapNotNullValuesCompat(): Map<Int, LatLon> {
    val out = HashMap<Int, LatLon>(size)
    for ((k, v) in this) if (v != null) out[k] = v
    return out
}

/** "id:lat:lon" entries separated by ','; "id:-" records a lookup that found nothing. */
private fun encodeSpots(m: Map<Int, LatLon?>): String =
    m.entries.joinToString(",") { (id, p) -> if (p == null) "$id:-" else "$id:${p.lat}:${p.lon}" }

private fun decodeSpots(s: String?): Map<Int, LatLon?> {
    if (s.isNullOrEmpty()) return emptyMap()
    val out = LinkedHashMap<Int, LatLon?>()
    for (e in s.split(',')) {
        val p = e.split(':')
        val id = p.getOrNull(0)?.toIntOrNull() ?: continue
        if (p.size == 2 && p[1] == "-") { out[id] = null; continue }
        val lat = p.getOrNull(1)?.toDoubleOrNull() ?: continue
        val lon = p.getOrNull(2)?.toDoubleOrNull() ?: continue
        out[id] = LatLon(lat, lon)
    }
    return out
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("elsewhere")

class Prefs(context: Context) {
    private val ds = context.applicationContext.store

    private object K {
        val theme = stringPreferencesKey("theme")
        val city = intPreferencesKey("city")
        val favorites = stringPreferencesKey("favorites")
        val recents = stringPreferencesKey("recents")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val notifDismissed = booleanPreferencesKey("notif_dismissed")
        val active = booleanPreferencesKey("active")
        val startedAt = longPreferencesKey("started_at")
        val locAsked = booleanPreferencesKey("loc_asked")
        val notifAsked = booleanPreferencesKey("notif_asked")
        val customSpots = stringPreferencesKey("custom_spots")
        val hotelSpots = stringPreferencesKey("hotel_spots")
    }

    val data: Flow<Stored> = ds.data.map { p ->
        Stored(
            theme = p[K.theme]?.let { v -> ThemePref.entries.firstOrNull { it.name == v } } ?: ThemePref.System,
            cityId = p[K.city] ?: CityData.DEFAULT_CITY_ID,
            favorites = p[K.favorites].orEmpty().split(',').mapNotNull { it.toIntOrNull() },
            recents = p[K.recents].orEmpty().split(',').mapNotNull { e ->
                val (id, t) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                Recent(id.toIntOrNull() ?: return@mapNotNull null, t.toLongOrNull() ?: 0L)
            },
            onboardingDone = p[K.onboarding] ?: false,
            notifDismissed = p[K.notifDismissed] ?: false,
            active = p[K.active] ?: false,
            startedAt = p[K.startedAt] ?: 0L,
            locationAsked = p[K.locAsked] ?: false,
            notificationsAsked = p[K.notifAsked] ?: false,
            customSpots = decodeSpots(p[K.customSpots]).mapNotNullValuesCompat(),
            hotelLookups = decodeSpots(p[K.hotelSpots]),
        )
    }

    suspend fun setTheme(t: ThemePref) = ds.edit { it[K.theme] = t.name }
    suspend fun setCity(id: Int, recents: List<Recent>) = ds.edit {
        it[K.city] = id
        it[K.recents] = recents.joinToString(",") { r -> "${r.id}:${r.time}" }
    }
    suspend fun setFavorites(ids: List<Int>) = ds.edit { it[K.favorites] = ids.joinToString(",") }
    suspend fun setOnboardingDone() = ds.edit { it[K.onboarding] = true }
    suspend fun setNotifDismissed() = ds.edit { it[K.notifDismissed] = true }
    suspend fun setActive(active: Boolean, startedAt: Long) = ds.edit {
        it[K.active] = active
        it[K.startedAt] = startedAt
    }
    suspend fun setLocationAsked() = ds.edit { it[K.locAsked] = true }
    suspend fun setNotificationsAsked() = ds.edit { it[K.notifAsked] = true }

    /** [at] null removes the custom spot (back to the hotel or city point). */
    suspend fun setCustomSpot(cityId: Int, at: LatLon?) = ds.edit {
        val m = decodeSpots(it[K.customSpots]).toMutableMap()
        if (at == null) m.remove(cityId) else m[cityId] = at
        it[K.customSpots] = encodeSpots(m)
    }

    suspend fun setHotelLookup(cityId: Int, at: LatLon?) = ds.edit {
        val m = decodeSpots(it[K.hotelSpots]).toMutableMap()
        m[cityId] = at
        it[K.hotelSpots] = encodeSpots(m)
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context).also { instance = it }
        }
    }
}
