package pk.psx.wealth.domain

import org.junit.Assert.*
import org.junit.Test

class RebalanceEngineTest {
    @Test fun `cash only plan buys underweight stock and never sells`() {
        val holdings = listOf(Holding("FFC", 80.0, 90.0, 100.0), Holding("SYS", 20.0, 100.0, 100.0))
        val trades = RebalanceEngine().cashOnly(RebalanceRequest(holdings, 2_000.0, mapOf("FFC" to .5, "SYS" to .5)))
        assertEquals(1, trades.size)
        assertEquals("SYS", trades.single().symbol)
        assertEquals(20.0, trades.single().quantity, 0.001)
        assertTrue(trades.all { it.action == "BUY" })
    }
}

