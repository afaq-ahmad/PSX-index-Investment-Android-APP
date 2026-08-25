package pk.psx.wealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PortfolioEntity::class,
        SecurityEntity::class,
        TransactionEntity::class,
        LatestQuoteEntity::class,
        LegacyIndexSnapshotRowEntity::class,
        DailyPriceEntity::class,
        IndexDefinitionEntity::class,
        IndexSnapshotEntity::class,
        IndexConstituentEntity::class,
        TargetAllocationEntity::class,
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        RebalancePlanEntity::class,
        RebalancePlanItemEntity::class,
        FundamentalMetricEntity::class,
        ProviderStatusEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PsxDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun securityDao(): SecurityDao
    abstract fun transactionDao(): TransactionDao
    abstract fun quoteDao(): QuoteDao
    abstract fun priceDao(): PriceDao
    abstract fun indexDao(): IndexDao
    abstract fun targetAllocationDao(): TargetAllocationDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun rebalanceDao(): RebalanceDao
    abstract fun fundamentalDao(): FundamentalDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun backupDao(): BackupDao
}
