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

data class MapPin(val id: String, val lat: Double, val lon: Double, val title: String)

/** OSMDroid-kaart met levenscyclus (onResume/onPause/onDetach), markers, eigen locatie en tikken. */
@Composable
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
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoom)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    LaunchedEffect(center) {
        center?.let { mapView.controller.setCenter(OsmGeoPoint(it.lat, it.lon)) }
    }
    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.setMultiTouchControls(interactive)
        if (!interactive) map.setOnTouchListener { _, _ -> true }
        map.overlays.clear()
        if (onMapTap != null) {
            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                    onMapTap(p.latitude, p.longitude)
                    return true
                }
                override fun longPressHelper(p: OsmGeoPoint): Boolean = false
            }))
        }
        pins.forEach { pin ->
            map.overlays.add(Marker(map).apply {
                position = OsmGeoPoint(pin.lat, pin.lon)
                title = pin.title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { m, _ ->
                    if (onPinClick != null) { onPinClick(pin.id); true } else { m.showInfoWindow(); true }
                }
            })
        }
        me?.let {
            map.overlays.add(Marker(map).apply {
                position = OsmGeoPoint(it.lat, it.lon)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_me)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        map.invalidate()
    })
}
