package be.reststop

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

//intilaiseer de app bij het starten, osmdoidrd opstarten, en de appcontainer maken
class ReststopApp : Application() {
    //zorgt dat onze app corect wordt ogpestald, dat de instleleing en verbinding klaastaan bij he topenen van de app
    //deze app wordt dan ernaa door d emainactivity opgestart
    lateinit var container: AppContainer
        private set

    //stelt de osmdroid in
    override fun onCreate() {
        super.onCreate()
        // OSMDroid: User-Agent en cachemap instellen (verplicht door OSM-tileserver)
        Configuration.getInstance().apply {
            //zegt welke app(packagenaam) de openstreetmap kaart gebruikt
            userAgentValue = packageName
            //we keizne waar de kaartgevene tijdelijke wordt bijgehoudne (bestand osmdroid)
            osmdroidBasePath = File(cacheDir, "osmdroid")
            //we keizen waar die kaart stukjes in de cache wordt bewaard, das hetzelfde map als het voirge
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        //maakt gebruikt van die appcontainer waardoor er vebrinding met het firebase, room, firestoren nominatim enz is
        container = AppContainer(this)
    }
}
