package pk.psx.wealth.feature.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SecurityGate(state: SecurityUiState, viewModel: SecurityViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(state.settings.privacyScreen, context) {
        val window = context.findActivity()?.window
        if (state.settings.privacyScreen) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (state.settings.privacyScreen) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    when {
        !state.loaded -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        state.unlocked -> content()
        else -> LockScreen(state, viewModel)
    }
}

@Composable
private fun LockScreen(state: SecurityUiState, viewModel: SecurityViewModel) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PSX Wealth is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Portfolio data stays on this device.", modifier = Modifier.padding(vertical = 12.dp))
        if (state.settings.pinVerifier != null) {
            OutlinedTextField(
                pin,
                { pin = it.filter(Char::isDigit).take(8) },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.verifyPin(pin); pin = "" }, enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Unlock") }
        }
        if (state.settings.biometricEnabled) {
            OutlinedButton(
                onClick = {
                    requestDeviceAuthentication(context, viewModel::biometricAuthenticated, viewModel::biometricFailed)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Use biometric or device lock") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    }
}

fun deviceAuthenticationAvailable(context: Context): Boolean = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
    BiometricManager.BIOMETRIC_SUCCESS

fun requestDeviceAuthentication(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findActivity() as? FragmentActivity
    if (activity == null) {
        onError("Device authentication is unavailable")
        return
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        override fun onAuthenticationFailed() = onError("Authentication was not recognized")
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock PSX Wealth")
        .setSubtitle("Confirm with your device security")
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL
