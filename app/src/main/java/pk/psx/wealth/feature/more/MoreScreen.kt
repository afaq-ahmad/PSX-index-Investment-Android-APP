package pk.psx.wealth.feature.more

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.psx.wealth.BuildConfig
import pk.psx.wealth.data.backup.BackupKind
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.preferences.NumberFormatPreference
import pk.psx.wealth.data.preferences.RebalanceModePreference
import pk.psx.wealth.data.preferences.ThemePreference
import pk.psx.wealth.data.report.ReportType
import pk.psx.wealth.feature.app.AppUiState
import pk.psx.wealth.feature.security.SecurityViewModel
import pk.psx.wealth.feature.security.deviceAuthenticationAvailable
import pk.psx.wealth.feature.security.requestDeviceAuthentication
import pk.psx.wealth.ui.design.LabelValue
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId

private enum class MoreSection { SETTINGS, REPORTS, BACKUP, DIAGNOSTICS }

@Composable
fun MoreScreen(
    appState: AppUiState,
    onSelectPortfolio: (Long) -> Unit,
    onCreatePortfolio: () -> Unit,
    onArchivePortfolio: (Long) -> Unit,
    securityViewModel: SecurityViewModel,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val security by securityViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(MoreSection.SETTINGS) }
    var pendingArchive by remember { mutableStateOf<PortfolioEntity?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    val export = state.export
    LaunchedEffect(Unit) { viewModel.refreshCounts() }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val payload = export
        if (uri == null || payload == null) viewModel.exportHandled("Export cancelled")
        else scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { writeDocument(context, uri, payload.bytes) } }
            viewModel.exportHandled(result.fold({ "Saved ${payload.fileName}" }, { it.message ?: "Could not save export" }))
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { readDocument(context, uri) } }
                .onSuccess { viewModel.previewRestore(it) }
                .onFailure { viewModel.exportHandled(it.message ?: "Could not read backup") }
        }
    }
    LaunchedEffect(export?.id) { export?.let { createDocument.launch(it.fileName) } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoreSection.entries.forEach { item ->
                FilterChip(section == item, { section = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.titlecase() }) })
            }
        }
        if (state.busy) Text("Working locally…", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
        state.message?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) }
        when (section) {
            MoreSection.SETTINGS -> SettingsPane(state, appState, onSelectPortfolio, onCreatePortfolio,
                { pendingArchive = it }, viewModel, securityViewModel, security, { showPinDialog = true })
            MoreSection.REPORTS -> ReportsPane(state.busy, viewModel::prepareReport)
            MoreSection.BACKUP -> BackupPane(state.busy, viewModel::prepareBackup) { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            MoreSection.DIAGNOSTICS -> DiagnosticsPane(state)
        }
    }

    state.restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace local data from backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${preview.manifest.kind.name.lowercase().replaceFirstChar { it.titlecase() }} backup from ${preview.manifest.createdAt}")
                    LabelValue("Portfolios", preview.portfolioCount.toString())
                    LabelValue("Transactions", preview.transactionCount.toString())
                    LabelValue("Targets", preview.targetCount.toString())
                    LabelValue("Watchlists", preview.watchlistCount.toString())
                    LabelValue("Fundamentals", preview.fundamentalCount.toString())
                    LabelValue("Cached prices", preview.cachedPriceCount.toString())
                    preview.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("This replaces current portfolios and research. Create a backup first if you may need them.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(onClick = viewModel::confirmRestore, enabled = !state.busy) { Text("Replace and restore") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
    pendingArchive?.let { portfolio ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("Archive ${portfolio.name}?") },
            text = { Text("It disappears from active portfolios, but its ledger remains in local storage and backups.") },
            confirmButton = { Button(onClick = { onArchivePortfolio(portfolio.id); pendingArchive = null }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text("Cancel") } },
        )
    }
    if (showPinDialog) PinDialog(onDismiss = { showPinDialog = false }) { pin, confirmation ->
        securityViewModel.setPin(pin, confirmation)
        showPinDialog = false
    }
}

