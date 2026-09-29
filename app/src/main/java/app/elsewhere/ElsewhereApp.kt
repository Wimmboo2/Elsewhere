package app.elsewhere

import android.app.Application
import app.elsewhere.data.CityRepository

class ElsewhereApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start parsing the city dataset right away, off the main thread; everything awaits the same Deferred.
        CityRepository.load(this)
    }
}
