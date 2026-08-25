package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class IndexInvestmentEngineTest {
    private val engine = IndexInvestmentEngine()

    @Test
    fun `published percentages create whole share targets`() {
        val result = engine.plan(ZERO, BigDecimal("100000"), listOf(
            row("FFC", "60", "100"),
            row("MARI", "40", "200"),
        ))

        assertMoney("100000", result.targetCapital)
        assertMoney("600", result.rows.first { it.symbol == "FFC" }.targetShares)
        assertMoney("200", result.rows.first { it.symbol == "MARI" }.targetShares)
        assertEquals(2, result.buyCount)
        assertMoney("0", result.roundingCash)
    }

    @Test
    fun `owned shares produce clear buy sell and balanced signals`() {
        val result = engine.plan(BigDecimal("100000"), ZERO, listOf(
            row("FFC", "50", "100", "600"),
            row("MARI", "30", "100", "200"),
            row("SYS", "20", "100", "200"),
        ))

        assertEquals(IndexGapAction.SELL, result.rows.first { it.symbol == "FFC" }.action)
        assertMoney("-100", result.rows.first { it.symbol == "FFC" }.shareGap)
        assertEquals(IndexGapAction.BUY, result.rows.first { it.symbol == "MARI" }.action)
        assertMoney("100", result.rows.first { it.symbol == "MARI" }.shareGap)
        assertEquals(IndexGapAction.BALANCED, result.rows.first { it.symbol == "SYS" }.action)
    }

    @Test
    fun `new funds increase the capital used for every target`() {
        val result = engine.plan(BigDecimal("80000"), BigDecimal("20000"), listOf(
            row("FFC", "100", "250", "320"),
        ))

        assertMoney("100000", result.targetCapital)
        assertMoney("400", result.rows.single().targetShares)
        assertMoney("80", result.rows.single().shareGap)
    }

    @Test
    fun `holding outside the selected index is explicitly marked for sale`() {
        val result = engine.plan(BigDecimal("100000"), ZERO, listOf(
            row("FFC", "100", "100", "900"),
            row("OLD", "0", "100", "100", isIndex = false),
        ))

        val outside = result.rows.first { it.symbol == "OLD" }
        assertEquals(IndexGapAction.SELL, outside.action)
        assertMoney("0", outside.targetShares)
        assertMoney("-100", outside.shareGap)
        assertTrue(!outside.isIndexConstituent)
    }

    @Test
    fun `weight remains visible when price is missing`() {
        val result = engine.plan(ZERO, BigDecimal("50000"), listOf(
            IndexAllocationInput("FFC", "Fauji Fertilizer", BigDecimal("100"), null),
        ))

        val row = result.rows.single()
        assertMoney("100", row.defaultWeightPercent)
        assertEquals(IndexGapAction.PRICE_REQUIRED, row.action)
        assertEquals(null, row.targetShares)
        assertTrue(result.warnings.any { "FFC" in it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero capital is rejected`() {
        engine.plan(ZERO, ZERO, listOf(row("FFC", "100", "100")))
    }

    private fun row(
        symbol: String,
        weight: String,
        price: String,
        owned: String = "0",
        isIndex: Boolean = true,
    ) = IndexAllocationInput(
        symbol = symbol,
        companyName = symbol,
        defaultWeightPercent = BigDecimal(weight),
        price = BigDecimal(price),
        ownedShares = BigDecimal(owned),
        isIndexConstituent = isIndex,
    )

    private fun assertMoney(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }
}
