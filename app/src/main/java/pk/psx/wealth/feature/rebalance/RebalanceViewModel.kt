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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.local.RebalancePlanEntity
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.RebalancePlanDraft
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.Holding
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.RebalanceEngine
import pk.psx.wealth.domain.RebalanceRequest
import pk.psx.wealth.domain.RebalanceResult
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import javax.inject.Inject

data class RebalanceUiState(
    val portfolioId: Long? = null,
    val snapshot: PortfolioSnapshot? = null,
    val targets: Map<String, BigDecimal> = emptyMap(),
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val plans: List<RebalancePlanEntity> = emptyList(),
    val result: RebalanceResult? = null,
    val additionalCash: BigDecimal = ZERO,
    val reserve: BigDecimal = ZERO,
    val minimumTrade: BigDecimal = ZERO,
    val allowSelling: Boolean = false,
    val savedPlanId: Long? = null,
    val error: String? = null,
)

@HiltViewModel
class RebalanceViewModel @Inject constructor(
    portfolios: PortfolioRepository,
    private val strategy: StrategyRepository,
    private val engine: RebalanceEngine,
    private val session: PortfolioSession,
) : ViewModel() {
    private val calculation = MutableStateFlow<RebalanceUiState?>(null)

    private val data: Flow<RebalanceUiState> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(RebalanceUiState()) else combine(
            portfolios.observeSnapshot(id), strategy.observeTargets(id), portfolios.observeQuotes(), strategy.observePlans(id),
        ) { snapshot, targets, quotes, plans ->
            RebalanceUiState(id, snapshot, targets, quotes.associateBy { it.symbol }, plans)
        }
    }

    val state: StateFlow<RebalanceUiState> = combine(data, calculation) { base, result ->
        if (result == null) base else base.copy(
            result = result.result,
            additionalCash = result.additionalCash,
            reserve = result.reserve,
            minimumTrade = result.minimumTrade,
            allowSelling = result.allowSelling,
            savedPlanId = result.savedPlanId,
            error = result.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RebalanceUiState())

    fun calculate(additional: String, reserve: String, minimum: String, allowSelling: Boolean) {
        val base = state.value
        calculation.value = runCatching {
            val snapshot = requireNotNull(base.snapshot) { "Create a portfolio first" }
            require(base.targets.isNotEmpty()) { "Configure target allocations first" }
            val holdings = extendHoldings(snapshot.holdings, base.targets.keys, base.quotes)
            val request = RebalanceRequest(
                holdings = holdings,
                additionalCash = additional.toBigDecimalOrNull() ?: ZERO,
                currentCash = snapshot.cashBalance,
                targetWeights = base.targets,
                minimumTrade = minimum.toBigDecimalOrNull() ?: ZERO,
                cashReserve = reserve.toBigDecimalOrNull() ?: ZERO,
            )
            val result = if (allowSelling) engine.full(request) else engine.cashOnly(request)
            RebalanceUiState(result = result, additionalCash = request.additionalCash, reserve = request.cashReserve,
                minimumTrade = request.minimumTrade, allowSelling = allowSelling)
        }.getOrElse { RebalanceUiState(error = it.message ?: "Could not calculate plan") }
    }

    fun savePlan() = viewModelScope.launch {
        val current = state.value
        runCatching {
            val id = requireNotNull(current.portfolioId)
            val result = requireNotNull(current.result)
            strategy.savePlan(RebalancePlanDraft(id, "SAVED_TARGET", current.additionalCash, current.reserve,
                current.allowSelling, current.minimumTrade, result))
        }.onSuccess { calculation.value = current.copy(savedPlanId = it, error = null) }
            .onFailure { calculation.value = current.copy(error = it.message) }
    }

    fun cancelPlan(id: Long) = viewModelScope.launch { strategy.cancelPlan(id) }

    private fun extendHoldings(
        holdings: List<Holding>,
        symbols: Set<String>,
        quotes: Map<String, MarketQuote>,
    ): List<Holding> = holdings + symbols.filter { symbol -> holdings.none { it.symbol == symbol } }.map { symbol ->
        Holding(symbol, ZERO, ZERO, ZERO, quotes[symbol]?.price, ZERO, ZERO, ZERO)
    }
}
