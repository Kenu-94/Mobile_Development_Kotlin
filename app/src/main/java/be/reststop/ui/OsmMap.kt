package be.reststop.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import be.reststop.LatLon
import be.reststop.R
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint as OsmGeoPoint

//herbruikbaar openstreetmap kaart component


//data class voor alle gegevns van een pin dat je op d ekaart doe
//let en lon zijn voor de coordinaten
data class MapPin(val id: String, val lat: Double, val lon: Double, val title: String)

// OSMDroid-kaart met levenscyclus (onResume/onPause/onDetach), markers, eigen locatie en tikken.
@Composable
//center:de locatie waar op gecenteerd moet worden
//pins: lijst met lcoaties die als markers op de kaart meoten weergegeven wordne
//me locatie van apaaraat
//als de gebruikt op de kaart klikt, krijgt die een callback (dit)die een lat en lon geeft
//onpickclick:ook een callback, die de id geeft van de marker waar d egebruik op klik
//true het is interactief!
fun OsmMap(
    modifier: Modifier = Modifier,
    center: LatLon?,
    zoom: Double = 14.0,
    pins: List<MapPin> = emptyList(),
    me: LatLon? = null,
    onMapTap: ((Double, Double) -> Unit)? = null,
    onPinClick: ((String) -> Unit)? = null,
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val mapView = remember {
        //door rememeber onthoud de mapview
        MapView(context).apply {
            //osmdroid kaart wordt gemaakt,
            setTileSource(TileSourceFactory.MAPNIK)
            //welke kaartgege en kaartweergave gebruikt wordt, we doen gwn standaard mapnik
            setMultiTouchControls(true)
            //kan met meerder vingers werken
            controller.setZoom(zoom)
        }
    }
    //lifecycle van deze scherm pakken
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, mapView) {
        //iets starten dat gekoppeld is met de mifecycle
        //zoals de observer
        val observer = LifecycleEventObserver { _, event ->
            //voortuderen reageren op de lfiecycle events
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                //scherm teurg actief? resume en osmdroid is er weer
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                //scherm op pauze? pauzeer de processen van de kaart enz
                else -> Unit
            }
        }
        //obserev registeren
        owner.lifecycle.addObserver(observer)
        onDispose {
            //compose effect=verwijder => observer loskoppelen,
            // kaart wordt detached
            owner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    //coroutine uitvoeren, als center verandert gaat het effect opnieuw
    LaunchedEffect(center) {
        //als cente rgeen nul is wordt kaart naar d elocatie verplaats
        //locatie wordt alleen gebruitk als die ebstaat
        //osmgeopoint zet lat en long om naar locatieformaat dat osmdroid verwacht
        center?.let { mapView.controller.setCenter(OsmGeoPoint(it.lat, it.lon)) }
    }
    //kaart tonen in compose weergaven
    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.setMultiTouchControls(interactive)
        if (!interactive) map.setOnTouchListener { _, _ -> true }
        //kan je d ekaart niet meer bedienen, gene interactie maar
        //wel dat je aanraakt en dat het wordt gzvaange door ene listerner
        map.overlays.clear()
        if (onMapTap != null) {
            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
              //als de gebruiker gwn 1 tik doet bij de kaart, p is de locatie dat getikt werd
                override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                    onMapTap(p.latitude, p.longitude)
                  //coordinatien vna die tik
                    return true
                }
                //er wordt niets gedana als je lang klikt
                override fun longPressHelper(p: OsmGeoPoint): Boolean = false
            }))
        }
        //voor elke pin wordt een marker gemaakt
        pins.forEach { pin ->
            map.overlays.add(Marker(map).apply {
                //osmdroid marker maken voor de huidige kaart
               //postiei van deze marker
                position = OsmGeoPoint(pin.lat, pin.lon)
                title = pin.title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { m, _ ->
                    //dit wwordt uitgevoerd bij het klikken
                    if (onPinClick != null) { onPinClick(pin.id); true } else { m.showInfoWindow(); true }
                }
            })
        }
        me?.let {
            //eigen lcoatie als marker beschikbaarheid checken
            map.overlays.add(Marker(map).apply {
                //maakt een maker voor huide eigne locatie
                //marker plaatse op de coordinaten vna eigne lcoatie
                position = OsmGeoPoint(it.lat, it.lon)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_me)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        map.invalidate()
        //opnieuw geteknt voor update te tonen
    })
}
