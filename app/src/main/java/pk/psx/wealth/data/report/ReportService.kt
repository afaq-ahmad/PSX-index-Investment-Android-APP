package pk.psx.wealth.data.report

import pk.psx.wealth.data.local.BackupDao
import pk.psx.wealth.data.local.toDomain
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.DividendEngine
import pk.psx.wealth.domain.PerformanceEngine
import pk.psx.wealth.domain.PortfolioCalculator
import pk.psx.wealth.domain.TransactionType
import pk.psx.wealth.domain.ZERO
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ReportType {
    PORTFOLIO_SUMMARY,
    HOLDINGS,
    TRANSACTIONS,
    CASH_CONTRIBUTIONS,
    DIVIDEND_INCOME,
    PERFORMANCE,
    ALLOCATION_VS_BENCHMARK,
    REBALANCE_PLAN,
    ANNUAL_WEALTH_SUMMARY,
}

data class ReportExport(val fileName: String, val bytes: ByteArray)

@Singleton
class ReportService @Inject constructor(
    private val dao: BackupDao,
    private val calculator: PortfolioCalculator,
    private val performance: PerformanceEngine,
    private val dividends: DividendEngine,
    private val clock: Clock,
) {
    suspend fun create(type: ReportType, portfolioId: Long): ReportExport {
        val portfolio = requireNotNull(dao.portfolios().firstOrNull { it.id == portfolioId }) { "Selected portfolio was not found" }
        val transactions = dao.transactions().filter { it.portfolioId == portfolioId }.map { it.toDomain() }
        val quoteMap = dao.quotes().associate { it.symbol to it.price.toBigDecimal() }
        val snapshot = calculator.calculate(transactions, quoteMap)
        val today = LocalDate.now(clock)
        val csv = when (type) {
            ReportType.PORTFOLIO_SUMMARY -> table(listOf("Metric", "Value"), listOf(
                listOf("Portfolio", portfolio.name), listOf("As of", today.toString()), listOf("Benchmark", portfolio.benchmark),
                listOf("Portfolio value", snapshot.totalPortfolioValue.text()), listOf("Cash", snapshot.cashBalance.text()),
                listOf("Stock market value", snapshot.stockMarketValue.text()), listOf("Net contributions", snapshot.netContributions.text()),
                listOf("Realized profit", snapshot.realizedProfit.text()), listOf("Unrealized profit", snapshot.unrealizedProfit.text()),
                listOf("Dividend income", snapshot.dividendIncome.text()), listOf("Fees and taxes", snapshot.feesAndTaxes.text()),
                listOf("Total profit", snapshot.totalProfit.text()), listOf("All prices available", snapshot.hasCompletePrices.toString()),
            ))
            ReportType.HOLDINGS -> table(
                listOf("Symbol", "Quantity", "Market price", "Market value", "Remaining cost", "Average cost", "Realized P/L", "Unrealized P/L", "Dividends", "Portfolio weight"),
                snapshot.holdings.map { holding -> listOf(holding.symbol, holding.quantity.text(), holding.marketPrice.text(), holding.marketValue.text(),
                    holding.remainingCost.text(), holding.averageCost.text(), holding.realizedProfit.text(), holding.unrealizedProfit.text(),
                    holding.dividends.text(), snapshot.portfolioWeight(holding.symbol).text()) },
            )
            ReportType.TRANSACTIONS -> table(
                listOf("ID", "Date", "Type", "Symbol", "Quantity", "Price", "Gross amount", "Fees", "Tax", "Cash amount", "Notes"),
                transactions.map { tx -> listOf(tx.id.toString(), tx.tradeDate.toString(), tx.type.name, tx.symbol.orEmpty(), tx.quantity.text(),
                    tx.price.text(), tx.grossAmount.text(), tx.fees.text(), tx.tax.text(), tx.cashAmount.text(), tx.notes.orEmpty()) },
            )
            ReportType.CASH_CONTRIBUTIONS -> table(
                listOf("Date", "Type", "Amount", "Running net contributions", "Notes"),
                buildList {
                    var running = ZERO
                    transactions.filter { it.type in setOf(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL) }
                        .sortedWith(compareBy({ it.tradeDate }, { it.id })).forEach { tx ->
                            val amount = tx.cashAmount ?: tx.grossAmount ?: ZERO
                            running = if (tx.type == TransactionType.CASH_DEPOSIT) running.add(amount) else running.subtract(amount)
                            add(listOf(tx.tradeDate.toString(), tx.type.name, amount.text(), running.text(), tx.notes.orEmpty()))
                        }
                },
            )
            ReportType.DIVIDEND_INCOME -> {
                val summary = dividends.summarize(transactions, snapshot, today)
                table(listOf("Section", "Period/Symbol", "Value"), buildList {
                    add(listOf("Summary", "Lifetime", summary.total.text()))
                    add(listOf("Summary", "This year", summary.thisYear.text()))
                    add(listOf("Summary", "Trailing 12 months", summary.trailingTwelveMonths.text()))
                    summary.byYear.forEach { (year, value) -> add(listOf("Year", year.toString(), value.text())) }
                    summary.bySecurity.forEach { row -> add(listOf("Security", row.symbol, row.total.text())) }
                })
            }
            ReportType.PERFORMANCE -> {
                val result = performance.summary(snapshot, transactions, today)
                table(listOf("Metric", "Value"), listOf(
                    listOf("As of", today.toString()), listOf("Current value", result.currentValue.text()),
                    listOf("Net contributions", result.netContributions.text()), listOf("Realized profit", result.realizedProfit.text()),
                    listOf("Unrealized profit", result.unrealizedProfit.text()), listOf("Dividends", result.dividends.text()),
                    listOf("Total profit", result.totalProfit.text()), listOf("Absolute return (decimal)", result.absoluteReturn.text()),
                    listOf("XIRR (annual decimal)", result.xirr.text()),
                ))
            }
            ReportType.ALLOCATION_VS_BENCHMARK -> {
                val header = dao.indexSnapshots().filter { it.indexCode == portfolio.benchmark }
                    .maxWithOrNull(compareBy<pk.psx.wealth.data.local.IndexSnapshotEntity>({ it.snapshotDate }, { it.retrievedAt }))
                val rows = header?.let { selected -> dao.indexConstituents().filter { it.snapshotId == selected.id } }.orEmpty().associateBy { it.symbol }
                val symbols = (rows.keys + snapshot.holdings.map { it.symbol }).sorted()
                table(listOf("Symbol", "Portfolio weight", "Index weight", "Difference", "Membership", "Snapshot date"), symbols.map { symbol ->
                    val portfolioWeight = snapshot.portfolioWeight(symbol) ?: ZERO
                    val indexWeight = rows[symbol]?.weightPercent?.toBigDecimal()?.movePointLeft(2) ?: ZERO
                    listOf(symbol, portfolioWeight.text(), indexWeight.text(), portfolioWeight.subtract(indexWeight).text(), when {
                        symbol in rows && snapshot.holdings.any { it.symbol == symbol } -> "BOTH"
                        symbol in rows -> "INDEX_ONLY"
                        else -> "OWNED_ONLY"
                    }, header?.snapshotDate.orEmpty())
                })
            }
            ReportType.REBALANCE_PLAN -> {
                val plan = dao.rebalancePlans().filter { it.portfolioId == portfolioId }.maxByOrNull { it.createdAt }
                val items = plan?.let { selected -> dao.rebalanceItems().filter { it.planId == selected.id } }.orEmpty()
                table(listOf("Plan ID", "Status", "Created", "Symbol", "Action", "Quantity", "Estimated price", "Estimated value", "Current weight", "Target weight", "Projected weight"),
                    items.map { item -> listOf(item.planId.toString(), plan?.status.orEmpty(), plan?.createdAt?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
                        item.symbol, item.action, item.quantity.toString(), item.estimatedPrice.toString(), item.estimatedValue.toString(),
                        item.currentWeight.toString(), item.targetWeight.toString(), item.projectedWeight.toString()) })
            }
            ReportType.ANNUAL_WEALTH_SUMMARY -> {
                val prices = dao.prices().map { row -> DailyPrice(row.symbol, LocalDate.parse(row.date), row.open?.toBigDecimal(), row.high?.toBigDecimal(),
                    row.low?.toBigDecimal(), row.close.toBigDecimal(), row.volume, row.isAdjusted, row.source, Instant.ofEpochMilli(row.retrievedAt)) }
                val points = performance.wealthHistory(transactions, prices, today).groupBy { it.date.year }.mapValues { (_, rows) -> rows.maxBy { it.date } }
                table(listOf("Year", "Year-end local valuation date", "Portfolio value", "Net contributions"), points.toSortedMap().map { (year, point) ->
                    listOf(year.toString(), point.date.toString(), point.value.text(), point.netContributions.text())
                })
            }
        }
        val name = "${portfolio.name.fileSafe()}-${type.name.lowercase().replace('_', '-')}-${today}.csv"
        return ReportExport(name, csv.encodeToByteArray())
    }

    private fun table(headers: List<String>, rows: List<List<String>>): String = buildString {
        appendLine(headers.joinToString(",", transform = ::cell))
        rows.forEach { row -> appendLine(row.joinToString(",", transform = ::cell)) }
    }

    private fun cell(raw: String): String {
        val protected = if (raw.toBigDecimalOrNull() == null && raw.firstOrNull() in setOf('=', '+', '-', '@')) "'$raw" else raw
        return "\"${protected.replace("\"", "\"\"")}\""
    }

    private fun BigDecimal?.text(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()
    private fun String.fileSafe() = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "portfolio" }
}
