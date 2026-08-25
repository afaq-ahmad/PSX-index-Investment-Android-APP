package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarketDataProviderRegistryTest {
    private val psx = FakeProvider(MarketProviderIds.PSX, MarketDataCapability.entries.toSet())
    private val scs = FakeProvider(
        MarketProviderIds.SCS,
        setOf(MarketDataCapability.QUOTE, MarketDataCapability.STOCK_SNAPSHOT),
    )
    private val registry = MarketDataProviderRegistry(setOf(scs, psx))

    @Test
    fun `PSX is the default quote provider and SCS is the fallback`() {
        assertEquals(
            listOf(MarketProviderIds.PSX, MarketProviderIds.SCS),
            registry.candidates(MarketDataCapability.QUOTE, MarketProviderConfiguration()).map { it.providerId },
        )
    }

    @Test
    fun `quote order and provider switches are honored`() {
        val configuration = MarketProviderConfiguration(
            psxProviderEnabled = true,
            scsQuoteFallbackEnabled = true,
            quoteProviderPreference = QuoteProviderPreference.SCS_FIRST,
        )
        assertEquals(
            listOf(MarketProviderIds.SCS, MarketProviderIds.PSX),
            registry.candidates(MarketDataCapability.QUOTE, configuration).map { it.providerId },
        )
        assertEquals(
            listOf(MarketProviderIds.PSX),
            registry.candidates(MarketDataCapability.QUOTE, configuration.copy(scsQuoteFallbackEnabled = false))
                .map { it.providerId },
        )
    }

    @Test
    fun `capability filtering prevents SCS from receiving index requests`() {
        assertEquals(
            listOf(MarketProviderIds.PSX),
            registry.candidates(MarketDataCapability.INDEX_CONSTITUENTS, MarketProviderConfiguration())
                .map { it.providerId },
        )
    }

    @Test
    fun `master online switch disables every remote candidate`() {
        assertTrue(
            registry.candidates(
                MarketDataCapability.QUOTE,
                MarketProviderConfiguration(remoteMarketDataEnabled = false),
            ).isEmpty(),
        )
    }
}

private class FakeProvider(
    override val providerId: String,
    override val capabilities: Set<MarketDataCapability>,
) : MarketDataProvider {
    override val displayName: String = providerId
    override suspend fun fetchQuote(symbol: String) = ProviderResult.Unsupported("fixture")
    override suspend fun fetchIndexConstituents(indexCode: String) = ProviderResult.Unsupported("fixture")
    override suspend fun fetchHistoricalPrices(symbol: String, from: LocalDate, to: LocalDate) =
        ProviderResult.Unsupported("fixture")
    override suspend fun fetchStockSnapshot(symbol: String) = ProviderResult.Unsupported("fixture")
}
