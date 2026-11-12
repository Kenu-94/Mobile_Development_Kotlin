package be.reststop.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import be.reststop.*
import be.reststop.R
import be.reststop.data.Reservation

private data class MerchantTab(val route: String, @StringRes val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantApp(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        MerchantTab("bags", R.string.tab_my_bags, Icons.AutoMirrored.Filled.List),
        MerchantTab("store", R.string.tab_my_store, Icons.Default.Home)
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
        NavHost(nav, startDestination = "bags", modifier = Modifier.padding(pad)) {
            composable("bags") { MyBagsScreen(onNewBag = { nav.navigate("newbag") }, onGoToStore = { nav.goTab("store") }) }
            composable("store") { StoreScreen() }
            composable("newbag") { NewBagScreen(onSaved = { nav.popBackStack() }) }
        }
    }
}

// ---------------- Mijn winkel ----------------

@Composable
fun StoreScreen() {
    val c = LocalContext.current.container
    val vm: StoreViewModel = viewModel(factory = vmFactory { StoreViewModel(c) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pins = if (ui.lat != null && ui.lon != null) listOf(MapPin("store", ui.lat!!, ui.lon!!, ui.name)) else emptyList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(ui.name, vm::onName, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.store_name)) })
        Text(stringResource(R.string.category))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollCompat()) {
            CATEGORIES.forEach { cat ->
                FilterChip(ui.category == cat, { vm.onCategory(cat) }, { Text(stringResource(categoryRes(cat))) })
            }
        }
        Text(stringResource(R.string.store_pick_hint))
        OsmMap(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
            center = if (ui.lat != null && ui.lon != null) LatLon(ui.lat!!, ui.lon!!) else DEFAULT_CENTER,
            pins = pins, onMapTap = vm::onPin
        )
        if (ui.geocoding) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(ui.street, vm::onStreet, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.street)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ui.postcode, vm::onPostcode, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.postcode)) })
            OutlinedTextField(ui.city, vm::onCity, Modifier.weight(2f), singleLine = true, label = { Text(stringResource(R.string.city)) })
        }
        ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (ui.saved) Text(stringResource(R.string.store_saved), color = MaterialTheme.colorScheme.primary)
        if (ui.saving) CircularProgressIndicator() else Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

// ---------------- Mijn pakketten ----------------

@Composable
fun MyBagsScreen(onNewBag: () -> Unit, onGoToStore: () -> Unit) {
    val c = LocalContext.current.container
    val vm: MyBagsViewModel = viewModel(factory = vmFactory { MyBagsViewModel(c) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStore() }

    var codeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    val pending: Reservation? = ui.items.flatMap { it.reservations }.firstOrNull { it.id == codeFor }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { codeFor = null; code = "" },
            title = { Text(stringResource(R.string.check_code_title)) },
            text = {
                OutlinedTextField(code, { code = it.uppercase().take(6) }, singleLine = true, label = { Text(stringResource(R.string.pickup_code)) })
            },
            confirmButton = {
                TextButton(onClick = { vm.collect(pending, code); codeFor = null; code = "" }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { codeFor = null; code = "" }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (!ui.hasStore && !ui.loading) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.create_store_first))
                    Button(onClick = onGoToStore) { Text(stringResource(R.string.go_to_store)) }
                }
            }
        }
        ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        Button(onClick = onNewBag, enabled = ui.hasStore, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.new_bag))
        }
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.items.isEmpty() -> Text(stringResource(R.string.no_own_bags), Modifier.padding(16.dp))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ui.items, key = { it.bag.id }) { item ->
                    val bag = item.bag
                    val live = item.reservations.filter { it.status != "cancelled" }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Base64Image(bag.photoBase64, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), sample = 4)
                                Column(Modifier.weight(1f)) {
                                    Text(bag.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.pickup_window, bag.pickupStart.hm(), bag.pickupEnd.hm()), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.stock, bag.quantityLeft, bag.quantityTotal), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.reservation_count, live.size), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Switch(bag.active, { vm.setActive(bag, it) })
                                    Text(stringResource(if (bag.active) R.string.online else R.string.offline), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            live.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${r.quantity}× · ${stringResource(statusRes(r.status))}", Modifier.weight(1f))
                                    if (r.status == "reserved") TextButton(onClick = { codeFor = r.id }) { Text(stringResource(R.string.mark_collected)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Pakket aanmaken ----------------

@Composable
fun NewBagScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val vm: NewBagViewModel = viewModel(factory = vmFactory { NewBagViewModel(c) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var original by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("1") }
    var startMin by rememberSaveable { mutableIntStateOf(17 * 60) }
    var endMin by rememberSaveable { mutableIntStateOf(18 * 60) }

    LaunchedEffect(ui.saved) { if (ui.saved) onSaved() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::onPhoto) }
    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }
    fun fmt(min: Int) = String.format(java.util.Locale.getDefault(), "%02d:%02d", min / 60, min % 60)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.bag_title)) })
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.bag_description)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                price, { price = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.bag_price)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                original, { original = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.bag_original)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        OutlinedTextField(
            qty, { qty = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.bag_quantity)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickTime(startMin) { startMin = it } }, Modifier.weight(1f)) { Text(stringResource(R.string.from_time, fmt(startMin))) }
            OutlinedButton(onClick = { pickTime(endMin) { endMin = it } }, Modifier.weight(1f)) { Text(stringResource(R.string.until_time, fmt(endMin))) }
        }
        if (ui.photoBase64.isNotEmpty()) Base64Image(ui.photoBase64, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
        if (ui.photoLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !ui.photoLoading, modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(if (ui.photoBase64.isEmpty()) R.string.add_photo else R.string.change_photo)) }
        ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (ui.saving) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { vm.save(title, description, price, original, qty, startMin, endMin) },
                enabled = !ui.photoLoading, modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.publish_bag)) }
        }
    }
}
