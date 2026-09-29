package app.elsewhere

import android.app.Application
import app.elsewhere.data.CityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ElsewhereApp : Application() {
    /** Process-wide scope for work that outlives a screen (dataset parsing). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Start parsing the city dataset right away, off the main thread; everything awaits the same Deferred.
        CityRepository.load(this, scope)
    }
}
