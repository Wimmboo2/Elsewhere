package app.elsewhere.data

import androidx.compose.runtime.Immutable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Immutable
data class LatLon(val lat: Double, val lon: Double)

/** Where the mocked location comes from, in priority order: Custom > Hotel > City. */
enum class SpotSource { Custom, Hotel, City }

/** The exact point Elsewhere reports for the selected city. */
@Immutable
data class Spot(val cityId: Int, val lat: Double, val lon: Double, val source: SpotSource)

/** A spot the user picked beats a looked-up hotel, which beats the GeoNames city point. */
fun effectiveSpot(city: City, stored: Stored): Spot {
    stored.customSpots[city.id]?.let { return Spot(city.id, it.lat, it.lon, SpotSource.Custom) }
    stored.hotelLookups[city.id]?.let { return Spot(city.id, it.lat, it.lon, SpotSource.Hotel) }
    return Spot(city.id, city.lat, city.lon, SpotSource.City)
}

/**
 * The only place the "nearest hotel" lookup is configured. Both services serve OpenStreetMap data
 * (ODbL, credited in Settings > About) under fair-use policies: one request per city, cached forever.
 * Check their terms before shipping to many users (README).
 */
object SpotConfig {
    const val USER_AGENT = "Elsewhere/1.0 (Android mock location app; github.com/Wimmboo2/Elsewhere)"
    const val PHOTON = "https://photon.komoot.io/api/"
    const val OVERPASS = "https://overpass-api.de/api/interpreter"
    /** Hotels farther than this from the city point are ignored (the city point is kept). */
    const val MAX_DISTANCE_M = 3000.0
    const val TIMEOUT_MS = 8000
    const val ATTRIBUTION = "Nearest hotel from OpenStreetMap via Photon and Overpass. Data © OpenStreetMap contributors, ODbL."
}

sealed interface HotelLookup {
    data class Found(val at: LatLon) : HotelLookup
    /** Both services answered and there is no hotel close enough: remember that, keep the city point. */
    data object NoneNearby : HotelLookup
    /** Offline or both services failed: try again later. */
    data object Failed : HotelLookup
}

object NearestHotel {
    suspend fun find(lat: Double, lon: Double): HotelLookup = withContext(Dispatchers.IO) {
        val photon = runCatching { photon(lat, lon) }
        photon.getOrNull()?.let { return@withContext pick(lat, lon, it) }
        val overpass = runCatching { overpass(lat, lon) }
        overpass.getOrNull()?.let { return@withContext pick(lat, lon, it) }
        HotelLookup.Failed
    }

    private fun pick(lat: Double, lon: Double, candidates: List<LatLon>): HotelLookup {
        val best = candidates.minByOrNull { distanceM(lat, lon, it.lat, it.lon) }
            ?: return HotelLookup.NoneNearby
        return if (distanceM(lat, lon, best.lat, best.lon) <= SpotConfig.MAX_DISTANCE_M) HotelLookup.Found(best)
        else HotelLookup.NoneNearby
    }

    /** Photon ranks by relevance with a location bias; asking for 20 and taking the nearest gives the closest hotel. */
    private fun photon(lat: Double, lon: Double): List<LatLon> {
        val url = SpotConfig.PHOTON + "?q=hotel&osm_tag=tourism:hotel&limit=20&location_bias_scale=0&zoom=18" +
            "&lat=$lat&lon=$lon"
        val json = JSONObject(get(url))
        val features = json.getJSONArray("features")
        return List(features.length()) { i ->
            val c = features.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates")
            LatLon(c.getDouble(1), c.getDouble(0))
        }
    }

    private fun overpass(lat: Double, lon: Double): List<LatLon> {
        val r = SpotConfig.MAX_DISTANCE_M.toInt()
        val q = "[out:json][timeout:10];nwr[\"tourism\"=\"hotel\"](around:$r,$lat,$lon);out center 40;"
        val json = JSONObject(get(SpotConfig.OVERPASS + "?data=" + URLEncoder.encode(q, "UTF-8")))
        val els = json.getJSONArray("elements")
        return List(els.length()) { i ->
            val e = els.getJSONObject(i)
            val c = if (e.has("center")) e.getJSONObject("center") else e
            LatLon(c.getDouble("lat"), c.getDouble("lon"))
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = SpotConfig.TIMEOUT_MS
            c.readTimeout = SpotConfig.TIMEOUT_MS
            c.setRequestProperty("User-Agent", SpotConfig.USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = lat1 * PI / 180
        val p2 = lat2 * PI / 180
        val dp = (lat2 - lat1) * PI / 180
        val dl = (lon2 - lon1) * PI / 180
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }
}
