package be.reststop.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.reststop.R
import be.reststop.findActivity

/**
 * Vraagt pas na uitleg en een actie van de gebruiker om de locatie (K1).
 * Gevallen: precies, benaderend, geweigerd, definitief geweigerd (knop naar instellingen).
 */
@Composable
fun LocationPrompt(state: HomeUiState, vm: HomeViewModel, onPickOnMap: () -> Unit) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanent by rememberSaveable { mutableStateOf(false) }
    var approximate by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            denied = false; permanent = false; approximate = !fine
            vm.fetchLocation()
        } else {
            denied = true
            val activity = context.findActivity()
            permanent = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Is de toestemming al eerder gegeven? Dan zonder vragen de locatie ophalen.
    LaunchedEffect(Unit) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted && state.location == null) vm.fetchLocation()
    }

    val request = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    if (state.location != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onPickOnMap) { Text(stringResource(R.string.change_location)) }
        }
        return
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.location_explain))
            if (approximate) Text(stringResource(R.string.location_approximate))
            if (denied) Text(
                stringResource(if (permanent) R.string.location_denied_permanent else R.string.location_denied),
                color = MaterialTheme.colorScheme.error
            )
            if (state.locating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (permanent) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }) { Text(stringResource(R.string.open_settings)) }
                    } else {
                        Button(onClick = { request() }) { Text(stringResource(R.string.allow_location)) }
                    }
                    OutlinedButton(onClick = onPickOnMap) { Text(stringResource(R.string.pick_on_map)) }
                }
            }
        }
    }
}
