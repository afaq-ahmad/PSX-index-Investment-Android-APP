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
    @Query("SELECT * FROM daily_prices ORDER BY date, symbol")
    fun observeAll(): Flow<List<DailyPriceEntity>>
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
    @Query("SELECT * FROM index_snapshot_headers ORDER BY indexCode, snapshotDate DESC, retrievedAt DESC")
    fun observeAllSnapshots(): Flow<List<IndexSnapshotEntity>>
    @Query("SELECT * FROM index_constituents WHERE snapshotId = :snapshotId ORDER BY weightPercent DESC, symbol")
    fun observeConstituents(snapshotId: Long): Flow<List<IndexConstituentEntity>>
    @Query("SELECT * FROM index_constituents WHERE snapshotId = :snapshotId ORDER BY weightPercent DESC, symbol")
    suspend fun constituents(snapshotId: Long): List<IndexConstituentEntity>
    @Query("SELECT * FROM index_constituents ORDER BY snapshotId, weightPercent DESC, symbol")
    fun observeAllConstituents(): Flow<List<IndexConstituentEntity>>
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
    @Query("SELECT * FROM target_allocations WHERE portfolioId = :portfolioId ORDER BY symbol")
    suspend fun list(portfolioId: Long): List<TargetAllocationEntity>
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
    @Query("SELECT * FROM watchlist_items ORDER BY watchlistId, symbol")
    fun observeAllItems(): Flow<List<WatchlistItemEntity>>
    @Upsert suspend fun upsertList(entity: WatchlistEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(entity: WatchlistItemEntity)
    @Delete suspend fun deleteItem(entity: WatchlistItemEntity)
    @Delete suspend fun deleteList(entity: WatchlistEntity)
    @Query("DELETE FROM watchlists WHERE id = :id") suspend fun deleteListById(id: Long)
    @Query("DELETE FROM watchlist_items WHERE watchlistId = :watchlistId AND symbol = :symbol")
    suspend fun removeItem(watchlistId: Long, symbol: String)
    @Query("SELECT DISTINCT symbol FROM watchlist_items ORDER BY symbol") suspend fun allSymbols(): List<String>
}

