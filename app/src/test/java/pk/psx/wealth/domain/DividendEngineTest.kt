package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DividendEngineTest {
    @Test
    fun `dividends remain income and use net cash`() {
        val transactions = listOf(
            PortfolioTransaction(portfolioId = 1, type = TransactionType.DIVIDEND, tradeDate = LocalDate.of(2026, 3, 1),
                symbol = "FFC", grossAmount = BigDecimal("100"), tax = BigDecimal("15")),
            PortfolioTransaction(portfolioId = 1, type = TransactionType.DIVIDEND, tradeDate = LocalDate.of(2025, 6, 1),
                symbol = "FFC", cashAmount = BigDecimal("50")),
        )
        val snapshot = PortfolioSnapshot(BigDecimal("135"), emptyList(), BigDecimal("100"), ZERO, ZERO, ZERO,
            BigDecimal("135"), BigDecimal("15"))
        val result = DividendEngine().summarize(transactions, snapshot, LocalDate.of(2026, 8, 25))
        assertEquals(0, BigDecimal("135").compareTo(result.total))
        assertEquals(0, BigDecimal("85").compareTo(result.thisYear))
        assertEquals(setOf(2025, 2026), result.byYear.keys)
    }
}
