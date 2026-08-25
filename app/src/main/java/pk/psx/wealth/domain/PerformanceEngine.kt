package pk.psx.wealth.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow

data class PerformanceSummary(
    val currentValue: BigDecimal,
    val netContributions: BigDecimal,
    val realizedProfit: BigDecimal,
    val unrealizedProfit: BigDecimal?,
    val dividends: BigDecimal,
    val totalProfit: BigDecimal?,
    val absoluteReturn: BigDecimal?,
    val xirr: BigDecimal?,
)

data class WealthPoint(
    val date: LocalDate,
    val value: BigDecimal,
    val netContributions: BigDecimal,
)

data class ValuationPeriod(
    val startValue: BigDecimal,
    val endValue: BigDecimal,
    val externalFlow: BigDecimal = ZERO,
)

data class IndexLevel(val date: LocalDate, val level: BigDecimal)
data class BenchmarkSimulation(val terminalValue: BigDecimal, val investedUnits: BigDecimal)

class PerformanceEngine @Inject constructor(private val calculator: PortfolioCalculator) {
    fun summary(snapshot: PortfolioSnapshot, transactions: List<PortfolioTransaction>, asOf: LocalDate): PerformanceSummary {
        val absolute = snapshot.totalProfit?.takeIf { snapshot.netContributions.signum() != 0 }
            ?.divide(snapshot.netContributions.abs(), MONEY_CONTEXT)
        return PerformanceSummary(
            currentValue = snapshot.totalPortfolioValue,
            netContributions = snapshot.netContributions,
            realizedProfit = snapshot.realizedProfit,
            unrealizedProfit = snapshot.unrealizedProfit,
            dividends = snapshot.dividendIncome,
            totalProfit = snapshot.totalProfit,
            absoluteReturn = absolute,
            xirr = xirr(transactions, snapshot.totalPortfolioValue, asOf),
        )
    }

    /** Returns an annualized decimal rate (for example 0.10 for 10%). */
    fun xirr(
        transactions: List<PortfolioTransaction>,
        terminalValue: BigDecimal,
        terminalDate: LocalDate,
    ): BigDecimal? {
        val flows = transactions.mapNotNull { transaction ->
            val amount = transaction.cashAmount ?: transaction.grossAmount
                ?: transaction.quantity.multiply(transaction.price)
            when (transaction.type) {
                TransactionType.CASH_DEPOSIT -> transaction.tradeDate to amount.negate()
                TransactionType.CASH_WITHDRAWAL -> transaction.tradeDate to amount
                else -> null
            }
        }.filter { !it.first.isAfter(terminalDate) } + (terminalDate to terminalValue)
        val grouped = flows.groupBy { it.first }
            .map { (date, values) -> date to values.map { it.second }.fold(ZERO, BigDecimal::add) }
            .filter { it.second.signum() != 0 }
            .sortedBy { it.first }
        if (grouped.size < 2 || grouped.none { it.second.signum() < 0 } || grouped.none { it.second.signum() > 0 }) return null
        val firstDate = grouped.first().first
        if (grouped.last().first == firstDate) return null

        fun npv(rate: Double): Double = grouped.sumOf { (date, amount) ->
            val years = ChronoUnit.DAYS.between(firstDate, date).toDouble() / 365.0
            amount.toDouble() / (1.0 + rate).pow(years)
        }

        var low = -0.9999
        var high = 10.0
        var lowValue = npv(low)
        var highValue = npv(high)
        while (lowValue * highValue > 0 && high < 1_000_000) {
            high *= 10
            highValue = npv(high)
        }
        if (!lowValue.isFinite() || !highValue.isFinite() || lowValue * highValue > 0) return null
        repeat(200) {
            val middle = (low + high) / 2
            val value = npv(middle)
            if (abs(value) < 0.000001) return BigDecimal.valueOf(middle)
            if (lowValue * value <= 0) {
                high = middle
                highValue = value
            } else {
                low = middle
                lowValue = value
            }
        }
        return BigDecimal.valueOf((low + high) / 2)
    }

    fun wealthHistory(
        transactions: List<PortfolioTransaction>,
        prices: List<DailyPrice>,
        asOf: LocalDate,
    ): List<WealthPoint> {
        val eligibleTransactions = transactions.filter { !it.tradeDate.isAfter(asOf) }
        if (eligibleTransactions.isEmpty()) return emptyList()
        val pricesByDate = prices.filter { !it.date.isAfter(asOf) }.groupBy(DailyPrice::date)
        val dates = (eligibleTransactions.map(PortfolioTransaction::tradeDate) + pricesByDate.keys).distinct().sorted()
        val latestPrices = mutableMapOf<String, BigDecimal?>()
        return dates.mapNotNull { date ->
            pricesByDate[date].orEmpty().forEach { latestPrices[it.symbol] = it.close }
            val snapshot = calculator.calculate(eligibleTransactions.filter { !it.tradeDate.isAfter(date) }, latestPrices)
            if (!snapshot.hasCompletePrices) null else WealthPoint(date, snapshot.totalPortfolioValue, snapshot.netContributions)
        }
    }

    /** Chain-links sub-period returns; externalFlow is measured immediately before end valuation. */
    fun timeWeightedReturn(periods: List<ValuationPeriod>): BigDecimal? {
        if (periods.isEmpty() || periods.any { it.startValue.signum() <= 0 }) return null
        return periods.fold(BigDecimal.ONE) { growth, period ->
            val rate = period.endValue.subtract(period.externalFlow).divide(period.startValue, MONEY_CONTEXT)
            growth.multiply(rate, MONEY_CONTEXT)
        }.subtract(BigDecimal.ONE)
    }

    /**
     * Invests each external flow into the most recent available index level.
     * Returns null rather than interpolating when a flow or terminal date has no prior level.
     */
    fun simulateBenchmark(
        transactions: List<PortfolioTransaction>,
        levels: List<IndexLevel>,
        asOf: LocalDate,
    ): BenchmarkSimulation? {
        val sortedLevels = levels.filter { !it.date.isAfter(asOf) && it.level.signum() > 0 }.sortedBy(IndexLevel::date)
        if (sortedLevels.isEmpty()) return null
        fun levelAt(date: LocalDate) = sortedLevels.lastOrNull { !it.date.isAfter(date) }?.level
        var units = ZERO
        transactions.filter { !it.tradeDate.isAfter(asOf) }.sortedBy(PortfolioTransaction::tradeDate).forEach { transaction ->
            val amount = transaction.cashAmount ?: transaction.grossAmount
                ?: transaction.quantity.multiply(transaction.price)
            val signed = when (transaction.type) {
                TransactionType.CASH_DEPOSIT -> amount
                TransactionType.CASH_WITHDRAWAL -> amount.negate()
                else -> return@forEach
            }
            val level = levelAt(transaction.tradeDate) ?: return null
            units = units.add(signed.divide(level, MONEY_CONTEXT))
            if (units.signum() < 0) return null
        }
        val terminalLevel = levelAt(asOf) ?: return null
        return BenchmarkSimulation(units.multiply(terminalLevel, MONEY_CONTEXT), units)
    }
}
