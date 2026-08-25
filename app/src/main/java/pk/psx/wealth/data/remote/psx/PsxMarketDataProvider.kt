package pk.psx.wealth.data.remote.psx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.IndexConstituent
import pk.psx.wealth.domain.MarketDataProvider
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.ProviderResult
import pk.psx.wealth.domain.StockSnapshot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PsxMarketDataProvider @Inject constructor(
    private val client: OkHttpClient,
    private val indexParser: PsxIndexParser,
    private val companyParser: PsxCompanyParser,
    private val historyParser: PsxHistoryParser,
    private val symbols: SymbolNormalizer,
    private val clock: Clock,
) : MarketDataProvider {
    override val providerId: String = "01-psx-direct"
    private val baseUrl = "https://dps.psx.com.pk"

    override suspend fun fetchQuote(symbol: String): ProviderResult<MarketQuote> = result {
        val normalized = symbols.normalize(symbol)
        val url = "$baseUrl/company/$normalized"
        companyParser.parseQuote(normalized, get(url), Instant.now(clock), url)
    }

    override suspend fun fetchIndexConstituents(indexCode: String): ProviderResult<List<IndexConstituent>> = result {
        val normalized = indexCode.trim().uppercase()
        require(normalized in setOf("KMI30", "KSE100", "KMIALLSHR")) { "Unsupported PSX index: $indexCode" }
        val url = "$baseUrl/indices/$normalized"
        indexParser.parse(normalized, get(url), LocalDate.now(clock), url)
    }

    override suspend fun fetchHistoricalPrices(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
    ): ProviderResult<List<DailyPrice>> = result {
        require(!to.isBefore(from)) { "Historical end date precedes start date" }
        val normalized = symbols.normalize(symbol)
        val url = "$baseUrl/timeseries/eod/$normalized"
        historyParser.parse(normalized, get(url), Instant.now(clock), url).filter { it.date in from..to }
    }

    override suspend fun fetchStockSnapshot(symbol: String): ProviderResult<StockSnapshot> = result {
        val normalized = symbols.normalize(symbol)
        val url = "$baseUrl/company/$normalized"
        companyParser.parseSnapshot(normalized, get(url), Instant.now(clock), url)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/json;q=0.9")
            .header("User-Agent", "PSX-Wealth/0.1 personal-non-commercial explicit-refresh")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} from PSX")
            response.body?.string()?.takeIf(String::isNotBlank) ?: error("PSX returned an empty response")
        }
    }

    private suspend fun <T> result(block: suspend () -> T): ProviderResult<T> = try {
        ProviderResult.Success(block())
    } catch (error: Exception) {
        ProviderResult.Failure(error.message ?: "PSX data request failed", error)
    }
}
