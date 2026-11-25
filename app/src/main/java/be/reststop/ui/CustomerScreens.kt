package be.reststop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import be.reststop.*
import be.reststop.R
import be.reststop.data.CachedReservation
import be.reststop.data.FavoriteStore
import java.util.Locale
// ui bestan voor de hele customer gedeelte en schermen
//userinterface, navigatie, ontdekkingspagina, kaart, pakketgeg
//bestellingen en favos
//jetpack compose voor de schermen bouwen en connecten met de viewmodels waar de logica is gemaakt


//data klass voor de verschillende tab van de klanten
//elke tab is een andere route voor navigatie
//een label verwijst naar een tekst rescource
//de icon gaan we tonen
private data class NavTab(val route: String, @StringRes val label: Int, val icon: ImageVector)

//hiermee kan je van een scherm naar andere scherm gaan
//door die popupto dat het stapel terug naar startscherm gaat
//launchsingletop= voorkoomt dat dezelfde table meerdere keren komt in het stack
//restorestate=een oude bewaarde state terug hersteld wordt wnr je terug gaat
fun NavController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

//hoofdcomposable functie voor de klanten
//bovenbalk, onderbalk, navigatie, gebruikersinterface van klanten is hier
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerApp(onLogout: () -> Unit) {
    val c = LocalContext.current.container
    //appcontainer wordt opgehaald van de context, daar zitten de repositroty, db enz
    val uid = c.auth.currentUser?.uid.orEmpty()
    //uid van ingelogd gebruiker wordt opgehaald
    // Per gebruiker een eigen ViewModel (sleutel = uid), zodat er na uitloggen geen oude data blijft hangen.
    val home: HomeViewModel = viewModel(key = "home-$uid", factory = vmFactory { HomeViewModel(c) })
    //viewmodel gemaakt/opgrhaald, homeviewmodel om de pakketen te bekijekn en opzoeken dus homepage
    val orders: OrdersViewModel = viewModel(key = "orders-$uid", factory = vmFactory { OrdersViewModel(c) })
    //ordersviewmodel om je bestelling te bekijken
    //vmfactory zorgt dat ze worden aangemaakt met de appcontainer
    //elke user heeft zijn eigne viewmodel, door de uid t egebruiken is elke viewmodel uniek
    val nav = rememberNavController()
    //die navigatiecontroller van bovenana wordt anagemaakt
    val entry by nav.currentBackStackEntryAsState()
    // we houdne bij welke scherm we nu zitten
    val route = entry?.destination?.route
    //naar waar we gaan, dus route van de hudige scherm
    val tabs = listOf(
        NavTab("discover", R.string.tab_discover, Icons.Default.Home),
        NavTab("map", R.string.tab_map, Icons.Default.Place),
        NavTab("orders", R.string.tab_orders, Icons.Default.ShoppingCart),
        NavTab("favorites", R.string.tab_favorites, Icons.Default.Favorite)
    )//de 4hoofd tab in de lijst, voor elke tab wordt een route aangeduid, tekst, en de icon ervoor
    val isTab = tabs.any { it.route == route }
    //checken als de huidige scherm een van die tabs zijn, zodat we laten
    //kunnen bepalen als we de onderbalk, uitlogknop^enz meoten tonen of niet

    Scaffold(
        topBar = {
            TopAppBar(
                //bovenbalk wordt gemaakt, met de naam, tekst wordt va de stringrescource gepakt
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    //back knop getoont als de klant niet in die hoofd tab zit van die lijst
                    //door die popbackstack kan de klant teurg gaan
                    if (!isTab && route != null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    //uitlogknop getoont bij de hoofdtab
                    if (isTab) IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.logout))
                    }
                }
            )
        },
        bottomBar = {
            //onderbalk, alleen als de gebruiker bij de hoofd tab is
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) }
                    )
                }//elke tab van die erder lijst overlope,
                // voor elek tab wordt er een navigatie item gemaakt met een icon
                //huidige tab is geselecteerd , en als je op een andere tab klik
                //wordt goTab uitgevoerd
            }
        }
    ) { pad ->
        //de scaffold wordt ni ingevuld, nahost beheert wat op het shcemr getootn wordt
        //app begint met het scherm discover (pakketen opzoeken enz)
        NavHost(nav, startDestination = "discover", modifier = Modifier.padding(pad)) {
            composable("discover") {
                DiscoverScreen(home, onOpenBag = { nav.navigate("detail/$it") }, onPickMap = { nav.navigate("pick") })
            }//wnr je bij de route discover ben word tdit getoont,discoverscreen,
            // homeviewmodel wordt gebruikt zodat de shcerm de gegevns bevat
            //als een pakket geklitk wordt, gaat de app anvigeren naar de detail scherm van dat pakket
            //via die onPickMap kan d eklant naar de scherm gaan bij de loactie die hij op de kaart kiest
            composable("map") {//zelfde
                MapScreen(home, onOpenBag = { nav.navigate("detail/$it") }, onPickMap = { nav.navigate("pick") })
            }
            composable("orders") { OrdersScreen(orders) }//toont order screen
            composable("favorites") { FavoritesScreen() }//toont favo screen
            composable("detail/{bagId}") { DetailScreen(onGoToOrders = { nav.popBackStack(); nav.goTab("orders") }) }
            //dit is die detailscreen van een pakket
            composable("pick") {
                //de scherm wordt geopend bij de lcoatie die ze keist,
                // huidige locatie is gegeven als beginpunt van de kaart
                //als de klant een neiwe locatie kiest wordn deze coordinaten opgeslagne bij home.setLocation()
                PickLocationScreen(initial = home.uiState.value.location) { lat, lon ->
                    home.setLocation(lat, lon)
                    nav.popBackStack()
                }
            }
        }
    }
}

