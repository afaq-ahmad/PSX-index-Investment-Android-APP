package pk.psx.wealth.domain

import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class SecurityDividendSummary(
    val symbol: String,
    val total: BigDecimal,
    val thisYear: BigDecimal,
    val yieldOnCost: BigDecimal?,
)

data class DividendSummary(
    val total: BigDecimal,
    val thisYear: BigDecimal,
    val trailingTwelveMonths: BigDecimal,
    val trailingMonthlyAverage: BigDecimal,
    val contributionToTotalReturn: BigDecimal?,
    val byYear: Map<Int, BigDecimal>,
    val bySecurity: List<SecurityDividendSummary>,
)

class DividendEngine @Inject constructor() {
    fun summarize(
        transactions: List<PortfolioTransaction>,
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
    ): DividendSummary {
        val dividends = transactions.filter { it.type == TransactionType.DIVIDEND && !it.tradeDate.isAfter(asOf) }
            .map { transaction ->
                val net = transaction.cashAmount
                    ?: (transaction.grossAmount ?: ZERO).subtract(transaction.tax).subtract(transaction.fees)
                Triple(transaction.tradeDate, transaction.symbol.orEmpty(), net)
            }
        val total = dividends.map { it.third }.fold(ZERO, BigDecimal::add)
        val thisYear = dividends.filter { it.first.year == asOf.year }.map { it.third }.fold(ZERO, BigDecimal::add)
        val trailingStart = asOf.minusYears(1).plusDays(1)
        val trailing = dividends.filter { it.first in trailingStart..asOf }.map { it.third }.fold(ZERO, BigDecimal::add)
        val byYear = dividends.groupBy { it.first.year }.toSortedMap().mapValues { (_, rows) ->
            rows.map { it.third }.fold(ZERO, BigDecimal::add)
        }
        val holdings = snapshot.holdings.associateBy(Holding::symbol)
        val bySecurity = dividends.groupBy { it.second }.map { (symbol, rows) ->
            val securityTotal = rows.map { it.third }.fold(ZERO, BigDecimal::add)
            val cost = holdings[symbol]?.remainingCost?.takeIf { it.signum() > 0 }
            SecurityDividendSummary(
                symbol = symbol,
                total = securityTotal,
                thisYear = rows.filter { it.first.year == asOf.year }.map { it.third }.fold(ZERO, BigDecimal::add),
                yieldOnCost = cost?.let { securityTotal.divide(it, MONEY_CONTEXT) },
            )
        }.sortedByDescending(SecurityDividendSummary::total)
        return DividendSummary(
            total = total,
            thisYear = thisYear,
            trailingTwelveMonths = trailing,
            trailingMonthlyAverage = trailing.divide(BigDecimal(12), MONEY_CONTEXT),
            contributionToTotalReturn = snapshot.totalProfit?.takeIf { it.signum() != 0 }
                ?.let { total.divide(it.abs(), MONEY_CONTEXT) },
            byYear = byYear,
            bySecurity = bySecurity,
        )
    }
}
