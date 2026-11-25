package be.reststop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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

private data class NavTab(val route: String, @StringRes val label: Int, val icon: ImageVector)

fun NavController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerApp(onLogout: () -> Unit) {
    val c = LocalContext.current.container
    val uid = c.auth.currentUser?.uid.orEmpty()
    val home: HomeViewModel = viewModel(key = "home-$uid", factory = vmFactory { HomeViewModel(c) })
    val orders: OrdersViewModel = viewModel(key = "orders-$uid", factory = vmFactory { OrdersViewModel(c) })
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        NavTab("discover", R.string.tab_discover, Icons.Default.Home),
        NavTab("map", R.string.tab_map, Icons.Default.Place),
        NavTab("orders", R.string.tab_orders, Icons.Default.ShoppingCart),
        NavTab("favorites", R.string.tab_favorites, Icons.Default.Favorite)
    )
    val isTab = tabs.any { it.route == route }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (!isTab && route != null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isTab) IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.logout))
                    }
                }
            )
        },
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "discover", modifier = Modifier.padding(pad)) {
            composable("discover") {
                DiscoverScreen(
                    home,
                    onOpenBag = { nav.navigate("detail/$it") },
                    onOpenStore = { nav.navigate("storeDetail/$it") },
                    onPickMap = { nav.navigate("pick") }
                )
            }
            composable("map") {
                MapScreen(
                    home,
                    onOpenBag = { nav.navigate("detail/$it") },
                    onOpenStore = { nav.navigate("storeDetail/$it") },
                    onPickMap = { nav.navigate("pick") }
                )
            }
            composable("orders") {
                OrdersScreen(orders, onOpenStore = { nav.navigate("storeDetail/$it") })
            }
            composable("favorites") {
                FavoritesScreen(onOpenStore = { nav.navigate("storeDetail/$it") })
            }
            composable("detail/{bagId}") {
                DetailScreen(
                    onGoToOrders = { nav.popBackStack(); nav.goTab("orders") },
                    onOpenStore = { nav.navigate("storeDetail/$it") }
                )
            }
            composable("storeDetail/{storeId}") {
                StoreDetailScreen(onOpenBag = { nav.navigate("detail/$it") })
            }
            composable("pick") {
                val homeState by home.uiState.collectAsStateWithLifecycle()
                PickLocationScreen(initial = homeState.location) { lat, lon ->
                    home.setLocation(lat, lon)
                    nav.popBackStack()
                }
            }
        }
    }
}

// ---------------- Ontdekken ----------------

@Composable
fun DiscoverScreen(vm: HomeViewModel, onOpenBag: (String) -> Unit, onOpenStore: (String) -> Unit, onPickMap: () -> Unit) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text(
            s.city?.let { stringResource(R.string.near_city, it) } ?: stringResource(R.string.near_unknown),
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)
        )
        LocationPrompt(s, vm, onPickMap)

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.maxKm == null, { vm.setMaxKm(null) }, { Text(stringResource(R.string.all_distances)) })
            listOf(1, 5, 10).forEach { km ->
                FilterChip(s.maxKm == km, { vm.setMaxKm(km) }, { Text(stringResource(R.string.km_value, km)) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.category == null, { vm.setCategory(null) }, { Text(stringResource(R.string.all_categories)) })
            CATEGORIES.forEach { cat ->
                FilterChip(s.category == cat, { vm.setCategory(cat) }, { Text(stringResource(categoryRes(cat))) })
            }
        }
        s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        when {
            s.loading || s.locating -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.items.isEmpty() -> Text(stringResource(R.string.no_bags), modifier = Modifier.padding(16.dp))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.items, key = { it.bag.id }) { BagCard(it, onOpenStore = onOpenStore) { onOpenBag(it.bag.id) } }
            }
        }
    }
}

