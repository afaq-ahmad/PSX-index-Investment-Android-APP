package pk.psx.wealth.domain

import java.time.LocalDate
import javax.inject.Inject

sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : ProviderResult<Nothing>
    data class Unsupported(val capability: String) : ProviderResult<Nothing>
}

interface MarketDataProvider {
    val providerId: String
    suspend fun fetchQuote(symbol: String): ProviderResult<MarketQuote>
    suspend fun fetchIndexConstituents(indexCode: String): ProviderResult<List<IndexConstituent>>
    suspend fun fetchHistoricalPrices(symbol: String, from: LocalDate, to: LocalDate): ProviderResult<List<DailyPrice>>
    suspend fun fetchStockSnapshot(symbol: String): ProviderResult<StockSnapshot>
}

class MarketDataProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards MarketDataProvider>,
) {
    val ordered: List<MarketDataProvider> = providers.sortedBy(MarketDataProvider::providerId)
}
