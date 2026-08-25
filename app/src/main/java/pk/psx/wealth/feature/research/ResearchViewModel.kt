package pk.psx.wealth.feature.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.local.FundamentalMetricEntity
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.data.local.WatchlistItemEntity
import pk.psx.wealth.data.repository.IndexResearchSnapshot
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.ResearchCatalog
import pk.psx.wealth.data.repository.ResearchRepository
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.BenchmarkPoint
import pk.psx.wealth.domain.DividendEngine
import pk.psx.wealth.domain.DividendSummary
import pk.psx.wealth.domain.FundamentalScore
import pk.psx.wealth.domain.FundamentalScoreEngine
import pk.psx.wealth.domain.IndexLevel
import pk.psx.wealth.domain.PerformanceEngine
import pk.psx.wealth.domain.PerformanceSummary
import pk.psx.wealth.domain.PeriodReturnSummary
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.ProfitLossPoint
import pk.psx.wealth.domain.TransactionType
import pk.psx.wealth.domain.WealthPoint
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.feature.common.PortfolioSession
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class ResearchSection { INDICES, PERFORMANCE, DIVIDENDS, SCREENER, WATCHLISTS }
enum class Membership { BOTH, OWNED_ONLY, INDEX_ONLY }

private val benchmarkIndexes = listOf("KMI30", "KSE100", "KMIALLSHR")

data class IndexComparisonRow(
    val symbol: String,
    val companyName: String,
    val indexWeight: BigDecimal,
    val portfolioWeight: BigDecimal,
    val difference: BigDecimal,
    val price: BigDecimal?,
    val membership: Membership,
)

data class SectorComparisonRow(
    val sector: String,
    val portfolioWeight: BigDecimal,
    val indexWeight: BigDecimal,
)

data class ScreenerRow(
    val security: SecurityEntity,
    val quote: LatestQuoteEntity?,
    val metrics: Map<String, FundamentalMetricEntity>,
    val score: FundamentalScore,
    val indexMemberships: Set<String>,
    val oneYearReturn: BigDecimal?,
) {
    fun metric(code: String): BigDecimal? = metrics[code]?.value?.toBigDecimal()
}

data class PortfolioResearchData(
    val snapshot: PortfolioSnapshot? = null,
    val transactions: List<PortfolioTransaction> = emptyList(),
    val targets: Map<String, BigDecimal> = emptyMap(),
)

data class BenchmarkResearchSummary(
    val indexCode: String,
    val terminalDate: LocalDate,
    val terminalValue: BigDecimal,
    val totalProfit: BigDecimal,
    val xirr: BigDecimal?,
    val portfolioValueDifference: BigDecimal?,
)

data class AllocationBreakdownRow(
    val label: String,
    val value: BigDecimal,
    val weight: BigDecimal,
    val profit: BigDecimal? = null,
)

data class HoldingGainRow(
    val symbol: String,
    val profit: BigDecimal,
    val returnFraction: BigDecimal?,
)

data class ResearchUiState(
    val section: ResearchSection = ResearchSection.INDICES,
    val indexCode: String = "KMI30",
    val portfolio: PortfolioResearchData = PortfolioResearchData(),
    val catalog: ResearchCatalog = ResearchCatalog(),
    val latestIndex: IndexResearchSnapshot? = null,
    val comparison: List<IndexComparisonRow> = emptyList(),
    val sectors: List<SectorComparisonRow> = emptyList(),
    val addedSymbols: Set<String> = emptySet(),
    val removedSymbols: Set<String> = emptySet(),
    val performance: PerformanceSummary? = null,
    val wealthHistory: List<WealthPoint> = emptyList(),
    val profitLossHistory: List<ProfitLossPoint> = emptyList(),
    val periodicReturns: List<PeriodReturnSummary> = emptyList(),
    val benchmark: BenchmarkResearchSummary? = null,
    val benchmarkHistory: List<BenchmarkPoint> = emptyList(),
    val benchmarkSummaries: List<BenchmarkResearchSummary> = emptyList(),
    val benchmarkHistories: Map<String, List<BenchmarkPoint>> = emptyMap(),
    val benchmarkRefreshing: Boolean = false,
    val benchmarkMessage: String = "Benchmark history is unavailable until local index-level history has been cached.",
    val portfolioAllocations: List<AllocationBreakdownRow> = emptyList(),
    val sectorAllocations: List<AllocationBreakdownRow> = emptyList(),
    val holdingGains: List<HoldingGainRow> = emptyList(),
    val dividends: DividendSummary? = null,
    val screenerRows: List<ScreenerRow> = emptyList(),
    val error: String? = null,
)

