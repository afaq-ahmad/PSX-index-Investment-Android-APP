package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PortfolioCalculatorTest {
    private val day = LocalDate.of(2026, 1, 1)
    @Test fun `ledger derives cash average cost gains and dividends`() {
        val tx = listOf(
            PortfolioTransaction(1, 1, TransactionType.CASH_DEPOSIT, day, amount = 100_000.0),
            PortfolioTransaction(2, 1, TransactionType.BUY, day, "FFC", 100.0, 100.0),
            PortfolioTransaction(3, 1, TransactionType.BUY, day, "FFC", 100.0, 120.0),
            PortfolioTransaction(4, 1, TransactionType.SELL, day, "FFC", 50.0, 150.0),
            PortfolioTransaction(5, 1, TransactionType.DIVIDEND, day, "FFC", amount = 500.0),
        )
        val result = PortfolioCalculator().calculate(tx, mapOf("FFC" to 140.0))
        assertEquals(86_000.0, result.cash, 0.001)
        assertEquals(150.0, result.holdings.single().quantity, 0.001)
        assertEquals(110.0, result.holdings.single().averageCost, 0.001)
        assertEquals(2_000.0, result.realizedGain, 0.001)
        assertEquals(500.0, result.dividends, 0.001)
    }
}

