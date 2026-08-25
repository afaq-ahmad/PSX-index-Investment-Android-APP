package pk.psx.wealth.feature.rebalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.TargetAllocationEngine
import pk.psx.wealth.domain.TargetMode
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import javax.inject.Inject

data class TargetUiState(
    val portfolioId: Long? = null,
    val benchmark: String = "KMI30",
    val holdingSymbols: Set<String> = emptySet(),
    val indexRows: List<IndexConstituentEntity> = emptyList(),
    val currentTargets: Map<String, BigDecimal> = emptyMap(),
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TargetViewModel @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val strategy: StrategyRepository,
    private val market: MarketRepository,
    private val engine: TargetAllocationEngine,
    private val session: PortfolioSession,
) : ViewModel() {
    private val result = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    private val data: Flow<TargetUiState> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(TargetUiState()) else combine(
            portfolios.observePortfolios(), portfolios.observeSnapshot(id), strategy.observeTargets(id),
        ) { available, snapshot, targets -> Triple(available.firstOrNull { it.id == id }, snapshot, targets) }
            .flatMapLatest { (portfolio, snapshot, targets) ->
                if (portfolio == null) flowOf(TargetUiState()) else market.observeIndex(portfolio.benchmark).map { index ->
                    TargetUiState(id, portfolio.benchmark, snapshot.holdings.map { it.symbol }.toSet(),
                        index?.constituents.orEmpty(), targets)
                }
            }
    }

    val state: StateFlow<TargetUiState> = combine(data, result) { base, outcome ->
        base.copy(saved = outcome.first, error = outcome.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetUiState())

    fun save(
        mode: TargetMode,
        selectedSymbols: Set<String>,
        customPercentages: Map<String, String>,
        cashTargetPercent: String,
    ) = viewModelScope.launch {
        runCatching {
            val current = state.value
            val id = requireNotNull(current.portfolioId)
            val cash = cashTargetPercent.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val weights = when (mode) {
                TargetMode.CUSTOM -> engine.custom(
                    customPercentages.filterValues(String::isNotBlank).mapValues { it.value.toBigDecimal() },
                    cash,
                )
                TargetMode.EQUAL_WEIGHT -> engine.equal(selectedSymbols, cash)
                TargetMode.INDEX_WEIGHT -> engine.index(current.indexRows.associate { it.symbol to (it.weightPercent?.toBigDecimal() ?: BigDecimal.ZERO) }, cash)
                TargetMode.SELECTED_INDEX -> engine.index(current.indexRows.associate { it.symbol to (it.weightPercent?.toBigDecimal() ?: BigDecimal.ZERO) }, cash, selectedSymbols)
            }
            strategy.saveTargets(id, weights)
        }.onSuccess { result.value = true to null }
            .onFailure { result.value = false to (it.message ?: "Could not save targets") }
    }
}
