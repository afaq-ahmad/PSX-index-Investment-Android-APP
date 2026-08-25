package pk.psx.wealth.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.psx.wealth.data.backup.BackupKind
import pk.psx.wealth.data.backup.BackupService
import pk.psx.wealth.data.backup.RestorePreview
import pk.psx.wealth.data.local.BackupDao
import pk.psx.wealth.data.local.DiagnosticsDao
import pk.psx.wealth.data.local.ProviderStatusEntity
import pk.psx.wealth.data.preferences.AppSettings
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.data.preferences.NumberFormatPreference
import pk.psx.wealth.data.preferences.RebalanceModePreference
import pk.psx.wealth.data.preferences.ThemePreference
import pk.psx.wealth.data.refresh.MarketRefreshScheduler
import pk.psx.wealth.data.report.ReportService
import pk.psx.wealth.data.report.ReportType
import pk.psx.wealth.feature.common.PortfolioSession
import pk.psx.wealth.domain.QuoteProviderPreference
import javax.inject.Inject

data class DataCounts(
    val portfolios: Int = 0,
    val transactions: Int = 0,
    val securities: Int = 0,
    val quotes: Int = 0,
    val prices: Int = 0,
    val indexSnapshots: Int = 0,
    val fundamentals: Int = 0,
)

data class ExportPayload(val id: Long, val fileName: String, val mimeType: String, val bytes: ByteArray)

data class MoreUiState(
    val settings: AppSettings = AppSettings(),
    val diagnostics: List<ProviderStatusEntity> = emptyList(),
    val counts: DataCounts = DataCounts(),
    val export: ExportPayload? = null,
    val restorePreview: RestorePreview? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

private data class MoreOperation(
    val export: ExportPayload? = null,
    val restorePreview: RestorePreview? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    diagnosticsDao: DiagnosticsDao,
    private val backupDao: BackupDao,
    private val reports: ReportService,
    private val backups: BackupService,
    private val scheduler: MarketRefreshScheduler,
    private val session: PortfolioSession,
) : ViewModel() {
    private val counts = MutableStateFlow(DataCounts())
    private val operation = MutableStateFlow(MoreOperation())

    val state: StateFlow<MoreUiState> = combine(
        settingsRepository.settings, diagnosticsDao.observeProviderStatus(), counts, operation,
    ) { settings, diagnostics, localCounts, op ->
        MoreUiState(settings, diagnostics, localCounts, op.export, op.restorePreview, op.busy, op.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    private var pendingRestore: ByteArray? = null

    init { refreshCounts() }

    fun prepareReport(type: ReportType) = viewModelScope.launch {
        runBusy {
            val portfolioId = requireNotNull(session.selectedPortfolioId.value) { "Create or select a portfolio first" }
            val report = withContext(Dispatchers.IO) { reports.create(type, portfolioId) }
            operation.value = MoreOperation(export = ExportPayload(System.nanoTime(), report.fileName, "text/csv", report.bytes))
        }
    }

    fun prepareBackup(kind: BackupKind) = viewModelScope.launch {
        runBusy {
            val backup = withContext(Dispatchers.IO) { backups.create(kind) }
            operation.value = MoreOperation(export = ExportPayload(System.nanoTime(), backup.fileName, "application/zip", backup.bytes))
        }
    }

    fun exportHandled(message: String) { operation.value = MoreOperation(message = message) }

    fun previewRestore(bytes: ByteArray) = viewModelScope.launch {
        runBusy {
            val preview = withContext(Dispatchers.IO) { backups.preview(bytes) }
            pendingRestore = bytes.copyOf()
            operation.value = MoreOperation(restorePreview = preview)
        }
    }

    fun cancelRestore() { pendingRestore = null; operation.value = MoreOperation() }

    fun confirmRestore() = viewModelScope.launch {
        runBusy {
            val bytes = requireNotNull(pendingRestore) { "Choose a backup again" }
            withContext(Dispatchers.IO) { backups.restore(bytes) }
            pendingRestore = null
            loadCounts()
            operation.value = MoreOperation(message = "Backup restored. Cached data may be refreshed when you choose.")
        }
    }

    fun setTheme(value: ThemePreference) = update { it.copy(theme = value) }
    fun setNumberFormat(value: NumberFormatPreference) = update { it.copy(numberFormat = value) }
    fun setBenchmark(value: String) = update { it.copy(defaultBenchmark = value) }
    fun setCashReserve(value: String) = update { it.copy(defaultCashReserve = value.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0) }
    fun setMinimumTrade(value: String) = update { it.copy(defaultMinimumTrade = value.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0) }
    fun setRebalanceMode(value: RebalanceModePreference) = update { it.copy(defaultRebalanceMode = value) }
    fun setRefreshOnOpen(value: Boolean) = update { it.copy(refreshOnOpen = value) }
    fun setDailyRefresh(value: Boolean) = update {
        scheduler.configureDaily(value && it.remoteMarketDataEnabled, it.wifiOnly)
        it.copy(dailyRefresh = value)
    }
    fun setWifiOnly(value: Boolean) = update {
        scheduler.configureDaily(it.dailyRefresh && it.remoteMarketDataEnabled, value)
        it.copy(wifiOnly = value)
    }
    fun setRemoteMarketData(value: Boolean) = update {
        scheduler.configureDaily(value && it.dailyRefresh, it.wifiOnly)
        it.copy(remoteMarketDataEnabled = value)
    }
    fun setPsxProvider(value: Boolean) = update { it.copy(psxProviderEnabled = value) }
    fun setScsFallback(value: Boolean) = update { it.copy(scsQuoteFallbackEnabled = value) }
    fun setQuoteProviderPreference(value: QuoteProviderPreference) = update { it.copy(quoteProviderPreference = value) }
    fun setRefreshPortfolioQuotes(value: Boolean) = update { it.copy(refreshPortfolioQuotes = value) }
    fun setRefreshWatchlistQuotes(value: Boolean) = update { it.copy(refreshWatchlistQuotes = value) }
    fun setRefreshKmi30(value: Boolean) = update { it.copy(refreshKmi30 = value) }
    fun setRefreshKse100(value: Boolean) = update { it.copy(refreshKse100 = value) }
    fun setRefreshKmiAllShare(value: Boolean) = update { it.copy(refreshKmiAllShare = value) }
    fun dismissMessage() { operation.value = operation.value.copy(message = null) }
    fun refreshCounts() = viewModelScope.launch { loadCounts() }

    private fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        runCatching { settingsRepository.update(transform) }
            .onFailure { operation.value = MoreOperation(message = it.message ?: "Could not save setting") }
    }

    private suspend fun loadCounts() {
        counts.value = DataCounts(
            portfolios = backupDao.portfolioCount(),
            transactions = backupDao.transactionCount(),
            securities = backupDao.securityCount(),
            quotes = backupDao.quoteCount(),
            prices = backupDao.priceCount(),
            indexSnapshots = backupDao.indexSnapshotCount(),
            fundamentals = backupDao.fundamentalCount(),
        )
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        operation.value = MoreOperation(busy = true)
        runCatching { block() }.onFailure { operation.value = MoreOperation(message = it.message ?: "Operation failed") }
    }
}
