package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import pk.psx.wealth.data.local.FundamentalMetricEntity
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.local.IndexSnapshotEntity
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.data.local.WatchlistItemEntity
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.StockSnapshot
import java.math.BigDecimal
import java.time.LocalDate

data class IndexResearchSnapshot(
    val header: IndexSnapshotEntity,
    val constituents: List<IndexConstituentEntity>,
)

data class ResearchCatalog(
    val securities: List<SecurityEntity> = emptyList(),
    val quotes: List<LatestQuoteEntity> = emptyList(),
    val fundamentals: List<FundamentalMetricEntity> = emptyList(),
    val watchlists: List<WatchlistEntity> = emptyList(),
    val watchlistItems: List<WatchlistItemEntity> = emptyList(),
    val indexSnapshots: List<IndexResearchSnapshot> = emptyList(),
) {
    fun indexHistory(code: String): List<IndexResearchSnapshot> = indexSnapshots
        .filter { it.header.indexCode == code }
        .sortedWith(compareByDescending<IndexResearchSnapshot> { it.header.snapshotDate }.thenByDescending { it.header.retrievedAt })
}

data class FundamentalDraft(
    val symbol: String,
    val periodEnd: LocalDate,
    val periodType: String,
    val metricCode: String,
    val value: BigDecimal,
    val unit: String,
    val source: String = "Manual",
)

interface ResearchRepository {
    fun observeCatalog(): Flow<ResearchCatalog>
    fun observeDailyPrices(): Flow<List<DailyPrice>>
    suspend fun createWatchlist(name: String): Long
    suspend fun deleteWatchlist(id: Long)
    suspend fun addToWatchlist(watchlistId: Long, symbol: String, notes: String? = null)
    suspend fun removeFromWatchlist(watchlistId: Long, symbol: String)
    suspend fun saveFundamental(draft: FundamentalDraft): Long
    suspend fun deleteFundamental(id: Long)
    suspend fun recordSnapshot(snapshot: StockSnapshot)
}
