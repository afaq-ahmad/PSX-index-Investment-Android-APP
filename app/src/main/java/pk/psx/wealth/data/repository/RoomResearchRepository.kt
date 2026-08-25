package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pk.psx.wealth.data.local.FundamentalDao
import pk.psx.wealth.data.local.FundamentalMetricEntity
import pk.psx.wealth.data.local.IndexDao
import pk.psx.wealth.data.local.PriceDao
import pk.psx.wealth.data.local.QuoteDao
import pk.psx.wealth.data.local.SecurityDao
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.WatchlistDao
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.data.local.WatchlistItemEntity
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.StockSnapshot
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomResearchRepository @Inject constructor(
    private val securities: SecurityDao,
    private val quotes: QuoteDao,
    private val prices: PriceDao,
    private val fundamentals: FundamentalDao,
    private val watchlists: WatchlistDao,
    private val indexes: IndexDao,
    private val clock: Clock,
) : ResearchRepository {
    override fun observeCatalog(): Flow<ResearchCatalog> {
        val market = combine(securities.observeAll(), quotes.observeAll(), fundamentals.observeAll()) { securityRows, quoteRows, metrics ->
            Triple(securityRows, quoteRows, metrics)
        }
        val lists = combine(watchlists.observeLists(), watchlists.observeAllItems()) { headers, items -> headers to items }
        val indexData = combine(indexes.observeAllSnapshots(), indexes.observeAllConstituents()) { headers, rows ->
            headers.map { header -> IndexResearchSnapshot(header, rows.filter { it.snapshotId == header.id }) }
        }
        return combine(market, lists, indexData) { (securityRows, quoteRows, metrics), (listRows, items), snapshots ->
            ResearchCatalog(securityRows, quoteRows, metrics, listRows, items, snapshots)
        }
    }

    override fun observeDailyPrices(): Flow<List<DailyPrice>> = prices.observeAll().map { rows ->
        rows.map { row ->
            DailyPrice(
                symbol = row.symbol,
                date = java.time.LocalDate.parse(row.date),
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

    override suspend fun createWatchlist(name: String): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Watchlist name is required" }
        return watchlists.upsertList(WatchlistEntity(name = clean))
    }

    override suspend fun deleteWatchlist(id: Long) = watchlists.deleteListById(id)

    override suspend fun addToWatchlist(watchlistId: Long, symbol: String, notes: String?) {
        val normalized = normalize(symbol)
        val securityId = ensureSecurity(normalized)
        watchlists.upsertItem(WatchlistItemEntity(watchlistId, normalized, securityId, notes = notes?.trim()?.takeIf(String::isNotEmpty)))
    }

    override suspend fun removeFromWatchlist(watchlistId: Long, symbol: String) =
        watchlists.removeItem(watchlistId, normalize(symbol))

    override suspend fun saveFundamental(draft: FundamentalDraft): Long {
        val symbol = normalize(draft.symbol)
        val code = draft.metricCode.trim().uppercase()
        require(code.matches(Regex("[A-Z0-9_]{1,40}"))) { "Metric code may contain letters, numbers and underscores" }
        require(draft.periodType.isNotBlank() && draft.unit.isNotBlank() && draft.source.isNotBlank())
        val existing = fundamentals.find(symbol, code, draft.periodEnd.toString())
        return fundamentals.upsert(FundamentalMetricEntity(
            id = existing?.id ?: 0,
            symbol = symbol,
            securityId = ensureSecurity(symbol),
            periodEnd = draft.periodEnd.toString(),
            periodType = draft.periodType.trim().uppercase(),
            metricCode = code,
            value = draft.value.toDouble(),
            unit = draft.unit.trim(),
            source = draft.source.trim(),
            retrievedAt = clock.millis(),
        ))
    }

    override suspend fun deleteFundamental(id: Long) = fundamentals.deleteById(id)

    override suspend fun recordSnapshot(snapshot: StockSnapshot) {
        val symbol = normalize(snapshot.symbol)
        val existing = securities.bySymbol(symbol)
        securities.upsert((existing ?: SecurityEntity(symbol = symbol, companyName = snapshot.companyName)).copy(
            companyName = snapshot.companyName.ifBlank { existing?.companyName ?: symbol },
            sector = snapshot.sector ?: existing?.sector,
            lastMetadataUpdate = clock.millis(),
        ))
        val date = snapshot.quote?.marketTime?.atZone(clock.zone)?.toLocalDate() ?: java.time.LocalDate.now(clock)
        listOfNotNull(
            snapshot.marketCap?.let { Triple("MARKET_CAP", it, "PKR") },
            snapshot.freeFloat?.let { Triple("FREE_FLOAT", it, "reported") },
            snapshot.peRatio?.let { Triple("PE", it, "ratio") },
            snapshot.dividendYield?.let { Triple("DIVIDEND_YIELD", it, "%") },
        ).forEach { (code, value, unit) ->
            saveFundamental(FundamentalDraft(symbol, date, "SNAPSHOT", code, value, unit,
                snapshot.quote?.source ?: "PSX company page"))
        }
    }

    private suspend fun ensureSecurity(symbol: String): Long {
        val existing = securities.bySymbol(symbol)
        return existing?.id ?: securities.upsert(SecurityEntity(symbol = symbol, companyName = symbol))
    }

    private fun normalize(symbol: String): String {
        val clean = symbol.trim().uppercase()
        require(clean.matches(Regex("[A-Z0-9-]{1,20}"))) { "Invalid PSX symbol" }
        return clean
    }
}