///Ontdekken

//pagina om pakketne t eontdekken, door klanten
//heeft homeviewmodel maar 2 functies , pakket openene en naar locatie keuze gaan
//is volledig in state, en bij elke aanpassing gaat de scherm zelf refreshen
@Composable
fun DiscoverScreen(vm: HomeViewModel, onOpenBag: (String) -> Unit, onPickMap: () -> Unit) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        //alles wordt in ene kolom gedaan
        Text(
            //bestaat het stad niet? locatie onbekend tekst
            s.city?.let { stringResource(R.string.near_city, it) } ?: stringResource(R.string.near_unknown),
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)
        )
        LocationPrompt(s, vm, onPickMap)
        //locatieprompt getoont,dit zegt tgeen de gebruiker wat zijn locatie is en kan die aanapssen op kaart

      //afstand filters
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
           //filterchip, kan dan alle afstandne bekijken, als maxLm null is is dit geselecteerd
            //wordt uitgeschakeld bij een klik
            FilterChip(s.maxKm == null, { vm.setMaxKm(null) }, { Text(stringResource(R.string.all_distances)) })
            listOf(1, 5, 10).forEach { km ->
                //voor elke afstand is er een chip
                FilterChip(s.maxKm == km, { vm.setMaxKm(km) }, { Text(stringResource(R.string.km_value, km)) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.category == null, { vm.setCategory(null) }, { Text(stringResource(R.string.all_categories)) })
            CATEGORIES.forEach { cat ->
                //alle categorieen, wnr de gebruikt klikt gaat het ef
                FilterChip(s.category == cat, { vm.setCategory(cat) }, { Text(stringResource(categoryRes(cat))) })
            }
        }
        //bij fouten wordt er een foutmelding getoont, haalt van de string rescourc en een kleur
        s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        when {
            //controle als pakket nog geladen wordt of als locatie nog bepaalt wordt

            s.loading || s.locating -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.items.isEmpty() -> Text(stringResource(R.string.no_bags), modifier = Modifier.padding(16.dp))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.items, key = { it.bag.id }) { BagCard(it) { onOpenBag(it.bag.id) } }
            }
            //gene pakketne beschikbaar?: melding tonen
            //wel pakketn? :wordt getoont in een scrolbar elijst, via bagcard
        }
    }
}
//maakt een kaart voor elke pakket,die krijgt een bagitem dat bevat de pakket gegevens
// en afstand
@Composable
private fun BagCard(item: BagItem, onClick: () -> Unit) {
    val bag = item.bag
    //klik event
    Card(onClick = onClick, Modifier.fillMaxWidth()) {
        //inhoud van d ekaart wordt als row gedaan, links pakketfoto
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Base64Image(bag.photoBase64, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)), sample = 4)
           //en rechtd wordt de gegevens van die pakket gedaan, oner elkaar, winkel naam, titel
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bag.storeName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(bag.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              //prijs en og waarde wort ook getoont
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.price_eur, bag.price), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.price_eur, bag.originalValue),
                        textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline
                    )
                }
                //begin en eind daggtum dat het open staat voor afhaal moment
                Text(stringResource(R.string.pickup_window, bag.pickupStart.hm(), bag.pickupEnd.hm()), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    //hoeveel er nog van deze pakketne over zijn, ook de afstand van je locatie tot winkel wordt getppnt
                    Text(stringResource(R.string.left_count, bag.quantityLeft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    item.distanceKm?.let {
                        Text(stringResource(R.string.distance_km, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// map Kaart

//dit gaa ttonen hoeveel pakket er beschikbaar zijn bij de loacties enz,
//dit zal dezelfde hpomeviewmodel gebruioken als de discovery screen, met ui screen
@Composable
fun MapScreen(vm: HomeViewModel, onOpenBag: (String) -> Unit, onPickMap: () -> Unit) {
    val s by vm.uiState.collectAsStateWithLifecycle()
  //houd bij welke winkel de gebruiker heeft geklikt
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
   //beschikbaar pakketen wordt als pin gedaan bij het kaart,
    // elke winkel kan maar 1 pin hebben dus niet per pakket dat een plek heb ,
    // maar als een plek pakketen aanbied
    val pins = s.items.distinctBy { it.bag.storeId }.map {
        MapPin(it.bag.storeId, it.bag.location.latitude, it.bag.location.longitude, it.bag.storeName)
    }
    //alleen pakketne van de winkel dat d egebruiker selecteert, allene dez epakketen worden geselcteerd
    val selectedBags = s.items.filter { it.bag.storeId == selected }

    Column(Modifier.fillMaxSize()) {
        //locatieprompt eers boveaan in het kolom, zo kan d egebruoiker altijd zijn lcoatie aanapssen ofso als die wilt
        LocationPrompt(s, vm, onPickMap)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(Modifier.fillMaxSize(), center = s.location ?: DEFAULT_CENTER, pins = pins, me = s.location, onPinClick = { selected = it })
            //de kaart pakt alle schermruimte , osm toont de kaart met d ehudiige locatie als fucos, met d epins van alle winkels zo kan je zien welke winkels in je buurt ene pakket hebben
            //als je dan op een van die winkels pin klik wordt die winkel dan geselcteerd
            if (s.loading || s.locating) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(16.dp))
            //gwn loading tonen
            if (selectedBags.isNotEmpty()) {
                Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                   //info tonen over d eiwnkel als die paakket heeft
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                       //winkelnaam tonen,, eerste pakket van de winkel wordt eerst getoont, fifo systeem
                        //want selectedbags is voor dezelfde winkel
                        Text(selectedBags.first().bag.storeName, style = MaterialTheme.typography.titleMedium)
                        selectedBags.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.bag.title, Modifier.weight(1f))
                                Button(onClick = { onOpenBag(item.bag.id) }) { Text(stringResource(R.string.view)) }
                            }//pakketen van winkel worden uiteindelijk getoont, knop per pakket enz als je op d eknop klik
                            //worden de gegevns van dat pakket dan getoont
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PickLocationScreen(initial: LatLon?, onPicked: (Double, Double) -> Unit) {
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    val pins = if (lat != null && lon != null) listOf(MapPin("pin", lat!!, lon!!, "")) else emptyList()
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.pick_hint), Modifier.padding(16.dp))
        OsmMap(Modifier.weight(1f).fillMaxWidth(), center = initial ?: DEFAULT_CENTER, pins = pins, onMapTap = { a, b -> lat = a; lon = b })
        Button(
            onClick = { onPicked(lat!!, lon!!) }, enabled = lat != null && lon != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(stringResource(R.string.use_this_location)) }
    }
}

// ---------------- Detail ----------------

@Composable
fun DetailScreen(onGoToOrders: () -> Unit) {
    val c = LocalContext.current.container
    val vm: DetailViewModel = viewModel(factory = vmFactory { DetailViewModel(c, createSavedStateHandle()) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bag = ui.bag

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (bag == null) {
        Text(stringResource(ui.error ?: R.string.bag_not_found), Modifier.padding(16.dp))
        return
    }
    ui.code?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.reserved_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pickup_code_explain))
                    Spacer(Modifier.height(12.dp))
                    Text(code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissCode(); onGoToOrders() }) { Text(stringResource(R.string.to_orders)) } }
        )
    }
    val soldOut = bag.quantityLeft <= 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Base64Image(bag.photoBase64, Modifier.fillMaxWidth().height(220.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bag.title, style = MaterialTheme.typography.headlineSmall)
                    Text(bag.storeName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = vm::toggleFavorite) {
                    Icon(
                        if (ui.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            ui.store?.let { Text("${it.address}, ${it.postcode} ${it.city}", style = MaterialTheme.typography.bodyMedium) }
            Text(bag.description)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.price_eur, bag.price), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.price_eur, bag.originalValue),
                    textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline
                )
            }
            Text(stringResource(R.string.pickup_window, bag.pickupStart.hm(), bag.pickupEnd.hm()))
            Text(
                if (soldOut) stringResource(R.string.sold_out) else stringResource(R.string.left_count, bag.quantityLeft),
                color = MaterialTheme.colorScheme.secondary
            )
            OsmMap(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                center = LatLon(bag.location.latitude, bag.location.longitude), zoom = 16.0,
                pins = listOf(MapPin(bag.storeId, bag.location.latitude, bag.location.longitude, bag.storeName)),
                interactive = false
            )
            if (!soldOut) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.quantity))
                    OutlinedIconButton(onClick = vm::decrease, enabled = ui.quantity > 1) { Icon(Icons.Default.Clear, stringResource(R.string.less)) }
                    Text(ui.quantity.toString(), style = MaterialTheme.typography.titleLarge)
                    OutlinedIconButton(onClick = vm::increase, enabled = ui.quantity < bag.quantityLeft) { Icon(Icons.Default.Add, stringResource(R.string.more)) }
                }
            }
            ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (ui.reserving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(onClick = vm::reserve, enabled = !soldOut && !ui.reserving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reserve))
            }
        }
    }
}

