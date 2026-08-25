package pk.psx.wealth.data.remote.psx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PsxParsersTest {
    private val symbols = SymbolNormalizer()

    @Test
    fun `index parser reads current PSX table layout and ignores XD tag`() {
        val rows = PsxIndexParser(symbols).parse("KMI30", resource("psx_index_sample.html"),
            LocalDate.of(2026, 8, 25), "fixture")
        assertEquals(listOf("FFC", "MEBL", "SYS"), rows.map { it.symbol })
        assertEquals(0, BigDecimal("547.74").compareTo(requireNotNull(rows.first().price)))
        assertEquals(635125L, rows.first().volume)
        assertEquals(0, BigDecimal("100").compareTo(rows.mapNotNull { it.weightPercent }.fold(BigDecimal.ZERO, BigDecimal::add)))
    }

    @Test
    fun `company parser keeps quote provenance and nullable metrics`() {
        val parser = PsxCompanyParser(symbols)
        val instant = Instant.parse("2026-08-25T12:00:00Z")
        val snapshot = parser.parseSnapshot("FFC", resource("psx_company_sample.html"), instant, "fixture")
        assertEquals("FFC", snapshot.symbol)
        assertEquals("FERTILIZER", snapshot.sector)
        assertEquals(0, BigDecimal("547.74").compareTo(requireNotNull(snapshot.quote).price))
        assertEquals(635125L, snapshot.quote?.volume)
        assertNotNull(snapshot.quote?.marketTime)
        assertEquals(0, BigDecimal("10.19").compareTo(requireNotNull(snapshot.peRatio)))
    }

    @Test
    fun `history parser creates sorted adjusted daily prices`() {
        val prices = PsxHistoryParser().parse("FFC", resource("psx_history_sample.json"), Instant.EPOCH, "fixture")
        assertEquals(2, prices.size)
        assertTrue(prices.zipWithNext().all { (a, b) -> a.date < b.date })
        assertEquals(true, prices.last().adjusted)
        assertEquals(635125L, prices.last().volume)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ambiguous symbol is rejected`() {
        symbols.normalize("FFC SOMETHING")
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing fixture $name" }.readText()
}