@Composable
private fun BagCard(item: BagItem, onOpenStore: ((String) -> Unit)? = null, onClick: () -> Unit) {
    val bag = item.bag
    Card(onClick = onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Base64Image(bag.photoBase64, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)), sample = 4)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    bag.storeName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = if (onOpenStore != null && bag.storeId.isNotBlank()) Modifier.clickable { onOpenStore(bag.storeId) } else Modifier
                )
                Text(bag.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.price_eur, bag.price), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.price_eur, bag.originalValue),
                        textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(stringResource(R.string.pickup_window, bag.pickupStart.hm(), bag.pickupEnd.hm()), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.left_count, bag.quantityLeft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    item.distanceKm?.let {
                        Text(stringResource(R.string.distance_km, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------- Kaart ----------------

@Composable
fun MapScreen(vm: HomeViewModel, onOpenBag: (String) -> Unit, onOpenStore: (String) -> Unit, onPickMap: () -> Unit) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pins = s.items.distinctBy { it.bag.storeId }.map {
        MapPin(it.bag.storeId, it.bag.location.latitude, it.bag.location.longitude, it.bag.storeName)
    }
    val selectedBags = s.items.filter { it.bag.storeId == selected }

    Column(Modifier.fillMaxSize()) {
        LocationPrompt(s, vm, onPickMap)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(Modifier.fillMaxSize(), center = s.location ?: DEFAULT_CENTER, pins = pins, me = s.location, onPinClick = { selected = it })
            if (s.loading || s.locating) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(16.dp))
            if (selectedBags.isNotEmpty()) {
                val storeId = selectedBags.first().bag.storeId
                Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            selectedBags.first().bag.storeName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (storeId.isNotBlank()) Modifier.clickable { onOpenStore(storeId) } else Modifier
                        )
                        selectedBags.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.bag.title, Modifier.weight(1f))
                                Button(onClick = { onOpenBag(item.bag.id) }) { Text(stringResource(R.string.view)) }
                            }
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
fun DetailScreen(onGoToOrders: () -> Unit, onOpenStore: (String) -> Unit) {
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
                    Text(
                        bag.storeName,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = if (bag.storeId.isNotBlank()) Modifier.clickable { onOpenStore(bag.storeId) } else Modifier
                    )
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

// ---------------- Winkel Detail ----------------

@Composable
fun StoreDetailScreen(onOpenBag: (String) -> Unit) {
    val c = LocalContext.current.container
    val vm: StoreDetailViewModel = viewModel(factory = vmFactory { StoreDetailViewModel(c, createSavedStateHandle()) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val store = ui.store

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (store == null) {
        Text(stringResource(ui.error ?: R.string.store_not_found), Modifier.padding(16.dp))
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(store.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(categoryRes(store.category)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = vm::toggleFavorite) {
                    Icon(
                        if (ui.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text("${store.address}, ${store.postcode} ${store.city}".trim(',', ' '), style = MaterialTheme.typography.bodyLarge)

            OsmMap(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                center = LatLon(store.location.latitude, store.location.longitude), zoom = 16.0,
                pins = listOf(MapPin(store.ownerId, store.location.latitude, store.location.longitude, store.name)),
                interactive = false
            )

            if (store.email.isNotBlank() || !store.phone.isNullOrBlank()) {
                HorizontalDivider()
                Text(stringResource(R.string.store_contact), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (store.email.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(store.email, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!store.phone.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(store.phone, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.available_bags), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (ui.bags.isEmpty()) {
                Text(stringResource(R.string.no_store_bags), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ui.bags.forEach { bag ->
                        BagCard(BagItem(bag, null)) { onOpenBag(bag.id) }
                    }
                }
            }
        }
    }
}

// ---------------- Bestellingen ----------------

@Composable
fun OrdersScreen(vm: OrdersViewModel, onOpenStore: (String) -> Unit) {
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
                    items(active, key = { it.id }) { OrderCard(it, onOpenStore = onOpenStore, onCancel = { vm.cancel(it.id) }) }
                    if (past.isNotEmpty()) item { Text(stringResource(R.string.past_orders), style = MaterialTheme.typography.titleMedium) }
                    items(past, key = { it.id }) { OrderCard(it, onOpenStore = onOpenStore, onCancel = null) }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: CachedReservation, onOpenStore: (String) -> Unit, onCancel: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                order.storeName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (order.storeId.isNotBlank()) Modifier.clickable { onOpenStore(order.storeId) } else Modifier
            )
            Text("${order.quantity} × ${order.bagTitle}", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pickup_window, order.pickupStart.hm(), order.pickupEnd.hm()))
            Text(stringResource(statusRes(order.status)), color = MaterialTheme.colorScheme.secondary)
            if (order.status == "reserved") {
                Text(stringResource(R.string.pickup_code_label, order.pickupCode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (onCancel != null && order.status == "reserved" && order.pickupStart > System.currentTimeMillis()) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel_order)) }
            }
        }
    }
}

// ---------------- Favorieten ----------------

@Composable
fun FavoritesScreen(onOpenStore: (String) -> Unit) {
    val c = LocalContext.current.container
    val uid = c.auth.currentUser?.uid.orEmpty()
    val vm: FavoritesViewModel = viewModel(key = "favs-$uid", factory = vmFactory { FavoritesViewModel(c) })
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    if (favorites.isEmpty()) {
        Text(stringResource(R.string.no_favorites), Modifier.padding(16.dp))
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites, key = { it.storeId }) { fav -> FavoriteRow(fav, onOpenStore = onOpenStore) { vm.remove(fav.storeId) } }
        }
    }
}

@Composable
private fun FavoriteRow(fav: FavoriteStore, onOpenStore: (String) -> Unit, onRemove: () -> Unit) {
    Card(onClick = { if (fav.storeId.isNotBlank()) onOpenStore(fav.storeId) }, Modifier.fillMaxWidth()) {
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
