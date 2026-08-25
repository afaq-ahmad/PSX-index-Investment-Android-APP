package pk.psx.wealth.domain

import java.time.LocalDate
import javax.inject.Inject

sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : ProviderResult<Nothing>
    data class Unsupported(val capability: String) : ProviderResult<Nothing>
}

enum class MarketDataCapability { QUOTE, INDEX_CONSTITUENTS, HISTORY, STOCK_SNAPSHOT }

enum class QuoteProviderPreference { PSX_FIRST, SCS_FIRST }

object MarketProviderIds {
    const val PSX = "01-psx-direct"
    const val SCS = "02-scs-trade"
}

data class MarketProviderConfiguration(
    val remoteMarketDataEnabled: Boolean = true,
    val psxProviderEnabled: Boolean = true,
    val scsQuoteFallbackEnabled: Boolean = true,
    val quoteProviderPreference: QuoteProviderPreference = QuoteProviderPreference.PSX_FIRST,
)

interface MarketDataProvider {
    val providerId: String
    val displayName: String
    val capabilities: Set<MarketDataCapability>
    suspend fun fetchQuote(symbol: String): ProviderResult<MarketQuote>
    suspend fun fetchIndexConstituents(indexCode: String): ProviderResult<List<IndexConstituent>>
    suspend fun fetchHistoricalPrices(symbol: String, from: LocalDate, to: LocalDate): ProviderResult<List<DailyPrice>>
    suspend fun fetchStockSnapshot(symbol: String): ProviderResult<StockSnapshot>
}

class MarketDataProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards MarketDataProvider>,
) {
    val available: List<MarketDataProvider> = providers.sortedBy(MarketDataProvider::providerId)

    fun candidates(
        capability: MarketDataCapability,
        configuration: MarketProviderConfiguration,
    ): List<MarketDataProvider> {
        if (!configuration.remoteMarketDataEnabled) return emptyList()
        val preferredIds = when (capability) {
            MarketDataCapability.QUOTE, MarketDataCapability.STOCK_SNAPSHOT -> when (configuration.quoteProviderPreference) {
                QuoteProviderPreference.PSX_FIRST -> listOf(MarketProviderIds.PSX, MarketProviderIds.SCS)
                QuoteProviderPreference.SCS_FIRST -> listOf(MarketProviderIds.SCS, MarketProviderIds.PSX)
            }
            MarketDataCapability.INDEX_CONSTITUENTS, MarketDataCapability.HISTORY -> listOf(MarketProviderIds.PSX)
        }
        val byId = available.associateBy(MarketDataProvider::providerId)
        val ordered = preferredIds.mapNotNull(byId::get) + available.filter { it.providerId !in preferredIds }
        return ordered.distinctBy(MarketDataProvider::providerId).filter { provider ->
            capability in provider.capabilities && when (provider.providerId) {
                MarketProviderIds.PSX -> configuration.psxProviderEnabled
                MarketProviderIds.SCS -> configuration.scsQuoteFallbackEnabled
                else -> true
            }
        }
    }
}