@Dao
interface RebalanceDao {
    @Query("SELECT * FROM rebalance_plans WHERE portfolioId = :portfolioId ORDER BY createdAt DESC")
    fun observePlans(portfolioId: Long): Flow<List<RebalancePlanEntity>>
    @Query("SELECT * FROM rebalance_plans WHERE id = :id") suspend fun plan(id: Long): RebalancePlanEntity?
    @Query("SELECT * FROM rebalance_plans WHERE id = :id") fun observePlan(id: Long): Flow<RebalancePlanEntity?>
    @Query("SELECT * FROM rebalance_plan_items WHERE planId = :planId ORDER BY action, estimatedValue DESC")
    suspend fun items(planId: Long): List<RebalancePlanItemEntity>
    @Query("SELECT * FROM rebalance_plan_items WHERE planId = :planId ORDER BY action, estimatedValue DESC")
    fun observeItems(planId: Long): Flow<List<RebalancePlanItemEntity>>
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
    @Query("SELECT * FROM fundamental_metrics WHERE symbol = :symbol AND metricCode = :metricCode AND periodEnd = :periodEnd LIMIT 1")
    suspend fun find(symbol: String, metricCode: String, periodEnd: String): FundamentalMetricEntity?
    @Upsert suspend fun upsert(entity: FundamentalMetricEntity): Long
    @Query("DELETE FROM fundamental_metrics WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM provider_status ORDER BY providerId, capability")
    fun observeProviderStatus(): Flow<List<ProviderStatusEntity>>
    @Query("SELECT * FROM provider_status WHERE providerId = :providerId AND capability = :capability")
    suspend fun get(providerId: String, capability: String): ProviderStatusEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ProviderStatusEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT COUNT(*) FROM portfolios") suspend fun portfolioCount(): Int
    @Query("SELECT COUNT(*) FROM transactions") suspend fun transactionCount(): Int
    @Query("SELECT COUNT(*) FROM securities") suspend fun securityCount(): Int
    @Query("SELECT COUNT(*) FROM quotes") suspend fun quoteCount(): Int
    @Query("SELECT COUNT(*) FROM daily_prices") suspend fun priceCount(): Int
    @Query("SELECT COUNT(*) FROM index_snapshot_headers") suspend fun indexSnapshotCount(): Int
    @Query("SELECT COUNT(*) FROM fundamental_metrics") suspend fun fundamentalCount(): Int
    @Query("SELECT * FROM portfolios ORDER BY id") suspend fun portfolios(): List<PortfolioEntity>
    @Query("SELECT * FROM securities ORDER BY id") suspend fun securities(): List<SecurityEntity>
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM target_allocations ORDER BY portfolioId, symbol") suspend fun targets(): List<TargetAllocationEntity>
    @Query("SELECT * FROM watchlists ORDER BY id") suspend fun watchlists(): List<WatchlistEntity>
    @Query("SELECT * FROM watchlist_items ORDER BY watchlistId, symbol") suspend fun watchlistItems(): List<WatchlistItemEntity>
    @Query("SELECT * FROM fundamental_metrics ORDER BY id") suspend fun fundamentals(): List<FundamentalMetricEntity>
    @Query("SELECT * FROM rebalance_plans ORDER BY id") suspend fun rebalancePlans(): List<RebalancePlanEntity>
    @Query("SELECT * FROM rebalance_plan_items ORDER BY planId, symbol, action") suspend fun rebalanceItems(): List<RebalancePlanItemEntity>
    @Query("SELECT * FROM quotes ORDER BY symbol") suspend fun quotes(): List<LatestQuoteEntity>
    @Query("SELECT * FROM daily_prices ORDER BY symbol, date") suspend fun prices(): List<DailyPriceEntity>
    @Query("SELECT * FROM index_definitions ORDER BY code") suspend fun indexDefinitions(): List<IndexDefinitionEntity>
    @Query("SELECT * FROM index_snapshot_headers ORDER BY id") suspend fun indexSnapshots(): List<IndexSnapshotEntity>
    @Query("SELECT * FROM index_constituents ORDER BY snapshotId, symbol") suspend fun indexConstituents(): List<IndexConstituentEntity>

    @Query("DELETE FROM rebalance_plan_items") suspend fun clearRebalanceItems()
    @Query("DELETE FROM rebalance_plans") suspend fun clearRebalancePlans()
    @Query("DELETE FROM target_allocations") suspend fun clearTargets()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM watchlist_items") suspend fun clearWatchlistItems()
    @Query("DELETE FROM watchlists") suspend fun clearWatchlists()
    @Query("DELETE FROM fundamental_metrics") suspend fun clearFundamentals()
    @Query("DELETE FROM index_constituents") suspend fun clearIndexConstituents()
    @Query("DELETE FROM index_snapshot_headers") suspend fun clearIndexSnapshots()
    @Query("DELETE FROM index_snapshots") suspend fun clearLegacyIndexSnapshots()
    @Query("DELETE FROM quotes") suspend fun clearQuotes()
    @Query("DELETE FROM daily_prices") suspend fun clearPrices()
    @Query("DELETE FROM provider_status") suspend fun clearProviderStatus()
    @Query("DELETE FROM index_definitions") suspend fun clearIndexDefinitions()
    @Query("DELETE FROM securities") suspend fun clearSecurities()
    @Query("DELETE FROM portfolios") suspend fun clearPortfolios()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPortfolios(rows: List<PortfolioEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSecurities(rows: List<SecurityEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(rows: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTargets(rows: List<TargetAllocationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWatchlists(rows: List<WatchlistEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWatchlistItems(rows: List<WatchlistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFundamentals(rows: List<FundamentalMetricEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRebalancePlans(rows: List<RebalancePlanEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRebalanceItems(rows: List<RebalancePlanItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuotes(rows: List<LatestQuoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPrices(rows: List<DailyPriceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIndexDefinitions(rows: List<IndexDefinitionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIndexSnapshots(rows: List<IndexSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIndexConstituents(rows: List<IndexConstituentEntity>)
}