// ---------------- Bestellingen ----------------

@Composable
fun OrdersScreen(vm: OrdersViewModel) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val list = orders
    Column(Modifier.fillMaxSize()) {
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(stringResource(R.string.no_orders), Modifier.padding(16.dp))
            else -> {
                val (active, past) = list.partition { it.status == "reserved" }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (active.isNotEmpty()) item { Text(stringResource(R.string.active_orders), style = MaterialTheme.typography.titleMedium) }
                    items(active, key = { it.id }) { OrderCard(it, onCancel = { vm.cancel(it.id) }) }
                    if (past.isNotEmpty()) item { Text(stringResource(R.string.past_orders), style = MaterialTheme.typography.titleMedium) }
                    items(past, key = { it.id }) { OrderCard(it, onCancel = null) }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: CachedReservation, onCancel: (() -> Unit)?) {
    val canCancel = onCancel != null && order.status == "reserved" && order.pickupStart > System.currentTimeMillis()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.storeName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${order.quantity} × ${order.bagTitle}", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pickup_window, order.pickupStart.hm(), order.pickupEnd.hm()))
            Text(stringResource(statusRes(order.status)), color = MaterialTheme.colorScheme.secondary)
            if (order.status == "reserved") {
                Text(stringResource(R.string.pickup_code_label, order.pickupCode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (canCancel) OutlinedButton(onClick = onCancel!!) { Text(stringResource(R.string.cancel_order)) }
        }
    }
}

// ---------------- Favorieten ----------------

@Composable
fun FavoritesScreen() {
    val c = LocalContext.current.container
    val vm: FavoritesViewModel = viewModel(factory = vmFactory { FavoritesViewModel(c) })
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    if (favorites.isEmpty()) {
        Text(stringResource(R.string.no_favorites), Modifier.padding(16.dp))
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites, key = { it.storeId }) { fav -> FavoriteRow(fav) { vm.remove(fav.storeId) } }
        }
    }
}

@Composable
private fun FavoriteRow(fav: FavoriteStore, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fav.name, style = MaterialTheme.typography.titleMedium)
                if (fav.address.isNotBlank()) Text(fav.address, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Favorite, stringResource(R.string.remove_favorite), tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
