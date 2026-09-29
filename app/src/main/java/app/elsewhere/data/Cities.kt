package app.elsewhere.data

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

@Immutable
class City(
    val id: Int,
    val code: String,
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val elevation: Int,
) {
    internal val normName = norm(name)
    internal val normRegion = norm(region)
    override fun equals(other: Any?) = other is City && other.id == id
    override fun hashCode() = id
}

@Immutable
class Country(val code: String, val name: String, val cities: List<City>) {
    internal val normName = norm(name)
    override fun equals(other: Any?) = other is Country && other.code == code
    override fun hashCode() = code.hashCode()
}

@Immutable
class CityData(val countries: List<Country>) {
    val byCode: Map<String, Country> = countries.associateBy { it.code }
    val byId: Map<Int, City> = HashMap<Int, City>(40_000).also { m -> countries.forEach { c -> c.cities.forEach { m[it.id] = it } } }

    fun country(code: String): Country? = byCode[code]
    fun city(id: Int): City? = byId[id]

    /** Prototype filter: `norm(c.name).includes(q)`. */
    fun searchCountries(query: String): List<Country> {
        val q = norm(query.trim())
        if (q.isEmpty()) return countries
        return countries.filter { it.normName.contains(q) }
    }

    /** Prototype filter: name OR region contains the query. */
    fun searchCities(code: String, query: String): List<City> {
        val all = byCode[code]?.cities ?: return emptyList()
        val q = norm(query.trim())
        if (q.isEmpty()) return all
        return all.filter { it.normName.contains(q) || it.normRegion.contains(q) }
    }

    companion object {
        /** Kyoto, the design's default city. */
        const val DEFAULT_CITY_ID = 1857910
    }
}

private val Marks = Regex("[̀-ͯ]")

/** Prototype `norm()`: NFD, strip combining marks, lowercase. */
fun norm(s: String): String = Marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

/** English country name from the platform's locale data. */
fun countryName(code: String): String {
    val n = Locale("", code).getDisplayCountry(Locale.ENGLISH)
    return if (n.isNullOrBlank() || n == code) code else n
}

/** Loads the bundled GeoNames asset once, off the main thread. */
object CityRepository {
    private var loading: Deferred<CityData>? = null
    /** Process-wide, so no caller's lifecycle (a stopping service, a cleared ViewModel) can cancel the shared load. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun load(context: Context): Deferred<CityData> =
        loading ?: scope.async { parse(context.applicationContext) }.also { loading = it }

    fun parse(context: Context): CityData {
        val countries = ArrayList<Country>(250)
        context.assets.open("cities.tsv").use { raw ->
            BufferedReader(InputStreamReader(raw, Charsets.UTF_8), 1 shl 16).use { r ->
                var code = ""
                var regions: List<String> = emptyList()
                var cities = ArrayList<City>()
                fun flush() {
                    if (code.isNotEmpty() && cities.isNotEmpty()) countries += Country(code, countryName(code), cities)
                }
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isEmpty()) continue
                    when (line[0]) {
                        '#' -> { flush(); code = line.substring(1); cities = ArrayList() }
                        '@' -> regions = if (line.length > 1) line.substring(1).split('|') else emptyList()
                        else -> {
                            val p = line.split('\t')
                            val ri = p[2].toInt()
                            cities += City(
                                id = p[0].toInt(), code = code, name = p[1],
                                region = if (ri >= 0) regions[ri] else "",
                                lat = p[3].toDouble(), lon = p[4].toDouble(), elevation = p[5].toInt(),
                            )
                        }
                    }
                }
                flush()
            }
        }
        // Alphabetical and accent-insensitive, like the prototype's sorted list.
        countries.sortWith(compareBy<Country> { it.normName }.thenBy { it.code })
        return CityData(countries)
    }
}
