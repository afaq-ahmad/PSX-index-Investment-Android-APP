package pk.psx.wealth.feature.portfolio

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
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.Holding
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

enum class HoldingSort { VALUE, PROFIT, WEIGHT, SYMBOL, DRIFT }
enum class HoldingFilter { ALL, PROFIT, LOSS, OVERWEIGHT, UNDERWEIGHT }

data class HoldingRow(
    val holding: Holding,
    val currentWeight: BigDecimal?,
    val targetWeight: BigDecimal?,
) {
    val drift: BigDecimal? = if (currentWeight != null && targetWeight != null) currentWeight - targetWeight else null
}

data class PortfolioUiState(
    val portfolio: PortfolioEntity? = null,
    val snapshot: PortfolioSnapshot? = null,
    val transactions: List<PortfolioTransaction> = emptyList(),
    val targets: Map<String, BigDecimal> = emptyMap(),
    val rows: List<HoldingRow> = emptyList(),
    val sort: HoldingSort = HoldingSort.VALUE,
    val filter: HoldingFilter = HoldingFilter.ALL,
    val message: String? = null,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val strategy: StrategyRepository,
    private val session: PortfolioSession,
) : ViewModel() {
    private val sort = MutableStateFlow(HoldingSort.VALUE)
    private val filter = MutableStateFlow(HoldingFilter.ALL)
    private val message = MutableStateFlow<String?>(null)

    private val selectedData: Flow<PortfolioUiState> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(PortfolioUiState()) else combine(
            repository.observePortfolios(), repository.observeSnapshot(id), repository.observeTransactions(id), strategy.observeTargets(id),
        ) { portfolios, snapshot, transactions, targets ->
            val rows = snapshot.holdings.map { holding ->
                HoldingRow(holding, snapshot.portfolioWeight(holding.symbol), targets[holding.symbol])
            }
            PortfolioUiState(portfolios.firstOrNull { it.id == id }, snapshot, transactions, targets, rows)
        }
    }

    val state: StateFlow<PortfolioUiState> = combine(selectedData, sort, filter, message) { base, selectedSort, selectedFilter, note ->
        val filtered = base.rows.filter { row ->
            when (selectedFilter) {
                HoldingFilter.ALL -> true
                HoldingFilter.PROFIT -> (row.holding.unrealizedProfit ?: ZERO).signum() > 0
                HoldingFilter.LOSS -> (row.holding.unrealizedProfit ?: ZERO).signum() < 0
                HoldingFilter.OVERWEIGHT -> row.drift?.signum() == 1
                HoldingFilter.UNDERWEIGHT -> row.drift?.signum() == -1
            }
        }
        val sorted = when (selectedSort) {
            HoldingSort.VALUE -> filtered.sortedByDescending { it.holding.marketValue }
            HoldingSort.PROFIT -> filtered.sortedByDescending { it.holding.unrealizedProfit }
            HoldingSort.WEIGHT -> filtered.sortedByDescending { it.currentWeight }
            HoldingSort.SYMBOL -> filtered.sortedBy { it.holding.symbol }
            HoldingSort.DRIFT -> filtered.sortedByDescending { it.drift?.abs() }
        }
        base.copy(rows = sorted, sort = selectedSort, filter = selectedFilter, message = note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    fun setSort(value: HoldingSort) { sort.value = value }
    fun setFilter(value: HoldingFilter) { filter.value = value }
    fun dismissMessage() { message.value = null }
    fun deleteTransaction(id: Long) = viewModelScope.launch {
        runCatching { repository.deleteTransaction(id) }
            .onFailure { message.value = it.message }
    }
    fun saveManualPrice(symbol: String, price: String, date: String, onSaved: () -> Unit) = viewModelScope.launch {
        runCatching { repository.saveManualQuote(symbol, price.toBigDecimal(), LocalDate.parse(date)) }
            .onSuccess { onSaved() }
            .onFailure { message.value = it.message }
    }
}
