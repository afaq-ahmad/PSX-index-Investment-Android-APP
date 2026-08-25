package pk.psx.wealth.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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
import pk.psx.wealth.data.remote.psx.IndexSnapshotValidator
import pk.psx.wealth.data.remote.psx.SymbolNormalizer
import pk.psx.wealth.domain.DailyPrice
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
        return when (val fetched = fetch { it.fetchIndexConstituents(normalized) }) {
            is ProviderResult.Success -> runCatching {
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
                recordSuccess("01-psx-direct", capability, fetched.value.size)
                RefreshItemResult(normalized, true, "Updated ${fetched.value.size} constituents", fetched.value.size)
            }.getOrElse { failure(capability, normalized, it) }
            is ProviderResult.Failure -> failure(capability, normalized, fetched.cause ?: IllegalStateException(fetched.message))
            is ProviderResult.Unsupported -> failure(capability, normalized, IllegalStateException(fetched.capability))
        }
    }

    override suspend fun refreshQuote(symbol: String): RefreshItemResult {
        val normalized = symbols.normalize(symbol)
        val capability = "QUOTE:$normalized"
        return when (val fetched = fetch { it.fetchQuote(normalized) }) {
            is ProviderResult.Success -> runCatching {
                saveQuote(fetched.value)
                recordSuccess("01-psx-direct", capability, 1)
                RefreshItemResult(normalized, true, "Quote updated", 1)
            }.getOrElse { failure(capability, normalized, it) }
            is ProviderResult.Failure -> failure(capability, normalized, fetched.cause ?: IllegalStateException(fetched.message))
            is ProviderResult.Unsupported -> failure(capability, normalized, IllegalStateException(fetched.capability))
        }
    }

    override suspend fun refreshHistory(symbol: String, from: LocalDate, to: LocalDate): RefreshItemResult {
        val normalized = symbols.normalize(symbol)
        val capability = "HISTORY:$normalized"
        return when (val fetched = fetch { it.fetchHistoricalPrices(normalized, from, to) }) {
            is ProviderResult.Success -> runCatching {
                require(fetched.value.isNotEmpty()) { "History response contained no valid prices" }
                val securityId = ensureSecurity(normalized, normalized)
                priceDao.upsertAll(fetched.value.map { it.toEntity(securityId) })
                recordSuccess("01-psx-direct", capability, fetched.value.size)
                RefreshItemResult(normalized, true, "Updated ${fetched.value.size} daily prices", fetched.value.size)
            }.getOrElse { failure(capability, normalized, it) }
            is ProviderResult.Failure -> failure(capability, normalized, fetched.cause ?: IllegalStateException(fetched.message))
            is ProviderResult.Unsupported -> failure(capability, normalized, IllegalStateException(fetched.capability))
        }
    }

    override suspend fun refreshStockSnapshot(symbol: String): Result<StockSnapshot> {
        val normalized = symbols.normalize(symbol)
        return when (val fetched = fetch { it.fetchStockSnapshot(normalized) }) {
            is ProviderResult.Success -> {
                fetched.value.quote?.let { saveQuote(it) }
                Result.success(fetched.value)
            }
            is ProviderResult.Failure -> Result.failure(fetched.cause ?: IllegalStateException(fetched.message))
            is ProviderResult.Unsupported -> Result.failure(UnsupportedOperationException(fetched.capability))
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

    private suspend fun <T> fetch(call: suspend (MarketDataProvider) -> ProviderResult<T>): ProviderResult<T> {
        var lastFailure: ProviderResult.Failure? = null
        for (provider in providers.ordered) {
            when (val result = call(provider)) {
                is ProviderResult.Success -> return result
                is ProviderResult.Failure -> lastFailure = result
                is ProviderResult.Unsupported -> Unit
            }
        }
        return lastFailure ?: ProviderResult.Failure("No provider supports this request")
    }

    private suspend fun recordSuccess(provider: String, capability: String, records: Int) {
        val now = clock.millis()
        diagnosticsDao.upsert(ProviderStatusEntity(provider.take(120), capability, now, now, null, records))
    }

    private suspend fun failure(capability: String, item: String, error: Throwable): RefreshItemResult {
        val message = (error.message ?: "Refresh failed").take(500)
        val previous = diagnosticsDao.get("01-psx-direct", capability)
        diagnosticsDao.upsert(ProviderStatusEntity(
            providerId = "01-psx-direct",
            capability = capability,
            lastAttemptAt = clock.millis(),
            lastSuccessAt = previous?.lastSuccessAt,
            lastError = message,
            cachedRecordCount = previous?.cachedRecordCount ?: 0,
        ))
        return RefreshItemResult(item, false, "$message. Cached data is unchanged.")
    }
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
