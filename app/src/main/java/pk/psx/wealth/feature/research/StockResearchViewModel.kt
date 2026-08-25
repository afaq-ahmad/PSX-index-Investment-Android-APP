package pk.psx.wealth.feature.research

import androidx.lifecycle.SavedStateHandle
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
import pk.psx.wealth.data.local.FundamentalMetricEntity
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.data.repository.FundamentalDraft
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.ResearchCatalog
import pk.psx.wealth.data.repository.ResearchRepository
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.FundamentalScore
import pk.psx.wealth.domain.FundamentalScoreEngine
import pk.psx.wealth.domain.Holding
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class StockPeriod { ONE_MONTH, SIX_MONTHS, YTD, ONE_YEAR, THREE_YEARS, FIVE_YEARS }

data class StockPortfolioContext(
    val holding: Holding? = null,
    val portfolioWeight: BigDecimal? = null,
    val targetWeight: BigDecimal? = null,
)

data class StockResearchUiState(
    val symbol: String = "",
    val security: SecurityEntity? = null,
    val quote: LatestQuoteEntity? = null,
    val period: StockPeriod = StockPeriod.ONE_YEAR,
    val history: List<DailyPrice> = emptyList(),
    val fundamentals: List<FundamentalMetricEntity> = emptyList(),
    val latestMetrics: Map<String, FundamentalMetricEntity> = emptyMap(),
    val score: FundamentalScore = FundamentalScore(null, BigDecimal.ZERO, "Generic", emptyList(), emptyList()),
    val memberships: Set<String> = emptySet(),
    val portfolioContext: StockPortfolioContext = StockPortfolioContext(),
    val watchlists: List<WatchlistEntity> = emptyList(),
    val watchedIn: Set<Long> = emptySet(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class StockPortfolioData(val snapshot: PortfolioSnapshot?, val targets: Map<String, BigDecimal>)
private data class StockSources(val catalog: ResearchCatalog, val prices: List<DailyPrice>, val portfolio: StockPortfolioData)
private data class StockOperation(val refreshing: Boolean = false, val message: String? = null)

@HiltViewModel
class StockResearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    portfolios: PortfolioRepository,
    strategy: StrategyRepository,
    private val market: MarketRepository,
    private val research: ResearchRepository,
    private val scoreEngine: FundamentalScoreEngine,
    session: PortfolioSession,
    private val clock: Clock,
) : ViewModel() {
    private val symbol = requireNotNull(savedStateHandle.get<String>("symbol")).trim().uppercase()
    private val period = MutableStateFlow(StockPeriod.ONE_YEAR)
    private val operation = MutableStateFlow(StockOperation())
    private val portfolio: Flow<StockPortfolioData> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(StockPortfolioData(null, emptyMap())) else combine(
            portfolios.observeSnapshot(id), strategy.observeTargets(id),
        ) { snapshot, targets -> StockPortfolioData(snapshot, targets) }
    }
    private val sources = combine(research.observeCatalog(), research.observeDailyPrices(), portfolio) { catalog, prices, localPortfolio ->
        StockSources(catalog, prices, localPortfolio)
    }

    val state: StateFlow<StockResearchUiState> = combine(sources, period, operation) { source, selectedPeriod, op ->
        buildState(source, selectedPeriod, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockResearchUiState(symbol = symbol))

    fun selectPeriod(value: StockPeriod) { period.value = value }
    fun dismissMessage() { operation.value = operation.value.copy(message = null) }

    fun refresh() {
        if (operation.value.refreshing) return
        viewModelScope.launch {
            operation.value = StockOperation(refreshing = true)
            val message = runCatching {
                val snapshot = market.refreshStockSnapshot(symbol).getOrThrow()
                research.recordSnapshot(snapshot)
                val history = market.refreshHistory(symbol, LocalDate.now(clock).minusYears(5), LocalDate.now(clock))
                if (history.success) "Quote, company data and ${history.records} daily prices updated" else "Company data updated; ${history.message}"
            }.getOrElse { it.message ?: "Research refresh failed; cached data is unchanged" }
            operation.value = StockOperation(message = message)
        }
    }

    fun addToWatchlist(watchlistId: Long?, notes: String = "") = viewModelScope.launch {
        runCatching {
            val id = watchlistId ?: research.createWatchlist("Research")
            research.addToWatchlist(id, symbol, notes)
        }.onFailure { operation.value = StockOperation(message = it.message) }
    }

    fun removeFromWatchlist(watchlistId: Long) = viewModelScope.launch {
        runCatching { research.removeFromWatchlist(watchlistId, symbol) }
            .onFailure { operation.value = StockOperation(message = it.message) }
    }

    fun saveMetric(code: String, value: String, unit: String, periodEnd: String, periodType: String) = viewModelScope.launch {
        runCatching {
            research.saveFundamental(FundamentalDraft(symbol, LocalDate.parse(periodEnd), periodType, code,
                value.toBigDecimal(), unit, "Manual"))
        }.onSuccess { operation.value = StockOperation(message = "Fundamental observation saved locally") }
            .onFailure { operation.value = StockOperation(message = it.message ?: "Could not save metric") }
    }

    fun deleteMetric(id: Long) = viewModelScope.launch { research.deleteFundamental(id) }

    private fun buildState(source: StockSources, selectedPeriod: StockPeriod, op: StockOperation): StockResearchUiState {
        val security = source.catalog.securities.firstOrNull { it.symbol == symbol }
        val metrics = source.catalog.fundamentals.filter { it.symbol == symbol }
            .sortedWith(compareByDescending<FundamentalMetricEntity> { it.periodEnd }.thenByDescending { it.retrievedAt })
        val latest = metrics.distinctBy { it.metricCode }.associateBy { it.metricCode }
        val memberships = listOf("KMI30", "KSE100", "KMIALLSHR").filterTo(mutableSetOf()) { code ->
            source.catalog.indexHistory(code).firstOrNull()?.constituents.orEmpty().any { it.symbol == symbol }
        }
        val snapshot = source.portfolio.snapshot
        val holding = snapshot?.holdings?.firstOrNull { it.symbol == symbol }
        val start = selectedPeriod.startDate(LocalDate.now(clock))
        return StockResearchUiState(
            symbol = symbol,
            security = security,
            quote = source.catalog.quotes.firstOrNull { it.symbol == symbol },
            period = selectedPeriod,
            history = source.prices.filter { it.symbol == symbol && !it.date.isBefore(start) }.sortedBy(DailyPrice::date),
            fundamentals = metrics,
            latestMetrics = latest,
            score = scoreEngine.score(security?.sector, latest.mapValues { it.value.value.toBigDecimal() }),
            memberships = memberships,
            portfolioContext = StockPortfolioContext(holding, snapshot?.portfolioWeight(symbol), source.portfolio.targets[symbol]),
            watchlists = source.catalog.watchlists,
            watchedIn = source.catalog.watchlistItems.filter { it.symbol == symbol }.mapTo(mutableSetOf()) { it.watchlistId },
            refreshing = op.refreshing,
            message = op.message,
        )
    }
}

private fun StockPeriod.startDate(today: LocalDate): LocalDate = when (this) {
    StockPeriod.ONE_MONTH -> today.minusMonths(1)
    StockPeriod.SIX_MONTHS -> today.minusMonths(6)
    StockPeriod.YTD -> today.withDayOfYear(1)
    StockPeriod.ONE_YEAR -> today.minusYears(1)
    StockPeriod.THREE_YEARS -> today.minusYears(3)
    StockPeriod.FIVE_YEARS -> today.minusYears(5)
}
