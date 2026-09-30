package app.elsewhere.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpotTest {
    private val kyoto = City(1857910, "JP", "Kyoto", "Kyoto", 35.02107, 135.75385, 50)

    @Test fun cityPointWhenNothingElse() {
        val s = effectiveSpot(kyoto, Stored())
        assertEquals(SpotSource.City, s.source)
        assertEquals(35.02107, s.lat, 0.0)
    }

    @Test fun hotelBeatsCityAndNoneNearbyKeepsCity() {
        val hotel = LatLon(35.01, 135.76)
        assertEquals(SpotSource.Hotel, effectiveSpot(kyoto, Stored(hotelLookups = mapOf(kyoto.id to hotel))).source)
        assertEquals(SpotSource.City, effectiveSpot(kyoto, Stored(hotelLookups = mapOf(kyoto.id to null))).source)
    }

    @Test fun customBeatsEverything() {
        val mine = LatLon(35.0, 135.7)
        val s = effectiveSpot(kyoto, Stored(customSpots = mapOf(kyoto.id to mine), hotelLookups = mapOf(kyoto.id to LatLon(1.0, 1.0))))
        assertEquals(SpotSource.Custom, s.source)
        assertEquals(mine.lat, s.lat, 0.0)
    }

    @Test fun distance() {
        // Tokyo Metropolitan Government Building to Keio Plaza Hotel is about 200 m
        val d = NearestHotel.distanceM(35.6895, 139.69171, 35.6893, 139.6939)
        assertTrue(d in 150.0..260.0)
    }

    /** Hits Photon/Overpass for real: ./gradlew testDebugUnitTest -Pelsewhere.liveNetwork=true */
    @Test fun liveLookupFindsAHotelNearTokyo() {
        assumeTrue(System.getProperty("elsewhere.liveNetwork") == "true")
        val r = runBlocking { NearestHotel.find(35.6895, 139.69171) }
        println("live lookup: $r")
        assertTrue(r is HotelLookup.Found)
        val at = (r as HotelLookup.Found).at
        assertTrue(NearestHotel.distanceM(35.6895, 139.69171, at.lat, at.lon) < 1000)
    }
}
