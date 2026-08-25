package pk.psx.wealth.domain

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class DataHealthSeverity { ERROR, WARNING, INFO }
enum class DataHealthStatus { HEALTHY, ATTENTION, ERROR }

data class DataHealthIssue(
    val code: String,
    val severity: DataHealthSeverity,
    val title: String,
    val detail: String,
    val affectedCount: Int,
    val action: String,
)

data class DataHealthReport(
    val checkedAt: Instant? = null,
    val issues: List<DataHealthIssue> = emptyList(),
) {
    val errorCount: Int get() = issues.count { it.severity == DataHealthSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == DataHealthSeverity.WARNING }
    val status: DataHealthStatus get() = when {
        errorCount > 0 -> DataHealthStatus.ERROR
        warningCount > 0 -> DataHealthStatus.ATTENTION
        else -> DataHealthStatus.HEALTHY
    }
}

data class DataQualityQuote(val symbol: String, val price: BigDecimal, val retrievedAt: Instant)
data class DataQualityTarget(val portfolioId: Long, val symbol: String, val targetPercent: BigDecimal)
data class IndexCacheQuality(
    val indexCode: String,
    val snapshotDate: LocalDate,
    val memberCount: Int,
    val weightPercentTotal: BigDecimal?,
)

data class DataQualityInput(
    val portfolioIds: Set<Long>,
    val transactions: List<PortfolioTransaction>,
    val quotes: List<DataQualityQuote>,
    val targets: List<DataQualityTarget>,
    val watchlistSymbols: Set<String>,
    val latestIndexes: List<IndexCacheQuality>,
    val checkedAt: Instant,
    val checkedDate: LocalDate,
)