private data class ResearchSources(
    val portfolio: PortfolioResearchData,
    val catalog: ResearchCatalog,
    val prices: List<DailyPrice>,
)

private data class ResearchChoices(
    val section: ResearchSection,
    val indexCode: String,
    val error: String?,
    val benchmarkOperation: BenchmarkOperation,
)

private data class BenchmarkOperation(
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class BenchmarkBundle(
    val levels: List<IndexLevel>,
    val summary: BenchmarkResearchSummary?,
    val history: List<BenchmarkPoint>,
)

@HiltViewModel
class ResearchViewModel @Inject constructor(
    portfolios: PortfolioRepository,
    strategy: StrategyRepository,
    private val research: ResearchRepository,
    private val performanceEngine: PerformanceEngine,
    private val dividendEngine: DividendEngine,
    private val scoreEngine: FundamentalScoreEngine,
    private val market: MarketRepository,
    session: PortfolioSession,
    private val clock: Clock,
) : ViewModel() {
    private val section = MutableStateFlow(ResearchSection.INDICES)
    private val indexCode = MutableStateFlow("KMI30")
    private val error = MutableStateFlow<String?>(null)
    private val benchmarkOperation = MutableStateFlow(BenchmarkOperation())

    private val portfolioData: Flow<PortfolioResearchData> = session.selectedPortfolioId.flatMapLatest { id ->
        if (id == null) flowOf(PortfolioResearchData()) else combine(
            portfolios.observeSnapshot(id), portfolios.observeTransactions(id), strategy.observeTargets(id),
        ) { snapshot, transactions, targets -> PortfolioResearchData(snapshot, transactions, targets) }
    }
    private val sources = combine(portfolioData, research.observeCatalog(), research.observeDailyPrices()) { portfolio, catalog, prices ->
        ResearchSources(portfolio, catalog, prices)
    }
    private val choices = combine(section, indexCode, error, benchmarkOperation) { selectedSection, selectedIndex, message, operation ->
        ResearchChoices(selectedSection, selectedIndex, message, operation)
    }

    val state: StateFlow<ResearchUiState> = combine(sources, choices) { source, choice ->
        buildState(source, choice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResearchUiState())

    fun selectSection(value: ResearchSection) { section.value = value }
    fun selectIndex(value: String) {
        indexCode.value = value
        benchmarkOperation.value = BenchmarkOperation()
    }
    fun dismissError() { error.value = null }

    fun createWatchlist(name: String) = operation { research.createWatchlist(name) }
    fun deleteWatchlist(id: Long) = operation { research.deleteWatchlist(id) }
    fun addToWatchlist(id: Long, symbol: String, notes: String) = operation { research.addToWatchlist(id, symbol, notes) }
    fun removeFromWatchlist(id: Long, symbol: String) = operation { research.removeFromWatchlist(id, symbol) }

    fun refreshBenchmarkHistory() = refreshBenchmarkHistories(listOf(indexCode.value))

    fun refreshAllBenchmarkHistories() = refreshBenchmarkHistories(benchmarkIndexes)

    private fun refreshBenchmarkHistories(indexes: List<String>) {
        if (benchmarkOperation.value.refreshing) return
        viewModelScope.launch {
            benchmarkOperation.value = BenchmarkOperation(refreshing = true)
            val result = runCatching {
                val portfolio = portfolioData.first()
                val asOf = LocalDate.now(clock)
                val firstExternalFlow = portfolio.transactions.asSequence()
                    .filter { it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.CASH_WITHDRAWAL }
                    .map(PortfolioTransaction::tradeDate)
                    .minOrNull()
                val from = (firstExternalFlow ?: asOf.minusYears(5)).minusDays(10)
                indexes.distinct().map { code -> code to market.refreshHistory(code, from, asOf) }
            }
            benchmarkOperation.value = BenchmarkOperation(
                message = result.fold(
                    onSuccess = { rows ->
                        val updated = rows.count { it.second.success }
                        val failed = rows.size - updated
                        if (failed == 0) "Updated ${rows.joinToString { it.first }} benchmark history."
                        else "$updated benchmark histories updated, $failed failed. Existing cached history remains available."
                    },
                    onFailure = { it.message ?: "Benchmark refresh failed. Cached history is unchanged." },
                ),
            )
        }
    }

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { error.value = it.message ?: "Could not update local research data" }
    }

    private fun buildState(source: ResearchSources, choice: ResearchChoices): ResearchUiState {
        val asOf = LocalDate.now(clock)
        val history = source.catalog.indexHistory(choice.indexCode)
        val latest = history.firstOrNull()
        val previous = history.drop(1).firstOrNull()
        val comparison = indexComparison(source.portfolio.snapshot, latest)
        val wealth = runCatching {
            performanceEngine.wealthHistory(source.portfolio.transactions, source.prices, asOf).let { points ->
                val snapshot = source.portfolio.snapshot
                if (snapshot != null && snapshot.hasCompletePrices && points.lastOrNull()?.date != asOf) {
                    points + WealthPoint(asOf, snapshot.totalPortfolioValue, snapshot.netContributions)
                } else points
            }
        }.getOrDefault(emptyList())
        val performance = source.portfolio.snapshot?.let {
            performanceEngine.summary(it, source.portfolio.transactions, asOf).copy(
                timeWeightedReturn = performanceEngine.timeWeightedReturnFromHistory(wealth),
            )
        }
        val profitLoss = performanceEngine.profitLossHistory(wealth)
        val periodicReturns = performanceEngine.periodicReturns(wealth, source.portfolio.transactions, asOf)
        val benchmarkBundles = benchmarkIndexes.associateWith { code ->
            benchmarkBundle(code, source, asOf)
        }
        val selectedBundle = benchmarkBundles.getValue(choice.indexCode)
        val indexLevels = selectedBundle.levels
        val benchmark = selectedBundle.summary
        val benchmarkHistory = selectedBundle.history
        val benchmarkMessage = choice.benchmarkOperation.message ?: when {
            source.portfolio.transactions.none { it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.CASH_WITHDRAWAL } ->
                "Record at least one deposit before comparing performance."
            indexLevels.isEmpty() ->
                "No cached ${choice.indexCode} level history. Download it explicitly to enable a fair comparison."
            benchmark == null ->
                "Cached ${choice.indexCode} history does not reach the first contribution date; refresh a longer period."
            else ->
                "Each deposit and withdrawal is simulated at the most recent available ${choice.indexCode} close."
        }
        return ResearchUiState(
            section = choice.section,
            indexCode = choice.indexCode,
            portfolio = source.portfolio,
            catalog = source.catalog,
            latestIndex = latest,
            comparison = comparison,
            sectors = sectorComparison(source.portfolio.snapshot, latest, source.catalog.securities),
            addedSymbols = if (previous == null) emptySet() else
                latest?.constituents.orEmpty().map { it.symbol }.toSet() - previous.constituents.map { it.symbol }.toSet(),
            removedSymbols = if (previous == null) emptySet() else
                previous.constituents.map { it.symbol }.toSet() - latest?.constituents.orEmpty().map { it.symbol }.toSet(),
            performance = performance,
            wealthHistory = wealth,
            profitLossHistory = profitLoss,
            periodicReturns = periodicReturns,
            benchmark = benchmark,
            benchmarkHistory = benchmarkHistory,
            benchmarkSummaries = benchmarkBundles.values.mapNotNull { it.summary },
            benchmarkHistories = benchmarkBundles.mapValues { it.value.history }.filterValues { it.isNotEmpty() },
            benchmarkRefreshing = choice.benchmarkOperation.refreshing,
            benchmarkMessage = benchmarkMessage,
            portfolioAllocations = portfolioAllocations(source.portfolio.snapshot),
            sectorAllocations = sectorAllocations(source.portfolio.snapshot, source.catalog.securities),
            holdingGains = holdingGains(source.portfolio.snapshot),
            dividends = source.portfolio.snapshot?.let { dividendEngine.summarize(source.portfolio.transactions, it, asOf) },
            screenerRows = screener(source.catalog, source.prices, asOf),
            error = choice.error,
        )
    }

    private fun benchmarkBundle(indexCode: String, source: ResearchSources, asOf: LocalDate): BenchmarkBundle {
        val levels = source.prices.asSequence()
            .filter { it.symbol == indexCode && !it.date.isAfter(asOf) }
            .map { IndexLevel(it.date, it.close) }
            .toList()
        val simulation = performanceEngine.simulateBenchmark(source.portfolio.transactions, levels, asOf)
        val history = performanceEngine.benchmarkHistory(source.portfolio.transactions, levels, asOf).orEmpty()
        val terminalDate = levels.maxByOrNull(IndexLevel::date)?.date
        val summary = if (simulation == null || terminalDate == null) null else BenchmarkResearchSummary(
            indexCode = indexCode,
            terminalDate = terminalDate,
            terminalValue = simulation.terminalValue,
            totalProfit = simulation.terminalValue.subtract(source.portfolio.snapshot?.netContributions ?: ZERO),
            xirr = performanceEngine.xirr(source.portfolio.transactions, simulation.terminalValue, asOf),
            portfolioValueDifference = source.portfolio.snapshot?.takeIf { it.hasCompletePrices }
                ?.totalPortfolioValue?.subtract(simulation.terminalValue),
        )
        return BenchmarkBundle(levels, summary, history)
    }

    private fun portfolioAllocations(snapshot: PortfolioSnapshot?): List<AllocationBreakdownRow> {
        if (snapshot == null) return emptyList()
        val holdings = snapshot.holdings.mapNotNull { holding ->
            holding.marketValue?.takeIf { it.signum() > 0 }?.let { value -> holding to value }
        }
        val values = holdings.map { it.second }.toMutableList()
        if (snapshot.cashBalance.signum() > 0) values += snapshot.cashBalance
        val total = values.fold(ZERO, BigDecimal::add)
        if (total.signum() <= 0) return emptyList()
        return buildList {
            holdings.forEach { (holding, value) ->
                add(AllocationBreakdownRow(
                    label = holding.symbol,
                    value = value,
                    weight = value.divide(total, pk.psx.wealth.domain.MONEY_CONTEXT),
                    profit = holding.totalProfit,
                ))
            }
            if (snapshot.cashBalance.signum() > 0) {
                add(AllocationBreakdownRow(
                    label = "Cash",
                    value = snapshot.cashBalance,
                    weight = snapshot.cashBalance.divide(total, pk.psx.wealth.domain.MONEY_CONTEXT),
                ))
            }
        }.sortedByDescending(AllocationBreakdownRow::value)
    }

    private fun sectorAllocations(
        snapshot: PortfolioSnapshot?,
        securities: List<SecurityEntity>,
    ): List<AllocationBreakdownRow> {
        if (snapshot == null || snapshot.stockMarketValue.signum() <= 0) return emptyList()
        val sectorBySymbol = securities.associate { it.symbol to (it.sector?.takeIf(String::isNotBlank) ?: "Unknown") }
        return snapshot.holdings.mapNotNull { holding ->
            holding.marketValue?.takeIf { it.signum() > 0 }?.let { value ->
                Triple(sectorBySymbol[holding.symbol] ?: "Unknown", value, holding.totalProfit ?: ZERO)
            }
        }.groupBy { it.first }.map { (sector, rows) ->
            val value = rows.map { it.second }.fold(ZERO, BigDecimal::add)
            AllocationBreakdownRow(
                label = sector,
                value = value,
                weight = value.divide(snapshot.stockMarketValue, pk.psx.wealth.domain.MONEY_CONTEXT),
                profit = rows.map { it.third }.fold(ZERO, BigDecimal::add),
            )
        }.sortedByDescending(AllocationBreakdownRow::value)
    }

    private fun holdingGains(snapshot: PortfolioSnapshot?): List<HoldingGainRow> = snapshot?.holdings.orEmpty()
        .mapNotNull { holding ->
            holding.totalProfit?.let { profit ->
                HoldingGainRow(
                    symbol = holding.symbol,
                    profit = profit,
                    returnFraction = holding.remainingCost.takeIf { it.signum() > 0 }
                        ?.let { profit.divide(it, pk.psx.wealth.domain.MONEY_CONTEXT) },
                )
            }
        }
        .sortedByDescending { it.profit }

    private fun indexComparison(snapshot: PortfolioSnapshot?, index: IndexResearchSnapshot?): List<IndexComparisonRow> {
        val indexRows = index?.constituents.orEmpty().associateBy { it.symbol }
        val held = snapshot?.holdings.orEmpty().associateBy { it.symbol }
        return (indexRows.keys + held.keys).sorted().map { symbol ->
            val row = indexRows[symbol]
            val indexWeight = row?.weightPercent?.toBigDecimal()?.movePointLeft(2) ?: ZERO
            val portfolioWeight = snapshot?.portfolioWeight(symbol) ?: ZERO
            IndexComparisonRow(
                symbol,
                row?.companyName ?: symbol,
                indexWeight,
                portfolioWeight,
                portfolioWeight - indexWeight,
                row?.price?.toBigDecimal() ?: held[symbol]?.marketPrice,
                when {
                    row != null && symbol in held -> Membership.BOTH
                    row != null -> Membership.INDEX_ONLY
                    else -> Membership.OWNED_ONLY
                },
            )
        }.sortedByDescending { it.difference.abs() }
    }

    private fun sectorComparison(
        snapshot: PortfolioSnapshot?,
        index: IndexResearchSnapshot?,
        securities: List<SecurityEntity>,
    ): List<SectorComparisonRow> {
        val sectors = securities.associate { it.symbol to (it.sector ?: "Unknown") }
        val portfolioValues = snapshot?.holdings.orEmpty().groupBy { sectors[it.symbol] ?: "Unknown" }.mapValues { (_, holdings) ->
            holdings.mapNotNull { it.marketValue }.fold(ZERO, BigDecimal::add)
        }
        val portfolioTotal = snapshot?.totalPortfolioValue ?: ZERO
        val indexWeights = index?.constituents.orEmpty().groupBy { sectors[it.symbol] ?: "Unknown" }.mapValues { (_, rows) ->
            rows.mapNotNull { it.weightPercent?.toBigDecimal()?.movePointLeft(2) }.fold(ZERO, BigDecimal::add)
        }
        return (portfolioValues.keys + indexWeights.keys).sorted().map { sector ->
            SectorComparisonRow(
                sector,
                if (portfolioTotal.signum() > 0) portfolioValues.getOrDefault(sector, ZERO).divide(portfolioTotal, pk.psx.wealth.domain.MONEY_CONTEXT) else ZERO,
                indexWeights.getOrDefault(sector, ZERO),
            )
        }
    }

    private fun screener(catalog: ResearchCatalog, prices: List<DailyPrice>, asOf: LocalDate): List<ScreenerRow> {
        val quotes = catalog.quotes.associateBy { it.symbol }
        val latestMetrics = catalog.fundamentals.groupBy { it.symbol }.mapValues { (_, rows) ->
            rows.sortedWith(compareByDescending<FundamentalMetricEntity> { it.periodEnd }.thenByDescending { it.retrievedAt })
                .distinctBy { it.metricCode }.associateBy { it.metricCode }
        }
        val memberships = mutableMapOf<String, MutableSet<String>>()
        listOf("KMI30", "KSE100", "KMIALLSHR").forEach { code ->
            catalog.indexHistory(code).firstOrNull()?.constituents.orEmpty().forEach { row -> memberships.getOrPut(row.symbol) { mutableSetOf() } += code }
        }
        val priceHistory = prices.groupBy(DailyPrice::symbol)
        return catalog.securities.filterNot { it.symbol in setOf("KMI30", "KSE100", "KMIALLSHR") }.map { security ->
            val metrics = latestMetrics[security.symbol].orEmpty()
            val values = metrics.mapValues { it.value.value.toBigDecimal() }
            val yearPrices = priceHistory[security.symbol].orEmpty().filter { it.date in asOf.minusYears(1)..asOf }.sortedBy(DailyPrice::date)
            val oneYear = if (yearPrices.size > 1 && yearPrices.first().close.signum() > 0) {
                yearPrices.last().close.divide(yearPrices.first().close, pk.psx.wealth.domain.MONEY_CONTEXT).subtract(BigDecimal.ONE)
            } else null
            ScreenerRow(security, quotes[security.symbol], metrics, scoreEngine.score(security.sector, values),
                memberships[security.symbol].orEmpty(), oneYear)
        }
    }
}
