package be.reststop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.reststop.R
import be.reststop.container
import be.reststop.vmFactory

@Composable
fun RootApp() {
    val c = LocalContext.current.container
    val session: SessionViewModel = viewModel(factory = vmFactory { SessionViewModel(c) })
    val state by session.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SessionState.LoggedOut -> LoginScreen(onLoggedIn = session::refresh)
        SessionState.Customer -> CustomerApp(onLogout = session::logout)
        SessionState.Merchant -> MerchantApp(onLogout = session::logout)
        is SessionState.Error -> Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(s.message), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = session::refresh) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = session::logout) { Text(stringResource(R.string.logout)) }
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val c = LocalContext.current.container
    val vm: AuthViewModel = viewModel(factory = vmFactory { AuthViewModel(c) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("customer") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.tagline), textAlign = TextAlign.Center)
        if (register) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.name)) })
        }
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        if (register) {
            Text(stringResource(R.string.role_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(role == "customer", { role = "customer" }, { Text(stringResource(R.string.role_customer)) })
                FilterChip(role == "merchant", { role = "merchant" }, { Text(stringResource(R.string.role_merchant)) })
            }
        }
        ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        ui.info?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        if (ui.loading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { vm.submit(register, name, email, password, role, onLoggedIn) }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (register) R.string.register else R.string.login))
            }
        }
        TextButton(onClick = { register = !register }) {
            Text(stringResource(if (register) R.string.have_account else R.string.no_account))
        }
        if (!register) TextButton(onClick = { vm.reset(email) }) { Text(stringResource(R.string.forgot_password)) }
    }
}
