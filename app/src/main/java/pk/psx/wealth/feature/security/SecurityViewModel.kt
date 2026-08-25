package pk.psx.wealth.feature.security

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.preferences.AppSettings
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.data.security.PinSecurity
import javax.inject.Inject

data class SecurityUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val requiresUnlock: Boolean = false,
    val unlocked: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val pinSecurity: PinSecurity,
) : ViewModel() {
    private val sessionUnlocked = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var backgroundAt: Long? = null

    val state: StateFlow<SecurityUiState> = combine(settings.settings, sessionUnlocked, error) { preferences, unlocked, message ->
        val required = preferences.pinVerifier != null || preferences.biometricEnabled
        SecurityUiState(preferences, true, required, !required || unlocked, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

    fun setPin(pin: String, confirmation: String) = viewModelScope.launch {
        runCatching {
            require(pin == confirmation) { "PIN confirmation does not match" }
            val verifier = pinSecurity.createVerifier(pin)
            settings.update { it.copy(pinVerifier = verifier) }
            sessionUnlocked.value = true
        }.onFailure { error.value = it.message }
    }

    fun verifyPin(pin: String) {
        val verifier = state.value.settings.pinVerifier
        if (verifier != null && pinSecurity.verify(pin, verifier)) {
            sessionUnlocked.value = true
            error.value = null
        } else error.value = "Incorrect PIN"
    }

    fun disablePin() = viewModelScope.launch {
        settings.update { it.copy(pinVerifier = null) }
        pinSecurity.clearKey()
        if (!state.value.settings.biometricEnabled) sessionUnlocked.value = true
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        settings.update { it.copy(biometricEnabled = enabled) }
        if (enabled) sessionUnlocked.value = true
    }

    fun setPrivacyScreen(enabled: Boolean) = viewModelScope.launch { settings.update { it.copy(privacyScreen = enabled) } }
    fun setAutoLock(minutes: Int) = viewModelScope.launch { settings.update { it.copy(autoLockMinutes = minutes.coerceIn(1, 120)) } }
    fun biometricAuthenticated() { sessionUnlocked.value = true; error.value = null }
    fun biometricFailed(message: String) { error.value = message }
    fun dismissError() { error.value = null }
    fun lockNow() { if (state.value.requiresUnlock) sessionUnlocked.value = false }

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }
    fun onForeground() {
        val elapsed = backgroundAt?.let { SystemClock.elapsedRealtime() - it } ?: return
        if (elapsed >= state.value.settings.autoLockMinutes * 60_000L) lockNow()
        backgroundAt = null
    }
}