@Composable
private fun SettingsPane(
    state: MoreUiState,
    appState: AppUiState,
    onSelectPortfolio: (Long) -> Unit,
    onCreatePortfolio: () -> Unit,
    onArchivePortfolio: (PortfolioEntity) -> Unit,
    viewModel: MoreViewModel,
    securityViewModel: SecurityViewModel,
    security: pk.psx.wealth.feature.security.SecurityUiState,
    onSetPin: () -> Unit,
) {
    val context = LocalContext.current
    var reserve by remember(state.settings.defaultCashReserve) { mutableStateOf(state.settings.defaultCashReserve.toString()) }
    var minimum by remember(state.settings.defaultMinimumTrade) { mutableStateOf(state.settings.defaultMinimumTrade.toString()) }
    var autoLock by remember(state.settings.autoLockMinutes) { mutableStateOf(state.settings.autoLockMinutes.toString()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingHeader("Portfolios")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            appState.portfolios.forEach { portfolio ->
                FilterChip(appState.selectedPortfolioId == portfolio.id, { onSelectPortfolio(portfolio.id) }, label = { Text(portfolio.name) })
            }
        }
        Button(onClick = onCreatePortfolio, modifier = Modifier.fillMaxWidth()) { Text("Create another portfolio") }
        appState.portfolios.firstOrNull { it.id == appState.selectedPortfolioId }?.let { selected ->
            OutlinedButton(onClick = { onArchivePortfolio(selected) }, enabled = appState.portfolios.size > 1,
                modifier = Modifier.fillMaxWidth()) { Text("Archive selected portfolio") }
        }

        SettingHeader("Display")
        Text("Currency: PKR")
        ChoiceRow("Theme", ThemePreference.entries, state.settings.theme, viewModel::setTheme) { it.name.lowercase().replaceFirstChar { char -> char.titlecase() } }
        ChoiceRow("Number format", NumberFormatPreference.entries, state.settings.numberFormat, viewModel::setNumberFormat) {
            if (it == NumberFormatPreference.LAKH_CRORE) "Lakh/Crore" else "Standard"
        }

        SettingHeader("Portfolio defaults")
        ChoiceRow("Benchmark", listOf("KMI30", "KSE100", "KMIALLSHR"), state.settings.defaultBenchmark, viewModel::setBenchmark) { it }
        OutlinedTextField(reserve, { reserve = it }, label = { Text("Default cash reserve") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { viewModel.setCashReserve(reserve) }, modifier = Modifier.fillMaxWidth()) { Text("Save cash reserve") }
        OutlinedTextField(minimum, { minimum = it }, label = { Text("Default minimum trade") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { viewModel.setMinimumTrade(minimum) }, modifier = Modifier.fillMaxWidth()) { Text("Save minimum trade") }
        ChoiceRow("Default rebalance", RebalanceModePreference.entries, state.settings.defaultRebalanceMode, viewModel::setRebalanceMode) {
            if (it == RebalanceModePreference.CASH_ONLY) "Cash only" else "Full"
        }

        SettingHeader("Data")
        SwitchRow("Refresh on app open", "Off by default; uses cached data while refreshing.", state.settings.refreshOnOpen, viewModel::setRefreshOnOpen)
        SwitchRow("Daily background refresh", "At most once daily and network constrained.", state.settings.dailyRefresh, viewModel::setDailyRefresh)
        SwitchRow("Wi-Fi only", "Applies to optional background refresh.", state.settings.wifiOnly, viewModel::setWifiOnly)

        SettingHeader("Local security")
        if (security.settings.pinVerifier == null) Button(onClick = onSetPin, modifier = Modifier.fillMaxWidth()) { Text("Set app PIN") }
        else OutlinedButton(onClick = securityViewModel::disablePin, modifier = Modifier.fillMaxWidth()) { Text("Disable app PIN") }
        val authAvailable = deviceAuthenticationAvailable(context)
        SwitchRow("Biometric / device lock", if (authAvailable) "Use Android device authentication." else "Not available on this device.",
            security.settings.biometricEnabled, { enable ->
                if (!enable) securityViewModel.setBiometric(false)
                else requestDeviceAuthentication(context, {
                    securityViewModel.biometricAuthenticated(); securityViewModel.setBiometric(true)
                }, securityViewModel::biometricFailed)
            }, enabled = authAvailable)
        SwitchRow("Screen privacy", "Blocks screenshots and recent-app previews on this activity.", security.settings.privacyScreen,
            securityViewModel::setPrivacyScreen)
        OutlinedTextField(autoLock, { autoLock = it.filter(Char::isDigit) }, label = { Text("Auto-lock after minutes (1–120)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { securityViewModel.setAutoLock(autoLock.toIntOrNull() ?: 5) }, modifier = Modifier.fillMaxWidth()) { Text("Save auto-lock") }
        if (security.requiresUnlock) OutlinedButton(onClick = securityViewModel::lockNow, modifier = Modifier.fillMaxWidth()) { Text("Lock now") }
        security.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("PIN verification uses an HMAC key held by Android Keystore. Security preferences are intentionally excluded from backups.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReportsPane(busy: Boolean, onExport: (ReportType) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Offline CSV reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose a report and Android will ask where to save it. Unknown values are blank, not zero.")
        ReportType.entries.forEach { type ->
            OutlinedButton(onClick = { onExport(type) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(type.label()) }
        }
    }
}

@Composable
private fun BackupPane(busy: Boolean, onBackup: (BackupKind) -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup and restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Backups are versioned ZIP files chosen through Android storage. They never leave the device unless you move them.")
        Button(onClick = { onBackup(BackupKind.ESSENTIAL) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create essential backup") }
        Text("Portfolios, ledger, targets, watchlists, research and saved plans.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onBackup(BackupKind.FULL) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create full backup") }
        Text("Also includes cached quotes, price history and index snapshots; it may be much larger.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Choose backup to restore") }
        Text("A restore is validated and previewed before any current data is replaced.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DiagnosticsPane(state: MoreUiState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Data and diagnostics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelValue("Portfolios", state.counts.portfolios.toString())
                LabelValue("Transactions", state.counts.transactions.toString())
                LabelValue("Securities", state.counts.securities.toString())
                LabelValue("Latest quotes", state.counts.quotes.toString())
                LabelValue("Cached daily prices", state.counts.prices.toString())
                LabelValue("Index snapshots", state.counts.indexSnapshots.toString())
                LabelValue("Fundamental observations", state.counts.fundamentals.toString())
            }
        }
        if (state.diagnostics.isEmpty()) Text("No provider attempts have been recorded yet.")
        state.diagnostics.forEach { status ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(status.capability, fontWeight = FontWeight.Bold)
                    LabelValue("Provider", status.providerId)
                    LabelValue("Last attempt", timestamp(status.lastAttemptAt))
                    LabelValue("Last success", status.lastSuccessAt?.let(::timestamp) ?: "Not updated")
                    LabelValue("Cached records", status.cachedRecordCount.toString())
                    status.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (status.lastError != null && status.lastSuccessAt != null) Text("The last good cache remains in use.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (BuildConfig.DEBUG) Text("Debug: app ${BuildConfig.VERSION_NAME}; Room schema 2. Errors are sanitized and exclude portfolio values.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingHeader(value: String) = Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun SwitchRow(label: String, supporting: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.fillMaxWidth(.78f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(supporting, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun <T> ChoiceRow(label: String, values: List<T>, selected: T, onSelect: (T) -> Unit, text: (T) -> String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelect(value) }, label = { Text(text(value)) }) }
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set local app PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("4–8 digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit).take(8) }, label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { Button(onClick = { onSave(pin, confirmation) }, enabled = pin.length >= 4 && confirmation.length >= 4) { Text("Set PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ReportType.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
private fun timestamp(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()

private fun writeDocument(context: Context, uri: Uri, bytes: ByteArray) {
    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Could not open the selected destination")
}

private fun readDocument(context: Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected backup")
    input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 50 * 1024 * 1024) { "Backup exceeds 50 MB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
