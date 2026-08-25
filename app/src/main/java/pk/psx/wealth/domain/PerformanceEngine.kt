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
    val timeWeightedReturn: BigDecimal? = null,
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
data class BenchmarkPoint(
    val date: LocalDate,
    val value: BigDecimal,
    val netContributions: BigDecimal,
)

data class ProfitLossPoint(
    val date: LocalDate,
    val dailyProfitLoss: BigDecimal?,
    val cumulativeProfitLoss: BigDecimal,
)

data class PeriodReturnSummary(
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeWeightedReturn: BigDecimal?,
    /** Non-annualized money-weighted return for this exact period. */
    val moneyWeightedReturn: BigDecimal?,
    val profitLoss: BigDecimal?,
)

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
        return solveXirr(flows)
    }

    private fun solveXirr(flows: List<Pair<LocalDate, BigDecimal>>): BigDecimal? {
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
     * Builds valuation sub-periods from contribution-adjusted wealth observations.
     * Contributions are treated as end-of-period flows, matching [ValuationPeriod].
     */
    fun timeWeightedReturnFromHistory(history: List<WealthPoint>): BigDecimal? {
        val points = history.sortedBy(WealthPoint::date).distinctBy(WealthPoint::date)
        if (points.size < 2) return null
        return timeWeightedReturn(points.zipWithNext { start, end ->
            ValuationPeriod(
                startValue = start.value,
                endValue = end.value,
                externalFlow = end.netContributions.subtract(start.netContributions),
            )
        })
    }

    /** Daily means each available complete local valuation date; gaps are never fabricated. */
    fun profitLossHistory(history: List<WealthPoint>): List<ProfitLossPoint> {
        val points = history.sortedBy(WealthPoint::date).distinctBy(WealthPoint::date)
        return points.mapIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            val daily = previous?.let {
                point.value.subtract(it.value)
                    .subtract(point.netContributions.subtract(it.netContributions))
            }
            ProfitLossPoint(
                date = point.date,
                dailyProfitLoss = daily,
                cumulativeProfitLoss = point.value.subtract(point.netContributions),
            )
        }
    }

    /**
     * Calculates comparable 1M, 3M, YTD, 1Y and MAX returns from complete local
     * valuation observations. TWR is chain-linked; MWR is an XIRR converted from
     * an annualized rate to the exact period length.
     */
    fun periodicReturns(
        history: List<WealthPoint>,
        transactions: List<PortfolioTransaction>,
        asOf: LocalDate,
    ): List<PeriodReturnSummary> {
        val points = history.filter { !it.date.isAfter(asOf) }
            .sortedBy(WealthPoint::date)
            .distinctBy(WealthPoint::date)
        val windows = listOf(
            "1M" to asOf.minusMonths(1),
            "3M" to asOf.minusMonths(3),
            "YTD" to asOf.withDayOfYear(1),
            "1Y" to asOf.minusYears(1),
            "MAX" to (points.firstOrNull()?.date ?: asOf),
        )
        return windows.map { (label, requestedStart) ->
            val anchor = points.lastOrNull { !it.date.isAfter(requestedStart) }
            val afterStart = points.filter { !it.date.isBefore(requestedStart) }
            val periodPoints = (listOfNotNull(anchor) + afterStart).distinctBy(WealthPoint::date)
            val start = periodPoints.firstOrNull()
            val end = periodPoints.lastOrNull()
            if (start == null || end == null || start.date == end.date) {
                PeriodReturnSummary(label, requestedStart, asOf, null, null, null)
            } else {
                val profit = end.value.subtract(start.value)
                    .subtract(end.netContributions.subtract(start.netContributions))
                PeriodReturnSummary(
                    label = label,
                    startDate = start.date,
                    endDate = end.date,
                    timeWeightedReturn = timeWeightedReturnFromHistory(periodPoints),
                    moneyWeightedReturn = periodicMoneyWeightedReturn(start, end, transactions),
                    profitLoss = profit,
                )
            }
        }
    }

    private fun periodicMoneyWeightedReturn(
        start: WealthPoint,
        end: WealthPoint,
        transactions: List<PortfolioTransaction>,
    ): BigDecimal? {
        val flows = buildList {
            add(start.date to start.value.negate())
            transactions.asSequence()
                .filter { it.tradeDate.isAfter(start.date) && !it.tradeDate.isAfter(end.date) }
                .mapNotNull { transaction ->
                    val amount = transaction.cashAmount ?: transaction.grossAmount
                        ?: transaction.quantity.multiply(transaction.price)
                    when (transaction.type) {
                        TransactionType.CASH_DEPOSIT -> transaction.tradeDate to amount.negate()
                        TransactionType.CASH_WITHDRAWAL -> transaction.tradeDate to amount
                        else -> null
                    }
                }
                .forEach { add(it) }
            add(end.date to end.value)
        }
        val annualized = solveXirr(flows) ?: return null
        val days = ChronoUnit.DAYS.between(start.date, end.date)
        if (days <= 0 || annualized <= BigDecimal("-1")) return null
        val periodRate = (1.0 + annualized.toDouble()).pow(days.toDouble() / 365.0) - 1.0
        return periodRate.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)
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
        var hasExternalFlow = false
        transactions.filter { !it.tradeDate.isAfter(asOf) }.sortedBy(PortfolioTransaction::tradeDate).forEach { transaction ->
            val amount = transaction.cashAmount ?: transaction.grossAmount
                ?: transaction.quantity.multiply(transaction.price)
            val signed = when (transaction.type) {
                TransactionType.CASH_DEPOSIT -> amount
                TransactionType.CASH_WITHDRAWAL -> amount.negate()
                else -> return@forEach
            }
            hasExternalFlow = true
            val level = levelAt(transaction.tradeDate) ?: return null
            units = units.add(signed.divide(level, MONEY_CONTEXT))
            if (units.signum() < 0) return null
        }
        if (!hasExternalFlow) return null
        val terminalLevel = levelAt(asOf) ?: return null
        return BenchmarkSimulation(units.multiply(terminalLevel, MONEY_CONTEXT), units)
    }

    /**
     * Replays external contributions into index units and values those units on every cached index date.
     * A contribution on a non-trading day uses the most recent prior level. No interpolation is invented.
     */
    fun benchmarkHistory(
        transactions: List<PortfolioTransaction>,
        levels: List<IndexLevel>,
        asOf: LocalDate,
    ): List<BenchmarkPoint>? {
        val sortedLevels = levels.filter { !it.date.isAfter(asOf) && it.level.signum() > 0 }
            .sortedBy(IndexLevel::date)
            .distinctBy(IndexLevel::date)
        val flows = transactions.filter { !it.tradeDate.isAfter(asOf) }.mapNotNull { transaction ->
            val amount = transaction.cashAmount ?: transaction.grossAmount
                ?: transaction.quantity.multiply(transaction.price)
            when (transaction.type) {
                TransactionType.CASH_DEPOSIT -> Triple(transaction.tradeDate, amount, amount)
                TransactionType.CASH_WITHDRAWAL -> Triple(transaction.tradeDate, amount.negate(), amount.negate())
                else -> null
            }
        }.sortedBy { it.first }
        if (sortedLevels.isEmpty() || flows.isEmpty()) return null
        fun levelAt(date: LocalDate) = sortedLevels.lastOrNull { !it.date.isAfter(date) }?.level
        if (levelAt(flows.first().first) == null) return null

        var units = ZERO
        var contributions = ZERO
        var nextFlow = 0
        val points = mutableListOf<BenchmarkPoint>()
        sortedLevels.forEach { level ->
            while (nextFlow < flows.size && !flows[nextFlow].first.isAfter(level.date)) {
                val flow = flows[nextFlow]
                val executionLevel = levelAt(flow.first) ?: return null
                units = units.add(flow.second.divide(executionLevel, MONEY_CONTEXT))
                contributions = contributions.add(flow.third)
                if (units.signum() < 0) return null
                nextFlow++
            }
            if (units.signum() > 0) {
                points += BenchmarkPoint(level.date, units.multiply(level.level, MONEY_CONTEXT), contributions)
            }
        }
        var appendedTerminalFlow = false
        while (nextFlow < flows.size && !flows[nextFlow].first.isAfter(asOf)) {
            val flow = flows[nextFlow]
            val executionLevel = levelAt(flow.first) ?: return null
            units = units.add(flow.second.divide(executionLevel, MONEY_CONTEXT))
            contributions = contributions.add(flow.third)
            if (units.signum() < 0) return null
            nextFlow++
            appendedTerminalFlow = true
        }
        if (appendedTerminalFlow && units.signum() > 0) {
            val terminalLevel = levelAt(asOf) ?: return null
            points += BenchmarkPoint(asOf, units.multiply(terminalLevel, MONEY_CONTEXT), contributions)
        }
        return points.takeIf { it.isNotEmpty() }
    }
}
