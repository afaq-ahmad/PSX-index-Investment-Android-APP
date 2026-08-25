package pk.psx.wealth.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios WHERE isArchived = 0 ORDER BY createdAt, id")
    fun observeActive(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios ORDER BY isArchived, createdAt, id")
    fun observeAll(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios WHERE id = :id")
    suspend fun get(id: Long): PortfolioEntity?

    @Upsert suspend fun upsert(entity: PortfolioEntity): Long
    @Delete suspend fun delete(entity: PortfolioEntity)
}

@Dao
interface SecurityDao {
    @Query("SELECT * FROM securities ORDER BY symbol") fun observeAll(): Flow<List<SecurityEntity>>
    @Query("SELECT * FROM securities WHERE symbol = :symbol LIMIT 1") suspend fun bySymbol(symbol: String): SecurityEntity?
    @Query("SELECT * FROM securities WHERE symbol LIKE :query OR companyName LIKE :query ORDER BY symbol LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<SecurityEntity>
    @Upsert suspend fun upsert(entity: SecurityEntity): Long
    @Query("SELECT COUNT(*) FROM securities") suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE portfolioId = :portfolioId ORDER BY date, id")
    fun observe(portfolioId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE portfolioId = :portfolioId ORDER BY date, id")
    suspend fun list(portfolioId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): TransactionEntity?
    @Upsert suspend fun upsert(entity: TransactionEntity): Long
    @Delete suspend fun delete(entity: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT COUNT(*) FROM transactions") suspend fun count(): Int
    @Query("SELECT DISTINCT symbol FROM transactions WHERE symbol IS NOT NULL AND symbol != '' ORDER BY symbol")
    suspend fun distinctSymbols(): List<String>
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY symbol") fun observeAll(): Flow<List<LatestQuoteEntity>>
    @Query("SELECT * FROM quotes WHERE symbol IN (:symbols) ORDER BY symbol") fun observe(symbols: List<String>): Flow<List<LatestQuoteEntity>>
    @Query("SELECT * FROM quotes WHERE symbol = :symbol") fun observe(symbol: String): Flow<LatestQuoteEntity?>
    @Query("SELECT * FROM quotes WHERE symbol = :symbol") suspend fun get(symbol: String): LatestQuoteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: LatestQuoteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entities: List<LatestQuoteEntity>)
    @Query("SELECT COUNT(*) FROM quotes") suspend fun count(): Int
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM daily_prices WHERE symbol = :symbol AND date BETWEEN :from AND :to ORDER BY date")
    fun observe(symbol: String, from: String, to: String): Flow<List<DailyPriceEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entities: List<DailyPriceEntity>)
    @Query("SELECT COUNT(*) FROM daily_prices") suspend fun count(): Int
}

@Dao
interface IndexDao {
    @Query("SELECT * FROM index_definitions ORDER BY code") fun observeDefinitions(): Flow<List<IndexDefinitionEntity>>
    @Query("SELECT * FROM index_snapshot_headers WHERE indexCode = :code ORDER BY snapshotDate DESC, retrievedAt DESC LIMIT 1")
    fun observeLatestSnapshot(code: String): Flow<IndexSnapshotEntity?>
    @Query("SELECT * FROM index_snapshot_headers WHERE indexCode = :code ORDER BY snapshotDate DESC, retrievedAt DESC LIMIT 1")
    suspend fun latestSnapshot(code: String): IndexSnapshotEntity?
    @Query("SELECT id FROM index_snapshot_headers WHERE indexCode = :code AND snapshotDate = :date LIMIT 1")
    suspend fun snapshotId(code: String, date: String): Long?
    @Query("SELECT * FROM index_snapshot_headers WHERE indexCode = :code ORDER BY snapshotDate DESC, retrievedAt DESC")
    suspend fun snapshots(code: String): List<IndexSnapshotEntity>
    @Query("SELECT * FROM index_constituents WHERE snapshotId = :snapshotId ORDER BY weightPercent DESC, symbol")
    fun observeConstituents(snapshotId: Long): Flow<List<IndexConstituentEntity>>
    @Query("SELECT * FROM index_constituents WHERE snapshotId = :snapshotId ORDER BY weightPercent DESC, symbol")
    suspend fun constituents(snapshotId: Long): List<IndexConstituentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDefinition(entity: IndexDefinitionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshot(entity: IndexSnapshotEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertConstituents(entities: List<IndexConstituentEntity>)
    @Query("DELETE FROM index_snapshot_headers WHERE id = :snapshotId") suspend fun deleteSnapshot(snapshotId: Long)
    @Query("SELECT COUNT(*) FROM index_snapshot_headers") suspend fun snapshotCount(): Int

    @Transaction
    suspend fun insertCompleteSnapshot(header: IndexSnapshotEntity, rows: List<IndexConstituentEntity>): Long {
        snapshotId(header.indexCode, header.snapshotDate)?.let { deleteSnapshot(it) }
        val id = insertSnapshot(header)
        insertConstituents(rows.map { it.copy(snapshotId = id) })
        return id
    }
}

@Dao
interface TargetAllocationDao {
    @Query("SELECT * FROM target_allocations WHERE portfolioId = :portfolioId ORDER BY symbol")
    fun observe(portfolioId: Long): Flow<List<TargetAllocationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entities: List<TargetAllocationEntity>)
    @Query("DELETE FROM target_allocations WHERE portfolioId = :portfolioId") suspend fun deleteForPortfolio(portfolioId: Long)

    @Transaction
    suspend fun replace(portfolioId: Long, entities: List<TargetAllocationEntity>) {
        deleteForPortfolio(portfolioId)
        upsertAll(entities)
    }
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlists ORDER BY name") fun observeLists(): Flow<List<WatchlistEntity>>
    @Query("SELECT * FROM watchlist_items WHERE watchlistId = :watchlistId ORDER BY symbol")
    fun observeItems(watchlistId: Long): Flow<List<WatchlistItemEntity>>
    @Upsert suspend fun upsertList(entity: WatchlistEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(entity: WatchlistItemEntity)
    @Delete suspend fun deleteItem(entity: WatchlistItemEntity)
    @Delete suspend fun deleteList(entity: WatchlistEntity)
    @Query("SELECT DISTINCT symbol FROM watchlist_items ORDER BY symbol") suspend fun allSymbols(): List<String>
}

@Dao
interface RebalanceDao {
    @Query("SELECT * FROM rebalance_plans WHERE portfolioId = :portfolioId ORDER BY createdAt DESC")
    fun observePlans(portfolioId: Long): Flow<List<RebalancePlanEntity>>
    @Query("SELECT * FROM rebalance_plans WHERE id = :id") suspend fun plan(id: Long): RebalancePlanEntity?
    @Query("SELECT * FROM rebalance_plan_items WHERE planId = :planId ORDER BY action, estimatedValue DESC")
    suspend fun items(planId: Long): List<RebalancePlanItemEntity>
    @Insert suspend fun insertPlan(entity: RebalancePlanEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(entities: List<RebalancePlanItemEntity>)
    @Query("UPDATE rebalance_plans SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)

    @Transaction
    suspend fun insertPlanWithItems(plan: RebalancePlanEntity, items: List<RebalancePlanItemEntity>): Long {
        val id = insertPlan(plan)
        insertItems(items.map { it.copy(planId = id) })
        return id
    }
}

@Dao
interface FundamentalDao {
    @Query("SELECT * FROM fundamental_metrics WHERE symbol = :symbol ORDER BY periodEnd DESC, metricCode")
    fun observe(symbol: String): Flow<List<FundamentalMetricEntity>>
    @Query("SELECT * FROM fundamental_metrics ORDER BY symbol, periodEnd DESC, metricCode")
    fun observeAll(): Flow<List<FundamentalMetricEntity>>
    @Upsert suspend fun upsert(entity: FundamentalMetricEntity): Long
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM provider_status ORDER BY providerId, capability")
    fun observeProviderStatus(): Flow<List<ProviderStatusEntity>>
    @Query("SELECT * FROM provider_status WHERE providerId = :providerId AND capability = :capability")
    suspend fun get(providerId: String, capability: String): ProviderStatusEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ProviderStatusEntity)
}
