package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RebalanceEngineTest {
    private val engine = RebalanceEngine()

    @Test
    fun `cash only buys underweight stock and never sells`() {
        val result = engine.cashOnly(request(cash = "2000"))
        assertEquals(1, result.trades.size)
        assertEquals("SYS", result.trades.single().symbol)
        assertEquals(TradeAction.BUY, result.trades.single().action)
        assertEquals(20, result.trades.single().quantity)
        assertTrue(result.driftAfter < result.driftBefore)
    }

    @Test
    fun `cash smaller than one share produces no trade`() {
        assertTrue(engine.cashOnly(request(cash = "50")).trades.isEmpty())
    }

    @Test
    fun `minimum trade and exclusions are respected`() {
        val minimum = request(cash = "2000").copy(minimumTrade = BigDecimal("2500"))
        assertTrue(engine.cashOnly(minimum).trades.isEmpty())
        val excluded = request(cash = "2000").copy(excludedSymbols = setOf("SYS"))
        assertTrue(engine.cashOnly(excluded).trades.isEmpty())
    }

    @Test
    fun `missing target price is reported without fake trade`() {
        val request = request(cash = "2000").copy(targetWeights = mapOf("FFC" to BigDecimal("0.4"), "MARI" to BigDecimal("0.6")))
        val result = engine.cashOnly(request)
        assertTrue(result.warnings.any { "MARI" in it })
        assertTrue(result.trades.none { it.symbol == "MARI" })
    }

    @Test
    fun `full rebalance can sell overweight holding`() {
        val result = engine.full(request(cash = "0"))
        assertTrue(result.trades.any { it.symbol == "FFC" && it.action == TradeAction.SELL })
        assertTrue(result.trades.any { it.symbol == "SYS" && it.action == TradeAction.BUY })
    }

    private fun request(cash: String) = RebalanceRequest(
        holdings = listOf(
            holding("FFC", "80", "100"),
            holding("SYS", "20", "100"),
        ),
        additionalCash = BigDecimal(cash),
        currentCash = ZERO,
        targetWeights = mapOf("FFC" to BigDecimal("0.5"), "SYS" to BigDecimal("0.5")),
    )

    private fun holding(symbol: String, quantity: String, price: String) = Holding(
        symbol = symbol,
        quantity = BigDecimal(quantity),
        remainingCost = BigDecimal(quantity).multiply(BigDecimal(price)),
        averageCost = BigDecimal(price),
        marketPrice = BigDecimal(price),
        realizedProfit = ZERO,
        dividends = ZERO,
        feesAndTaxes = ZERO,
    )
}
