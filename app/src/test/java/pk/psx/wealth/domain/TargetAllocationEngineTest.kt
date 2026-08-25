package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TargetAllocationEngineTest {
    private val engine = TargetAllocationEngine()

    @Test
    fun `equal weights leave explicit cash target`() {
        val targets = engine.equal(setOf("FFC", "MARI", "SYS"), BigDecimal("10"))
        assertEquals(0, BigDecimal("0.90").compareTo(targets.values.fold(ZERO, BigDecimal::add)))
        assertEquals(0, BigDecimal("0.30").compareTo(targets.getValue("FFC")))
    }

    @Test
    fun `selected index weights are renormalized`() {
        val targets = engine.index(
            mapOf("FFC" to BigDecimal("50"), "MARI" to BigDecimal("30"), "SYS" to BigDecimal("20")),
            selectedSymbols = setOf("MARI", "SYS"),
        )
        assertEquals(setOf("MARI", "SYS"), targets.keys)
        assertEquals(0, BigDecimal.ONE.compareTo(targets.values.fold(ZERO, BigDecimal::add)))
        assertEquals(0, BigDecimal("0.6").compareTo(targets.getValue("MARI")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom weights must match stock allocation`() {
        engine.custom(mapOf("FFC" to BigDecimal("50")), BigDecimal.ZERO)
    }
}
