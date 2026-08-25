package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.local.IndexSnapshotEntity
import pk.psx.wealth.data.local.ProviderStatusEntity
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.StockSnapshot
import java.time.LocalDate

data class StoredIndexSnapshot(
    val header: IndexSnapshotEntity,
    val constituents: List<IndexConstituentEntity>,
)

data class RefreshItemResult(
    val item: String,
    val success: Boolean,
    val message: String,
    val records: Int = 0,
)

interface MarketRepository {
    fun observeQuote(symbol: String): Flow<MarketQuote?>
    fun observeIndex(code: String): Flow<StoredIndexSnapshot?>
    fun observeHistory(symbol: String, from: LocalDate, to: LocalDate): Flow<List<DailyPrice>>
    fun observeDiagnostics(): Flow<List<ProviderStatusEntity>>
    suspend fun refreshIndex(code: String): RefreshItemResult
    suspend fun refreshQuote(symbol: String): RefreshItemResult
    suspend fun refreshHistory(symbol: String, from: LocalDate, to: LocalDate): RefreshItemResult
    suspend fun refreshStockSnapshot(symbol: String): Result<StockSnapshot>
}
