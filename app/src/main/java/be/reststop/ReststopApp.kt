package be.reststop

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class ReststopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // OSMDroid: User-Agent en cachemap instellen (verplicht door OSM-tileserver)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        container = AppContainer(this)
    }
}
