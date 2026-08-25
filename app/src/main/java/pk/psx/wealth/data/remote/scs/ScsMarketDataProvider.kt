package pk.psx.wealth.data.remote.scs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.psx.wealth.data.remote.psx.SymbolNormalizer
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.IndexConstituent
import pk.psx.wealth.domain.MarketDataCapability
import pk.psx.wealth.domain.MarketDataProvider
import pk.psx.wealth.domain.MarketProviderIds
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.ProviderResult
import pk.psx.wealth.domain.StockSnapshot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScsMarketDataProvider @Inject constructor(
    private val client: OkHttpClient,
    private val parser: ScsCompanyParser,
    private val symbols: SymbolNormalizer,
    private val clock: Clock,
) : MarketDataProvider {
    override val providerId: String = MarketProviderIds.SCS
    override val displayName: String = "SCS Trade"
    override val capabilities: Set<MarketDataCapability> = setOf(
        MarketDataCapability.QUOTE,
        MarketDataCapability.STOCK_SNAPSHOT,
    )

    private val snapshotUrl = "https://www.scstrade.com/stockscreening/SS_CompanySnapShot.aspx".toHttpUrl()

    override suspend fun fetchQuote(symbol: String): ProviderResult<MarketQuote> = fetch(symbol) { normalized, html, time, source ->
        parser.parseQuote(normalized, html, time, source)
    }

    override suspend fun fetchStockSnapshot(symbol: String): ProviderResult<StockSnapshot> =
        fetch(symbol) { normalized, html, time, source -> parser.parseSnapshot(normalized, html, time, source) }

    override suspend fun fetchIndexConstituents(indexCode: String): ProviderResult<List<IndexConstituent>> =
        ProviderResult.Unsupported("SCS Trade does not provide index constituent snapshots")

    override suspend fun fetchHistoricalPrices(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
    ): ProviderResult<List<DailyPrice>> = ProviderResult.Unsupported("SCS Trade history is not used by this app")

    private suspend fun <T> fetch(
        symbol: String,
        parse: (String, String, Instant, String) -> T,
    ): ProviderResult<T> {
        val normalized = runCatching { symbols.normalize(symbol) }
            .getOrElse { return ProviderResult.Failure(it.message ?: "Invalid symbol", it) }
        var lastError: Exception? = null
        for (candidate in listOf(normalized, normalized.lowercase(Locale.ROOT)).distinct()) {
            val url = snapshotUrl.newBuilder().addQueryParameter("symbol", candidate).build()
            try {
                val html = get(url.toString())
                return ProviderResult.Success(parse(normalized, html, Instant.now(clock), url.toString()))
            } catch (error: Exception) {
                lastError = error
            }
        }
        return ProviderResult.Failure(lastError?.message ?: "SCS Trade data request failed", lastError)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html")
            .header("User-Agent", "PSX-Wealth/0.1 personal-non-commercial explicit-refresh")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} from SCS Trade")
            response.body?.string()?.takeIf(String::isNotBlank) ?: error("SCS Trade returned an empty response")
        }
    }
}
