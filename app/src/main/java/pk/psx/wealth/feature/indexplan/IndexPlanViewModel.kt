package pk.psx.wealth.feature.indexplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.StoredIndexSnapshot
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.IndexAllocationInput
import pk.psx.wealth.domain.IndexInvestmentEngine
import pk.psx.wealth.domain.IndexInvestmentPlan
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.TargetAllocationEngine
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import javax.inject.Inject

private val supportedIndexes = setOf("KMI30", "KSE100", "KMIALLSHR")

private data class IndexPortfolioData(
    val portfolio: PortfolioEntity? = null,
    val snapshot: PortfolioSnapshot? = null,
    val quotes: List<MarketQuote> = emptyList(),
)

private data class IndexPlanOperation(
    val refreshing: Boolean = false,
    val savingTargets: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class IndexPlanUiState(
    val portfolio: PortfolioEntity? = null,
    val snapshot: PortfolioSnapshot? = null,
    val indexCode: String = "KMI30",
    val indexRows: List<IndexConstituentEntity> = emptyList(),
    val snapshotDate: String? = null,
    val source: String? = null,
    val additionalFunds: String = "0",
    val calculatedCurrentValue: BigDecimal = ZERO,
    val valuationComplete: Boolean = true,
    val plan: IndexInvestmentPlan? = null,
    val refreshing: Boolean = false,
    val savingTargets: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class IndexPlanViewModel @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val strategy: StrategyRepository,
    private val market: MarketRepository,
    private val planner: IndexInvestmentEngine,
    private val targets: TargetAllocationEngine,
    private val session: PortfolioSession,
) : ViewModel() {
    private val selectedIndex = MutableStateFlow("KMI30")
    private val additionalFunds = MutableStateFlow("0")
    private val operation = MutableStateFlow(IndexPlanOperation())
    private var initializedPortfolioId: Long? = null

    private val portfolioData: Flow<IndexPortfolioData> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(IndexPortfolioData()) else combine(
            portfolios.observePortfolios(), portfolios.observeSnapshot(id), portfolios.observeQuotes(),
        ) { available, snapshot, quotes ->
            IndexPortfolioData(available.firstOrNull { it.id == id }, snapshot, quotes)
        }
    }

    private val indexData: Flow<StoredIndexSnapshot?> = selectedIndex.flatMapLatest(market::observeIndex)

    val state: StateFlow<IndexPlanUiState> = combine(
        portfolioData, selectedIndex, indexData, additionalFunds, operation,
    ) { portfolio, indexCode, index, fundsText, op ->
        buildState(portfolio, indexCode, index, fundsText, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexPlanUiState())

    init {
        viewModelScope.launch {
            session.selectedPortfolioId.flatMapLatest { id ->
                if (id == null) flowOf(null) else portfolios.observePortfolios().map { rows -> rows.firstOrNull { it.id == id } }
            }.collect { portfolio ->
                if (portfolio != null && initializedPortfolioId != portfolio.id) {
                    initializedPortfolioId = portfolio.id
                    selectedIndex.value = portfolio.benchmark.takeIf { it in supportedIndexes } ?: "KMI30"
                    additionalFunds.value = "0"
                    operation.value = IndexPlanOperation()
                }
            }
        }
    }

    fun selectIndex(value: String) {
        val normalized = value.trim().uppercase()
        if (normalized !in supportedIndexes) return
        selectedIndex.value = normalized
        operation.value = IndexPlanOperation()
    }

    fun setAdditionalFunds(value: String) {
        additionalFunds.value = value
        operation.value = operation.value.copy(error = null, message = null)
    }

    fun refreshIndex() {
        if (operation.value.refreshing) return
        viewModelScope.launch {
            operation.value = IndexPlanOperation(refreshing = true)
            val result = runCatching { market.refreshIndex(selectedIndex.value) }
            operation.value = result.fold(
                onSuccess = { response ->
                    if (response.success) IndexPlanOperation(message = response.message)
                    else IndexPlanOperation(error = response.message)
                },
                onFailure = { IndexPlanOperation(error = it.message ?: "Could not refresh index constituents") },
            )
        }
    }

    fun saveIndexTargets() {
        if (operation.value.savingTargets) return
        viewModelScope.launch {
            operation.value = IndexPlanOperation(savingTargets = true)
            val result = runCatching {
                val current = state.value
                val portfolio = requireNotNull(current.portfolio) { "Create or select a portfolio first" }
                require(current.indexRows.isNotEmpty()) { "Load index constituents first" }
                val weights = targets.index(current.indexRows.associate { row ->
                    row.symbol to (row.weightPercent?.let { value -> BigDecimal.valueOf(value) } ?: ZERO)
                })
                strategy.saveTargets(portfolio.id, weights)
                portfolios.updatePortfolio(portfolio.copy(benchmark = current.indexCode))
                weights.size
            }
            operation.value = result.fold(
                onSuccess = { count -> IndexPlanOperation(message = "Saved $count ${selectedIndex.value} weights as portfolio targets.") },
                onFailure = { IndexPlanOperation(error = it.message ?: "Could not save index targets") },
            )
        }
    }

    fun dismissMessage() { operation.value = operation.value.copy(message = null, error = null) }

    private fun buildState(
        portfolio: IndexPortfolioData,
        indexCode: String,
        index: StoredIndexSnapshot?,
        fundsText: String,
        op: IndexPlanOperation,
    ): IndexPlanUiState {
        val snapshot = portfolio.snapshot
        val indexRows = index?.constituents.orEmpty()
        val quotePrices = portfolio.quotes.associate { it.symbol to it.price }
        val indexPrices = indexRows.associate { row ->
            row.symbol to row.price?.let { value -> BigDecimal.valueOf(value) }
        }
        val holdings = snapshot?.holdings.orEmpty()
        val resolvedHoldingValues = holdings.map { holding ->
            val price = holding.marketPrice ?: quotePrices[holding.symbol] ?: indexPrices[holding.symbol]
            holding to price
        }
        val valuationComplete = resolvedHoldingValues.all { it.second != null }
        val currentValue = (snapshot?.cashBalance ?: ZERO).add(
            resolvedHoldingValues.mapNotNull { (holding, price) -> price?.multiply(holding.quantity) }
                .fold(ZERO, BigDecimal::add),
        )

        val parsedFunds = fundsText.trim().ifBlank { "0" }.toBigDecimalOrNull()
        val inputError = when {
            parsedFunds == null -> "Enter a valid new-funds amount"
            parsedFunds.signum() < 0 -> "New funds cannot be negative"
            else -> null
        }
        val indexSymbols = indexRows.map(IndexConstituentEntity::symbol).toSet()
        val indexInputs = indexRows.map { row ->
            IndexAllocationInput(
                symbol = row.symbol,
                companyName = row.companyName,
                defaultWeightPercent = row.weightPercent?.let { value -> BigDecimal.valueOf(value) } ?: ZERO,
                price = quotePrices[row.symbol] ?: row.price?.let { value -> BigDecimal.valueOf(value) },
                ownedShares = holdings.firstOrNull { it.symbol == row.symbol }?.quantity ?: ZERO,
            )
        }
        val outsideInputs = holdings.filter { it.symbol !in indexSymbols && it.quantity.signum() > 0 }.map { holding ->
            IndexAllocationInput(
                symbol = holding.symbol,
                companyName = holding.symbol,
                defaultWeightPercent = ZERO,
                price = holding.marketPrice ?: quotePrices[holding.symbol],
                ownedShares = holding.quantity,
                isIndexConstituent = false,
            )
        }
        val planResult = if (inputError == null && indexInputs.isNotEmpty() && currentValue.add(parsedFunds!!).signum() > 0) {
            runCatching { planner.plan(currentValue, parsedFunds, indexInputs + outsideInputs) }
        } else null
        val plan = planResult?.getOrNull()
        val planError = planResult?.exceptionOrNull()?.message

        return IndexPlanUiState(
            portfolio = portfolio.portfolio,
            snapshot = snapshot,
            indexCode = indexCode,
            indexRows = indexRows,
            snapshotDate = index?.header?.snapshotDate,
            source = index?.header?.source,
            additionalFunds = fundsText,
            calculatedCurrentValue = currentValue,
            valuationComplete = valuationComplete,
            plan = plan,
            refreshing = op.refreshing,
            savingTargets = op.savingTargets,
            message = op.message,
            error = op.error ?: inputError ?: planError,
        )
    }
}
