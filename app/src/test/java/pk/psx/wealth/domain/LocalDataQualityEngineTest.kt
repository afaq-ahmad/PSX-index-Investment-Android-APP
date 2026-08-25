package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class LocalDataQualityEngineTest {
    private val engine = LocalDataQualityEngine(PortfolioCalculator())
    private val now = Instant.parse("2026-08-25T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 25)

    @Test
    fun `valid local ledger prices targets and indexes are healthy`() {
        val report = engine.inspect(input(
            transactions = listOf(
                transaction(1, TransactionType.CASH_DEPOSIT, today.minusMonths(2), cash = "1000"),
                transaction(2, TransactionType.BUY, today.minusMonths(1), symbol = "FFC", quantity = "5", price = "100"),
            ),
            quotes = listOf(DataQualityQuote("FFC", BigDecimal("110"), now)),
            targets = listOf(DataQualityTarget(1, "FFC", BigDecimal("100"))),
        ))

        assertEquals(DataHealthStatus.HEALTHY, report.status)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `impossible ledger and over allocated targets are errors`() {
        val report = engine.inspect(input(
            transactions = listOf(
                transaction(1, TransactionType.CASH_DEPOSIT, today, cash = "100"),
                transaction(2, TransactionType.BUY, today, symbol = "FFC", quantity = "1", price = "100"),
                transaction(3, TransactionType.SELL, today, symbol = "FFC", quantity = "2", price = "100"),
            ),
            targets = listOf(
                DataQualityTarget(1, "FFC", BigDecimal("80")),
                DataQualityTarget(1, "MARI", BigDecimal("40")),
            ),
        ))

        assertEquals(DataHealthStatus.ERROR, report.status)
        assertTrue(report.issues.any { it.code == "LEDGER_REPLAY_FAILED" })
        assertTrue(report.issues.any { it.code == "INVALID_TARGETS" })
        assertTrue(report.issues.any { it.code == "MISSING_QUOTES" })
    }

    @Test
    fun `stale observations and future dates produce actionable warnings`() {
        val report = engine.inspect(input(
            transactions = listOf(
                transaction(1, TransactionType.CASH_DEPOSIT, today, cash = "1000"),
                transaction(2, TransactionType.BUY, today.plusDays(1), symbol = "FFC", quantity = "1", price = "100"),
            ),
            quotes = listOf(DataQualityQuote("FFC", BigDecimal("100"), now.minusSeconds(9 * 86_400))),
            indexes = validIndexes().map { it.copy(snapshotDate = today.minusDays(20)) },
        ))

        assertEquals(DataHealthStatus.ATTENTION, report.status)
        assertTrue(report.issues.any { it.code == "FUTURE_TRANSACTIONS" })
        assertTrue(report.issues.any { it.code == "STALE_QUOTES" })
        assertTrue(report.issues.any { it.code == "STALE_INDEX_CACHE" })
    }

    @Test
    fun `truncated index cache is rejected by the audit`() {
        val report = engine.inspect(input(indexes = listOf(
            IndexCacheQuality("KMI30", today, 3, BigDecimal("10")),
            IndexCacheQuality("KSE100", today, 100, BigDecimal("100")),
            IndexCacheQuality("KMIALLSHR", today, 200, BigDecimal("100")),
        )))

        assertEquals(DataHealthStatus.ERROR, report.status)
        assertTrue(report.issues.any { it.code == "INVALID_INDEX_CACHE" })
    }

    @Test
    fun `symbols and index codes are normalized before completeness checks`() {
        val report = engine.inspect(input(
            transactions = listOf(
                transaction(1, TransactionType.CASH_DEPOSIT, today, cash = "1000"),
                transaction(2, TransactionType.BUY, today, symbol = "FFC", quantity = "1", price = "100"),
            ),
            quotes = listOf(DataQualityQuote(" ffc ", BigDecimal("100"), now)),
            indexes = validIndexes().map { it.copy(indexCode = it.indexCode.lowercase()) },
        ))

        assertEquals(DataHealthStatus.HEALTHY, report.status)
        assertTrue(report.issues.isEmpty())
    }

    private fun input(
        transactions: List<PortfolioTransaction> = emptyList(),
        quotes: List<DataQualityQuote> = emptyList(),
        targets: List<DataQualityTarget> = emptyList(),
        indexes: List<IndexCacheQuality> = validIndexes(),
    ) = DataQualityInput(
        portfolioIds = setOf(1),
        transactions = transactions,
        quotes = quotes,
        targets = targets,
        watchlistSymbols = emptySet(),
        latestIndexes = indexes,
        checkedAt = now,
        checkedDate = today,
    )

    private fun validIndexes() = listOf(
        IndexCacheQuality("KMI30", today, 30, BigDecimal("100")),
        IndexCacheQuality("KSE100", today, 100, BigDecimal("100")),
        IndexCacheQuality("KMIALLSHR", today, 200, BigDecimal("100")),
    )

    private fun transaction(
        id: Long,
        type: TransactionType,
        date: LocalDate,
        symbol: String? = null,
        quantity: String = "0",
        price: String = "0",
        cash: String? = null,
    ) = PortfolioTransaction(
        id = id,
        portfolioId = 1,
        type = type,
        tradeDate = date,
        symbol = symbol,
        quantity = BigDecimal(quantity),
        price = BigDecimal(price),
        cashAmount = cash?.let(::BigDecimal),
    )
}
