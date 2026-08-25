package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PerformanceEngineTest {
    private val engine = PerformanceEngine(PortfolioCalculator())

    @Test
    fun `xirr uses deposits and terminal value but ignores buys`() {
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 1, 1), cash = "1000"),
            transaction(TransactionType.BUY, LocalDate.of(2025, 2, 1), symbol = "FFC", quantity = "5", price = "100"),
        )
        val rate = requireNotNull(engine.xirr(transactions, BigDecimal("1100"), LocalDate.of(2026, 1, 1)))
        assertTrue(rate.subtract(BigDecimal("0.10")).abs() < BigDecimal("0.00001"))
    }

    @Test
    fun `xirr is unavailable without both flow signs`() {
        assertNull(engine.xirr(emptyList(), BigDecimal("100"), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `xirr respects the timing of repeated contributions`() {
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 1, 1), cash = "1000"),
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 7, 2), cash = "1000"),
        )
        val rate = requireNotNull(engine.xirr(transactions, BigDecimal("2100"), LocalDate.of(2026, 1, 1)))
        assertTrue(rate > BigDecimal("0.05"))
        assertTrue(rate < BigDecimal("0.08"))
    }

    @Test
    fun `wealth history waits for a real price instead of treating missing as zero`() {
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2026, 1, 1), cash = "1000"),
            transaction(TransactionType.BUY, LocalDate.of(2026, 1, 2), symbol = "FFC", quantity = "2", price = "100"),
        )
        val prices = listOf(DailyPrice("FFC", LocalDate.of(2026, 1, 3), close = BigDecimal("110"), source = "fixture", retrievedAt = Instant.EPOCH))
        val history = engine.wealthHistory(transactions, prices, LocalDate.of(2026, 1, 3))
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)), history.map { it.date })
        assertEquals(0, BigDecimal("1020").compareTo(history.last().value))
    }

    @Test
    fun `time weighted return chain links periods`() {
        val result = requireNotNull(engine.timeWeightedReturn(listOf(
            ValuationPeriod(BigDecimal("100"), BigDecimal("110")),
            ValuationPeriod(BigDecimal("110"), BigDecimal("132")),
        )))
        assertEquals(0, BigDecimal("0.32").compareTo(result.stripTrailingZeros()))
    }

    @Test
    fun `history TWR removes deposits before chain linking`() {
        val result = requireNotNull(engine.timeWeightedReturnFromHistory(listOf(
            WealthPoint(LocalDate.of(2026, 1, 1), BigDecimal("1000"), BigDecimal("1000")),
            WealthPoint(LocalDate.of(2026, 2, 1), BigDecimal("1600"), BigDecimal("1500")),
            WealthPoint(LocalDate.of(2026, 3, 1), BigDecimal("1760"), BigDecimal("1500")),
        )))

        assertEquals(0, BigDecimal("0.21").compareTo(result.stripTrailingZeros()))
    }

    @Test
    fun `profit history reports contribution adjusted daily and cumulative pnl`() {
        val result = engine.profitLossHistory(listOf(
            WealthPoint(LocalDate.of(2026, 1, 1), BigDecimal("1000"), BigDecimal("1000")),
            WealthPoint(LocalDate.of(2026, 1, 2), BigDecimal("1110"), BigDecimal("1100")),
            WealthPoint(LocalDate.of(2026, 1, 3), BigDecimal("1090"), BigDecimal("1100")),
        ))

        assertNull(result.first().dailyProfitLoss)
        assertEquals(0, BigDecimal("10").compareTo(requireNotNull(result[1].dailyProfitLoss)))
        assertEquals(0, BigDecimal("-20").compareTo(requireNotNull(result[2].dailyProfitLoss)))
        assertEquals(0, BigDecimal("-10").compareTo(result.last().cumulativeProfitLoss))
    }

    @Test
    fun `periodic returns provide TWR MWR and contribution adjusted profit`() {
        val history = listOf(
            WealthPoint(LocalDate.of(2025, 1, 1), BigDecimal("1000"), BigDecimal("1000")),
            WealthPoint(LocalDate.of(2025, 7, 1), BigDecimal("1600"), BigDecimal("1500")),
            WealthPoint(LocalDate.of(2026, 1, 1), BigDecimal("1760"), BigDecimal("1500")),
        )
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 1, 1), cash = "1000"),
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 7, 1), cash = "500"),
        )

        val maximum = engine.periodicReturns(history, transactions, LocalDate.of(2026, 1, 1))
            .first { it.label == "MAX" }

        assertEquals(0, BigDecimal("0.21").compareTo(requireNotNull(maximum.timeWeightedReturn).stripTrailingZeros()))
        assertEquals(0, BigDecimal("260").compareTo(requireNotNull(maximum.profitLoss)))
        val moneyWeighted = requireNotNull(maximum.moneyWeightedReturn)
        assertTrue(moneyWeighted > BigDecimal("0.20"))
        assertTrue(moneyWeighted < BigDecimal("0.22"))
    }

    @Test
    fun `benchmark simulation invests each dated external flow`() {
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2026, 1, 1), cash = "1000"),
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2026, 2, 1), cash = "550"),
        )
        val result = requireNotNull(engine.simulateBenchmark(transactions, listOf(
            IndexLevel(LocalDate.of(2026, 1, 1), BigDecimal("100")),
            IndexLevel(LocalDate.of(2026, 2, 1), BigDecimal("110")),
            IndexLevel(LocalDate.of(2026, 3, 1), BigDecimal("120")),
        ), LocalDate.of(2026, 3, 1)))
        assertEquals(0, BigDecimal("1800").compareTo(result.terminalValue))
    }

    @Test
    fun `benchmark history replays deposits and withdrawals without interpolation`() {
        val transactions = listOf(
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2026, 1, 3), cash = "1000"),
            transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2026, 2, 1), cash = "550"),
            transaction(TransactionType.CASH_WITHDRAWAL, LocalDate.of(2026, 2, 15), cash = "120"),
        )
        val history = requireNotNull(engine.benchmarkHistory(transactions, listOf(
            IndexLevel(LocalDate.of(2026, 1, 2), BigDecimal("100")),
            IndexLevel(LocalDate.of(2026, 2, 1), BigDecimal("110")),
            IndexLevel(LocalDate.of(2026, 2, 14), BigDecimal("120")),
            IndexLevel(LocalDate.of(2026, 3, 1), BigDecimal("120")),
        ), LocalDate.of(2026, 3, 1)))

        assertEquals(LocalDate.of(2026, 2, 1), history.first().date)
        assertEquals(0, BigDecimal("1650").compareTo(history.first().value))
        assertEquals(0, BigDecimal("1430").compareTo(history.last().netContributions))
        assertEquals(0, BigDecimal("1680").compareTo(history.last().value))
    }

    @Test
    fun `benchmark history is unavailable when levels do not cover the first deposit`() {
        val result = engine.benchmarkHistory(
            listOf(transaction(TransactionType.CASH_DEPOSIT, LocalDate.of(2025, 1, 1), cash = "1000")),
            listOf(IndexLevel(LocalDate.of(2026, 1, 1), BigDecimal("100"))),
            LocalDate.of(2026, 1, 1),
        )
        assertNull(result)
    }

    private fun transaction(
        type: TransactionType,
        date: LocalDate,
        symbol: String? = null,
        quantity: String = "0",
        price: String = "0",
        cash: String? = null,
    ) = PortfolioTransaction(
        portfolioId = 1,
        type = type,
        tradeDate = date,
        symbol = symbol,
        quantity = BigDecimal(quantity),
        price = BigDecimal(price),
        cashAmount = cash?.let(::BigDecimal),
    )
}
