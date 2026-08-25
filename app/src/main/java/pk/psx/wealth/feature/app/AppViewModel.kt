package pk.psx.wealth.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.data.refresh.RefreshCoordinator
import pk.psx.wealth.data.refresh.MarketRefreshScheduler
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.feature.common.PortfolioSession
import javax.inject.Inject

data class AppUiState(
    val portfolios: List<PortfolioEntity> = emptyList(),
    val selectedPortfolioId: Long? = null,
    val refreshing: Boolean = false,
    val refreshMessage: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val settings: AppSettingsRepository,
    private val refreshCoordinator: RefreshCoordinator,
    private val refreshScheduler: MarketRefreshScheduler,
    private val session: PortfolioSession,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val refreshMessage = MutableStateFlow<String?>(null)
    private var settingsInitialized = false

    val state: StateFlow<AppUiState> = combine(
        portfolios.observePortfolios(), session.selectedPortfolioId, refreshing, refreshMessage,
    ) { available, selected, loading, message ->
        AppUiState(available, selected, loading, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            combine(portfolios.observePortfolios(), settings.settings) { available, preferences -> available to preferences }
                .collect { (available, preferences) ->
                    refreshScheduler.configureDaily(
                        preferences.dailyRefresh && preferences.remoteMarketDataEnabled,
                        preferences.wifiOnly,
                    )
                    val selected = session.selectedPortfolioId.value
                    if (selected == null || available.none { it.id == selected }) {
                        session.select(available.firstOrNull { it.id == preferences.defaultPortfolioId }?.id ?: available.firstOrNull()?.id)
                    }
                    if (!settingsInitialized) {
                        settingsInitialized = true
                        if (preferences.refreshOnOpen) refresh()
                    }
                }
        }
    }

    fun selectPortfolio(id: Long) {
        session.select(id)
        viewModelScope.launch { settings.update { it.copy(defaultPortfolioId = id) } }
    }

    fun createPortfolio(name: String, benchmark: String = "KMI30") = viewModelScope.launch {
        runCatching { portfolios.createPortfolio(name, benchmark) }
            .onSuccess { id ->
                session.select(id)
                settings.update { it.copy(defaultPortfolioId = id, defaultBenchmark = benchmark) }
            }
            .onFailure { refreshMessage.value = it.message }
    }

    fun archivePortfolio(id: Long) = viewModelScope.launch {
        runCatching { portfolios.archivePortfolio(id) }
            .onFailure { refreshMessage.value = it.message ?: "Could not archive portfolio" }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            refreshMessage.value = runCatching { refreshCoordinator.refreshAll().message }
                .getOrElse { it.message ?: "Refresh failed. Cached data is unchanged." }
            refreshing.value = false
        }
    }

    fun dismissMessage() { refreshMessage.value = null }
}
