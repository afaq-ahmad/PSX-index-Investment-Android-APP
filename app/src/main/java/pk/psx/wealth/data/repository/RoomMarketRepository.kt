package pk.psx.wealth.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pk.psx.wealth.data.local.DailyPriceEntity
import pk.psx.wealth.data.local.DiagnosticsDao
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.local.IndexDao
import pk.psx.wealth.data.local.IndexSnapshotEntity
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.PriceDao
import pk.psx.wealth.data.local.ProviderStatusEntity
import pk.psx.wealth.data.local.PsxDatabase
import pk.psx.wealth.data.local.QuoteDao
import pk.psx.wealth.data.local.SecurityDao
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.toDomain
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.data.preferences.marketProviderConfiguration
import pk.psx.wealth.data.remote.psx.IndexSnapshotValidator
import pk.psx.wealth.data.remote.psx.SymbolNormalizer
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.MarketDataCapability
import pk.psx.wealth.domain.MarketDataProvider
import pk.psx.wealth.domain.MarketDataProviderRegistry
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.ProviderResult
import pk.psx.wealth.domain.StockSnapshot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMarketRepository @Inject constructor(
    private val db: PsxDatabase,
    private val securityDao: SecurityDao,
    private val quoteDao: QuoteDao,
    private val priceDao: PriceDao,
    private val indexDao: IndexDao,
    private val diagnosticsDao: DiagnosticsDao,
    private val providers: MarketDataProviderRegistry,
    private val validator: IndexSnapshotValidator,
    private val symbols: SymbolNormalizer,
    private val settingsRepository: AppSettingsRepository,
    private val clock: Clock,
) : MarketRepository {
    override fun observeQuote(symbol: String): Flow<MarketQuote?> =
        quoteDao.observe(symbols.normalize(symbol)).map { it?.toDomain() }

    override fun observeIndex(code: String): Flow<StoredIndexSnapshot?> =
        indexDao.observeLatestSnapshot(code.trim().uppercase()).flatMapLatest { header ->
            if (header == null) flowOf(null)
            else indexDao.observeConstituents(header.id).map { StoredIndexSnapshot(header, it) }
        }

    override fun observeHistory(symbol: String, from: LocalDate, to: LocalDate): Flow<List<DailyPrice>> =
        priceDao.observe(symbols.normalize(symbol), from.toString(), to.toString()).map { rows ->
            rows.map { row ->
                DailyPrice(
                    symbol = row.symbol,
                    date = LocalDate.parse(row.date),
                    open = row.open?.toBigDecimal(),
                    high = row.high?.toBigDecimal(),
                    low = row.low?.toBigDecimal(),
                    close = row.close.toBigDecimal(),
                    volume = row.volume,
                    adjusted = row.isAdjusted,
                    source = row.source,
                    retrievedAt = Instant.ofEpochMilli(row.retrievedAt),
                )
            }
        }

    override fun observeDiagnostics(): Flow<List<ProviderStatusEntity>> = diagnosticsDao.observeProviderStatus()

    override suspend fun refreshIndex(code: String): RefreshItemResult {
        val normalized = code.trim().uppercase()
        val capability = "INDEX:$normalized"
        return when (val fetched = fetch(capability, MarketDataCapability.INDEX_CONSTITUENTS) { it.fetchIndexConstituents(normalized) }) {
            is ProviderFetch.Success -> runCatching {
                validator.validate(normalized, fetched.value)
                val now = clock.millis()
                db.withTransaction {
                    val securityIds = fetched.value.associate { row ->
                        row.symbol to ensureSecurity(row.symbol, row.companyName)
                    }
                    val header = IndexSnapshotEntity(
                        indexCode = normalized,
                        snapshotDate = fetched.value.first().snapshotDate.toString(),
                        retrievedAt = now,
                        source = fetched.value.first().source,
                    )
                    indexDao.insertCompleteSnapshot(header, fetched.value.map { row ->
                        IndexConstituentEntity(
                            snapshotId = 0,
                            symbol = row.symbol,
                            companyName = row.companyName,
                            securityId = securityIds[row.symbol],
                            weightPercent = row.weightPercent?.toDouble(),
                            price = row.price?.toDouble(),
                            volume = row.volume,
                            freeFloat = row.freeFloat?.toDouble(),
                            marketCap = row.marketCap?.toDouble(),
                        )
                    })
                    val quotes = fetched.value.mapNotNull { row ->
                        row.price?.takeIf { it.signum() > 0 }?.let { price ->
                            LatestQuoteEntity(row.symbol, price.toDouble(), fetchedAt = now, source = row.source,
                                securityId = securityIds[row.symbol], volume = row.volume)
                        }
                    }
                    quoteDao.upsertAll(quotes)
                    priceDao.upsertAll(quotes.map { quote ->
                        DailyPriceEntity(quote.symbol, fetched.value.first().snapshotDate.toString(), quote.securityId,
                            close = quote.price, volume = quote.volume, source = quote.source, retrievedAt = now)
                    })
                }
                recordSuccess(fetched.provider.providerId, capability, fetched.value.size)
                RefreshItemResult(normalized, true,
                    "Updated ${fetched.value.size} constituents from ${fetched.provider.displayName}", fetched.value.size)
            }.getOrElse { failure(fetched.provider.providerId, capability, normalized, it) }
            is ProviderFetch.Failure -> RefreshItemResult(normalized, false, "${fetched.message}. Cached data is unchanged.")
        }
    }

    override suspend fun refreshQuote(symbol: String): RefreshItemResult {
        val normalized = symbols.normalize(symbol)
        val capability = "QUOTE:$normalized"
        return when (val fetched = fetch(capability, MarketDataCapability.QUOTE) { it.fetchQuote(normalized) }) {
            is ProviderFetch.Success -> runCatching {
                saveQuote(fetched.value)
                recordSuccess(fetched.provider.providerId, capability, 1)
                RefreshItemResult(normalized, true, "Quote updated from ${fetched.provider.displayName}", 1)
            }.getOrElse { failure(fetched.provider.providerId, capability, normalized, it) }
            is ProviderFetch.Failure -> RefreshItemResult(normalized, false, "${fetched.message}. Cached data is unchanged.")
        }
    }

    override suspend fun refreshHistory(symbol: String, from: LocalDate, to: LocalDate): RefreshItemResult {
        val normalized = symbols.normalize(symbol)
        val capability = "HISTORY:$normalized"
        return when (val fetched = fetch(capability, MarketDataCapability.HISTORY) { it.fetchHistoricalPrices(normalized, from, to) }) {
            is ProviderFetch.Success -> runCatching {
                require(fetched.value.isNotEmpty()) { "History response contained no valid prices" }
                val securityId = ensureSecurity(normalized, normalized)
                priceDao.upsertAll(fetched.value.map { it.toEntity(securityId) })
                recordSuccess(fetched.provider.providerId, capability, fetched.value.size)
                RefreshItemResult(normalized, true,
                    "Updated ${fetched.value.size} daily prices from ${fetched.provider.displayName}", fetched.value.size)
            }.getOrElse { failure(fetched.provider.providerId, capability, normalized, it) }
            is ProviderFetch.Failure -> RefreshItemResult(normalized, false, "${fetched.message}. Cached data is unchanged.")
        }
    }

    override suspend fun refreshStockSnapshot(symbol: String): Result<StockSnapshot> {
        val normalized = symbols.normalize(symbol)
        val capability = "SNAPSHOT:$normalized"
        return when (val fetched = fetch(capability, MarketDataCapability.STOCK_SNAPSHOT) { it.fetchStockSnapshot(normalized) }) {
            is ProviderFetch.Success -> runCatching {
                fetched.value.quote?.let { saveQuote(it) }
                recordSuccess(fetched.provider.providerId, capability, 1)
                fetched.value
            }.onFailure { recordFailure(fetched.provider.providerId, capability, it) }
            is ProviderFetch.Failure -> Result.failure(IllegalStateException(fetched.message))
        }
    }

    private suspend fun saveQuote(quote: MarketQuote) {
        require(quote.price.signum() > 0) { "Quote price must be positive" }
        val securityId = ensureSecurity(quote.symbol, quote.symbol)
        db.withTransaction {
            quoteDao.upsert(LatestQuoteEntity(
                symbol = quote.symbol,
                price = quote.price.toDouble(),
                change = quote.change?.toDouble(),
                fetchedAt = quote.retrievedAt.toEpochMilli(),
                source = quote.source,
                securityId = securityId,
                changePercent = quote.changePercent?.toDouble(),
                volume = quote.volume,
                marketTimestamp = quote.marketTime?.toEpochMilli(),
            ))
            val date = quote.marketTime?.atZone(clock.zone)?.toLocalDate() ?: LocalDate.now(clock)
            priceDao.upsertAll(listOf(DailyPriceEntity(quote.symbol, date.toString(), securityId, close = quote.price.toDouble(),
                volume = quote.volume, source = quote.source, retrievedAt = quote.retrievedAt.toEpochMilli())))
        }
    }

    private suspend fun ensureSecurity(symbol: String, companyName: String): Long {
        val existing = securityDao.bySymbol(symbol)
        if (existing != null) {
            securityDao.upsert(existing.copy(companyName = companyName.ifBlank { existing.companyName },
                lastMetadataUpdate = clock.millis()))
            return existing.id
        }
        return securityDao.upsert(SecurityEntity(symbol = symbol, companyName = companyName.ifBlank { symbol },
            lastMetadataUpdate = clock.millis()))
    }

    private suspend fun <T> fetch(
        capabilityName: String,
        capability: MarketDataCapability,
        call: suspend (MarketDataProvider) -> ProviderResult<T>,
    ): ProviderFetch<T> {
        val configuration = settingsRepository.settings.first().marketProviderConfiguration()
        val candidates = providers.candidates(capability, configuration)
        if (candidates.isEmpty()) {
            return ProviderFetch.Failure("No enabled provider supports ${capability.name.lowercase().replace('_', ' ')}")
        }
        val failures = mutableListOf<String>()
        for (provider in candidates) {
            when (val result = call(provider)) {
                is ProviderResult.Success -> return ProviderFetch.Success(provider, result.value)
                is ProviderResult.Failure -> {
                    val error = result.cause ?: IllegalStateException(result.message)
                    recordFailure(provider.providerId, capabilityName, error)
                    failures += "${provider.displayName}: ${sanitized(error)}"
                }
                is ProviderResult.Unsupported -> {
                    val error = UnsupportedOperationException(result.capability)
                    recordFailure(provider.providerId, capabilityName, error)
                    failures += "${provider.displayName}: unsupported"
                }
            }
        }
        return ProviderFetch.Failure("All enabled providers failed (${failures.joinToString("; ")})")
    }

    private suspend fun recordSuccess(provider: String, capability: String, records: Int) {
        val now = clock.millis()
        diagnosticsDao.upsert(ProviderStatusEntity(provider.take(120), capability, now, now, null, records))
    }

    private suspend fun recordFailure(providerId: String, capability: String, error: Throwable) {
        val message = sanitized(error)
        val previous = diagnosticsDao.get(providerId, capability)
        diagnosticsDao.upsert(ProviderStatusEntity(
            providerId = providerId.take(120),
            capability = capability,
            lastAttemptAt = clock.millis(),
            lastSuccessAt = previous?.lastSuccessAt,
            lastError = message,
            cachedRecordCount = previous?.cachedRecordCount ?: 0,
        ))
    }

    private suspend fun failure(providerId: String, capability: String, item: String, error: Throwable): RefreshItemResult {
        recordFailure(providerId, capability, error)
        val message = sanitized(error)
        return RefreshItemResult(item, false, "$message. Cached data is unchanged.")
    }

    private fun sanitized(error: Throwable): String = (error.message ?: "Refresh failed").take(500)
}

private sealed interface ProviderFetch<out T> {
    data class Success<T>(val provider: MarketDataProvider, val value: T) : ProviderFetch<T>
    data class Failure(val message: String) : ProviderFetch<Nothing>
}

private fun DailyPrice.toEntity(securityId: Long?) = DailyPriceEntity(
    symbol = symbol,
    date = date.toString(),
    securityId = securityId,
    open = open?.toDouble(),
    high = high?.toDouble(),
    low = low?.toDouble(),
    close = close.toDouble(),
    volume = volume,
    isAdjusted = adjusted,
    source = source,
    retrievedAt = retrievedAt.toEpochMilli(),
)