class LocalDataQualityEngine @Inject constructor(private val calculator: PortfolioCalculator) {
    fun inspect(input: DataQualityInput): DataHealthReport {
        val issues = mutableListOf<DataHealthIssue>()
        val normalizedQuotes = input.quotes.map { it.copy(symbol = it.symbol.trim().uppercase()) }
        val normalizedTargets = input.targets.map { it.copy(symbol = it.symbol.trim().uppercase()) }
        val normalizedIndexes = input.latestIndexes.map { it.copy(indexCode = it.indexCode.trim().uppercase()) }
        if (input.portfolioIds.isEmpty()) {
            issues += issue("NO_PORTFOLIO", DataHealthSeverity.INFO, "No portfolio yet",
                "Create a local portfolio before running ledger and valuation checks.", 0, "Create a portfolio from Home.")
        }

        val orphanTransactions = input.transactions.filter { it.portfolioId !in input.portfolioIds }
        if (orphanTransactions.isNotEmpty()) issues += issue(
            "ORPHAN_TRANSACTIONS", DataHealthSeverity.ERROR, "Transactions reference a missing portfolio",
            "${orphanTransactions.size} ledger row(s) cannot be assigned to an existing portfolio.",
            orphanTransactions.size, "Restore a known-good backup or correct the affected records.",
        )

        val futureTransactions = input.transactions.filter { it.tradeDate.isAfter(input.checkedDate) }
        if (futureTransactions.isNotEmpty()) issues += issue(
            "FUTURE_TRANSACTIONS", DataHealthSeverity.WARNING, "Transactions use a future date",
            "Review transaction IDs ${futureTransactions.map { it.id }.sample()}.",
            futureTransactions.size, "Edit the date if it was entered accidentally.",
        )

        val invalidIds = mutableSetOf<Long>()
        input.transactions.forEach { transaction ->
            runCatching { calculator.validate(transaction) }.onFailure { invalidIds += transaction.id }
        }
        if (invalidIds.isNotEmpty()) issues += issue(
            "INVALID_TRANSACTIONS", DataHealthSeverity.ERROR, "Ledger rows fail accounting validation",
            "Invalid transaction IDs ${invalidIds.sample()}.", invalidIds.size,
            "Edit the affected rows; quantities, prices, fees, tax and symbols must be valid.",
        )

        val heldSymbols = mutableSetOf<String>()
        val negativeCashPortfolios = mutableSetOf<Long>()
        val replayFailures = mutableListOf<Long>()
        input.portfolioIds.forEach { portfolioId ->
            val ledger = input.transactions.filter { it.portfolioId == portfolioId && it.id !in invalidIds }
            runCatching { calculator.calculate(ledger, emptyMap()) }
                .onSuccess { snapshot ->
                    heldSymbols += snapshot.holdings.map(Holding::symbol)
                    if (snapshot.cashBalance.signum() < 0) negativeCashPortfolios += portfolioId
                }
                .onFailure { replayFailures += portfolioId }
        }
        if (replayFailures.isNotEmpty()) issues += issue(
            "LEDGER_REPLAY_FAILED", DataHealthSeverity.ERROR, "A portfolio ledger cannot be replayed",
            "Portfolio IDs ${replayFailures.sample()} contain an impossible sequence, commonly an oversell.",
            replayFailures.size, "Review sells and corporate actions in chronological order.",
        )
        if (negativeCashPortfolios.isNotEmpty()) issues += issue(
            "NEGATIVE_CASH", DataHealthSeverity.WARNING, "Calculated cash is negative",
            "Portfolio IDs ${negativeCashPortfolios.sample()} currently have negative ledger cash.",
            negativeCashPortfolios.size, "Confirm missing deposits, fees, withdrawals or correction entries.",
        )

        val orphanTargets = normalizedTargets.filter { it.portfolioId !in input.portfolioIds }
        if (orphanTargets.isNotEmpty()) issues += issue(
            "ORPHAN_TARGETS", DataHealthSeverity.ERROR, "Targets reference a missing portfolio",
            "${orphanTargets.size} allocation row(s) cannot be assigned to an existing portfolio.",
            orphanTargets.size, "Restore a known-good backup or correct the affected targets.",
        )
        val activeTargets = normalizedTargets.filter { it.portfolioId in input.portfolioIds }
        val invalidTargets = activeTargets.filter {
            it.symbol.isBlank() || it.targetPercent < ZERO || it.targetPercent > BigDecimal(100)
        }
        val overAllocated = activeTargets.groupBy(DataQualityTarget::portfolioId).filterValues { rows ->
            rows.fold(ZERO) { total, row -> total.add(row.targetPercent) } > BigDecimal("100.0001")
        }.keys
        if (invalidTargets.isNotEmpty() || overAllocated.isNotEmpty()) issues += issue(
            "INVALID_TARGETS", DataHealthSeverity.ERROR, "Target allocation exceeds valid limits",
            "${invalidTargets.size} target row(s) are outside 0–100%; ${overAllocated.size} portfolio total(s) exceed 100%.",
            invalidTargets.size + overAllocated.size, "Open Targets and save a valid allocation.",
        )

        val requiredQuoteSymbols = (heldSymbols + activeTargets.map(DataQualityTarget::symbol) + input.watchlistSymbols)
            .map { it.trim().uppercase() }.filter(String::isNotBlank).toSortedSet()
        val quotesBySymbol = normalizedQuotes.associateBy { it.symbol }
        val invalidQuotes = normalizedQuotes.filter { it.symbol.isBlank() || it.price.signum() <= 0 }
        if (invalidQuotes.isNotEmpty()) issues += issue(
            "INVALID_QUOTES", DataHealthSeverity.ERROR, "Stored quotes contain invalid prices",
            "Non-positive prices: ${invalidQuotes.map(DataQualityQuote::symbol).sample()}.",
            invalidQuotes.size, "Enter a valid manual price or refresh; do not treat an unknown price as zero.",
        )
        val missingQuotes = requiredQuoteSymbols.filter { it !in quotesBySymbol }
        if (missingQuotes.isNotEmpty()) issues += issue(
            "MISSING_QUOTES", DataHealthSeverity.WARNING, "Securities have no stored price",
            "Missing: ${missingQuotes.sample()}.", missingQuotes.size,
            "Refresh while online or enter a labelled manual price.",
        )
        val staleQuotes = requiredQuoteSymbols.mapNotNull(quotesBySymbol::get).filter {
            Duration.between(it.retrievedAt, input.checkedAt).toDays() > QUOTE_STALE_DAYS
        }
        if (staleQuotes.isNotEmpty()) issues += issue(
            "STALE_QUOTES", DataHealthSeverity.WARNING, "Stored prices are stale",
            "Older than $QUOTE_STALE_DAYS days: ${staleQuotes.map(DataQualityQuote::symbol).sample()}.",
            staleQuotes.size, "Refresh or enter a newer manual observation when convenient.",
        )
        val futureQuotes = normalizedQuotes.filter { it.retrievedAt.isAfter(input.checkedAt.plus(Duration.ofHours(1))) }
        if (futureQuotes.isNotEmpty()) issues += issue(
            "FUTURE_QUOTES", DataHealthSeverity.WARNING, "Quote timestamps are in the future",
            "Check the device clock for ${futureQuotes.map(DataQualityQuote::symbol).sample()}.",
            futureQuotes.size, "Correct the device date/time, then refresh the affected quote.",
        )

        val indexByCode = normalizedIndexes.associateBy(IndexCacheQuality::indexCode)
        val missingIndexes = REQUIRED_INDEXES.filter { it !in indexByCode }
        if (missingIndexes.isNotEmpty()) issues += issue(
            "MISSING_INDEX_CACHE", DataHealthSeverity.INFO, "Some index snapshots are not cached",
            "Not cached: ${missingIndexes.joinToString()}.", missingIndexes.size,
            "Refresh only the indexes you use; offline portfolio accounting is unaffected.",
        )
        val invalidIndexes = normalizedIndexes.filter { snapshot ->
            snapshot.memberCount !in expectedMembers(snapshot.indexCode) ||
                snapshot.weightPercentTotal?.let { it < BigDecimal("95") || it > BigDecimal("105") } == true
        }
        if (invalidIndexes.isNotEmpty()) issues += issue(
            "INVALID_INDEX_CACHE", DataHealthSeverity.ERROR, "A cached index snapshot looks incomplete",
            invalidIndexes.joinToString { "${it.indexCode} (${it.memberCount} members)" }, invalidIndexes.size,
            "Keep the prior backup and refresh; do not use this snapshot for target allocation.",
        )
        val staleIndexes = normalizedIndexes.filter {
            ChronoUnit.DAYS.between(it.snapshotDate, input.checkedDate) > INDEX_STALE_DAYS
        }
        if (staleIndexes.isNotEmpty()) issues += issue(
            "STALE_INDEX_CACHE", DataHealthSeverity.WARNING, "Index snapshots are stale",
            "Older than $INDEX_STALE_DAYS days: ${staleIndexes.map(IndexCacheQuality::indexCode).sample()}.",
            staleIndexes.size, "Refresh the indexes before an index-weight rebalance.",
        )
        val futureIndexes = normalizedIndexes.filter { it.snapshotDate.isAfter(input.checkedDate.plusDays(1)) }
        if (futureIndexes.isNotEmpty()) issues += issue(
            "FUTURE_INDEX_CACHE", DataHealthSeverity.WARNING, "Index snapshot dates are in the future",
            "Check the device clock for ${futureIndexes.map(IndexCacheQuality::indexCode).sample()}.",
            futureIndexes.size, "Correct the device date/time, then refresh the affected index.",
        )

        return DataHealthReport(
            checkedAt = input.checkedAt,
            issues = issues.sortedWith(compareBy<DataHealthIssue> { it.severity.ordinal }.thenBy { it.code }),
        )
    }

    private fun expectedMembers(code: String) = when (code) {
        "KMI30" -> 25..35
        "KSE100" -> 85..115
        "KMIALLSHR" -> 80..500
        else -> 1..500
    }

    private fun issue(
        code: String,
        severity: DataHealthSeverity,
        title: String,
        detail: String,
        affected: Int,
        action: String,
    ) = DataHealthIssue(code, severity, title, detail, affected, action)

    private fun Collection<*>.sample(limit: Int = 6): String {
        val shown = take(limit).joinToString()
        return if (size > limit) "$shown, +${size - limit} more" else shown
    }

    private companion object {
        const val QUOTE_STALE_DAYS = 7L
        const val INDEX_STALE_DAYS = 14L
        val REQUIRED_INDEXES = setOf("KMI30", "KSE100", "KMIALLSHR")
    }
}
