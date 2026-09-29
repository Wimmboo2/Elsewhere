package app.elsewhere.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CityDataTest {
    private val data by lazy { CityRepository.parse(ApplicationProvider.getApplicationContext()) }

    @Test fun parsesDataset() {
        val t0 = System.currentTimeMillis()
        val d = data
        println("parsed ${d.countries.size} countries, ${d.byId.size} cities in ${System.currentTimeMillis() - t0}ms")
        assertTrue(d.countries.size > 200)
        assertEquals("Kyoto", d.city(CityData.DEFAULT_CITY_ID)?.name)
        assertEquals("Japan", d.country("JP")?.name)
    }

    @Test fun searchIsAccentInsensitive() {
        assertTrue(data.searchCities("IS", "reykjavik").any { it.name == "Reykjavík" })
        assertTrue(data.searchCountries("turk").isNotEmpty())
        assertTrue(data.searchCountries("Atlantis").isEmpty())
        // region match
        assertTrue(data.searchCities("JP", "kyoto").isNotEmpty())
    }

    @Test fun countriesAreSortedAccentInsensitive() {
        val names = data.countries.map { norm(it.name) }
        assertEquals(names.sorted(), names)
        // cities by population: the capital comes first for Japan
        assertEquals("Tokyo", data.country("JP")!!.cities.first().name)
    }

    @Test fun normMatchesPrototype() {
        assertEquals("sao paulo", norm("São Paulo"))
        assertEquals("hoi an", norm("Hội An"))
    }
}
