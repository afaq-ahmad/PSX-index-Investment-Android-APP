package pk.psx.wealth.data.remote.scs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pk.psx.wealth.data.remote.psx.SymbolNormalizer
import java.math.BigDecimal
import java.time.Instant

class ScsParsersTest {
    private val parser = ScsCompanyParser(SymbolNormalizer())
    private val retrievedAt = Instant.parse("2026-08-25T12:00:00Z")

    @Test
    fun `quote parser reads the SCS snapshot ids used by spreadsheet imports`() {
        val quote = parser.parseQuote("ffc", resource("scs_company_sample.html"), retrievedAt, "fixture")

        assertEquals("FFC", quote.symbol)
        assertEquals(0, BigDecimal("547.74").compareTo(quote.price))
        assertEquals(0, BigDecimal("2.91").compareTo(requireNotNull(quote.change)))
        assertEquals(0, BigDecimal("0.53").compareTo(requireNotNull(quote.changePercent)))
        assertEquals(635125L, quote.volume)
        assertNull(quote.marketTime)
        assertEquals(retrievedAt, quote.retrievedAt)
    }

    @Test
    fun `snapshot parser keeps company sector and scaled market cap`() {
        val snapshot = parser.parseSnapshot("FFC", resource("scs_company_sample.html"), retrievedAt, "fixture")

        assertEquals("Fauji Fertilizer Company Ltd. Consolidated", snapshot.companyName)
        assertEquals("FERTILIZER", snapshot.sector)
        assertEquals(0, BigDecimal("788210000000").compareTo(requireNotNull(snapshot.marketCap)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing price is rejected instead of becoming zero`() {
        parser.parseQuote("FFC", "<html><body>No quote</body></html>", retrievedAt, "fixture")
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing fixture $name" }.readText()
}
