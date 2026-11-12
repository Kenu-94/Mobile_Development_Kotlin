package be.reststop.ui

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.reststop.*
import be.reststop.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data object Customer : SessionState
    data object Merchant : SessionState
    data class Error(@StringRes val message: Int) : SessionState
}

/** Bepaalt bij het starten meteen het juiste startscherm voor de rol (A2). */
class SessionViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SessionState.Loading
            _state.value = try {
                when (c.authRepo.role()) {
                    "customer" -> SessionState.Customer
                    "merchant" -> SessionState.Merchant
                    else -> {
                        if (c.authRepo.uid != null) c.authRepo.logout()
                        SessionState.LoggedOut
                    }
                }
            } catch (e: Exception) {
                SessionState.Error(e.toMessageRes())
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            c.authRepo.logout()
            c.room.reservationDao().clear() // persoonlijke offline data van vorige gebruiker wissen
            _state.value = SessionState.LoggedOut
        }
    }
}

data class AuthUiState(
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    @StringRes val info: Int? = null
)

class AuthViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun submit(register: Boolean, name: String, email: String, password: String, role: String, onSuccess: () -> Unit) {
        val mail = email.trim()
        val error = when {
            register && name.isBlank() -> R.string.err_required
            mail.isEmpty() || password.isEmpty() -> R.string.err_required
            !Patterns.EMAIL_ADDRESS.matcher(mail).matches() -> R.string.err_invalid_email
            register && password.length < 6 -> R.string.err_weak_password
            else -> null
        }
        if (error != null) { _ui.value = AuthUiState(error = error); return }
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            try {
                if (register) c.authRepo.register(name.trim(), mail, password, role)
                else c.authRepo.login(mail, password)
                _ui.value = AuthUiState()
                onSuccess()
            } catch (e: Exception) {
                _ui.value = AuthUiState(error = e.toMessageRes())
            }
        }
    }

    fun reset(email: String) {
        val mail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            _ui.value = AuthUiState(error = R.string.err_invalid_email); return
        }
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            _ui.value = try {
                c.authRepo.resetPassword(mail)
                AuthUiState(info = R.string.reset_sent)
            } catch (e: Exception) {
                AuthUiState(error = e.toMessageRes())
            }
        }
    }
}
