package pk.psx.wealth.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val benchmark: String = "KMI30",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
)

@Entity(tableName = "securities", indices = [Index(value = ["symbol"], unique = true), Index("sector")])
data class SecurityEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val companyName: String,
    val sector: String? = null,
    val isActive: Boolean = true,
    val isShariahKnown: Boolean = false,
    val isShariahCompliant: Boolean? = null,
    val lastMetadataUpdate: Long? = null,
)

@Entity(tableName = "transactions", indices = [Index("portfolioId"), Index("symbol"), Index("securityId")])
data class TransactionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioId: Long,
    val type: String,
    val date: String,
    val symbol: String?,
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val amount: Double? = null,
    val notes: String? = null,
    val securityId: Long? = null,
    @ColumnInfo(defaultValue = "0") val fees: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val tax: Double = 0.0,
    val cashAmount: Double? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "quotes", indices = [Index("securityId")])
data class LatestQuoteEntity(
    @androidx.room.PrimaryKey val symbol: String,
    val price: Double,
    val change: Double? = null,
    val fetchedAt: Long,
    val source: String,
    val securityId: Long? = null,
    val changePercent: Double? = null,
    val volume: Long? = null,
    val marketTimestamp: Long? = null,
    @ColumnInfo(defaultValue = "0") val isManual: Boolean = false,
)

/** Retained only so databases created by version 1 can be migrated without data loss. */
@Entity(tableName = "index_snapshots", primaryKeys = ["indexCode", "symbol", "snapshotDate"])
data class LegacyIndexSnapshotRowEntity(
    val indexCode: String,
    val symbol: String,
    val companyName: String,
    val weight: Double,
    val price: Double?,
    val snapshotDate: String,
)

@Entity(tableName = "daily_prices", primaryKeys = ["symbol", "date"], indices = [Index("securityId")])
data class DailyPriceEntity(
    val symbol: String,
    val date: String,
    val securityId: Long? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double,
    val volume: Long? = null,
    val isAdjusted: Boolean? = null,
    val source: String,
    val retrievedAt: Long,
)

@Entity(tableName = "index_definitions")
data class IndexDefinitionEntity(
    @androidx.room.PrimaryKey val code: String,
    val name: String,
    val description: String? = null,
)

@Entity(tableName = "index_snapshot_headers", indices = [Index(value = ["indexCode", "snapshotDate"], unique = true)])
data class IndexSnapshotEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val indexCode: String,
    val snapshotDate: String,
    val retrievedAt: Long,
    val source: String,
)

@Entity(
    tableName = "index_constituents",
    primaryKeys = ["snapshotId", "symbol"],
    indices = [Index("symbol")],
    foreignKeys = [ForeignKey(
        entity = IndexSnapshotEntity::class,
        parentColumns = ["id"],
        childColumns = ["snapshotId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class IndexConstituentEntity(
    val snapshotId: Long,
    val symbol: String,
    val companyName: String,
    val securityId: Long? = null,
    val weightPercent: Double? = null,
    val price: Double? = null,
    val volume: Long? = null,
    val freeFloat: Double? = null,
    val marketCap: Double? = null,
)

@Entity(tableName = "target_allocations", primaryKeys = ["portfolioId", "symbol"], indices = [Index("securityId")])
data class TargetAllocationEntity(
    val portfolioId: Long,
    val symbol: String,
    val securityId: Long? = null,
    val targetPercent: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "watchlist_items",
    primaryKeys = ["watchlistId", "symbol"],
    indices = [Index("securityId")],
    foreignKeys = [ForeignKey(
        entity = WatchlistEntity::class,
        parentColumns = ["id"],
        childColumns = ["watchlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class WatchlistItemEntity(
    val watchlistId: Long,
    val symbol: String,
    val securityId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
)

@Entity(tableName = "rebalance_plans", indices = [Index("portfolioId")])
data class RebalancePlanEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioId: Long,
    val createdAt: Long,
    val strategyType: String,
    val newCash: Double,
    val cashReserve: Double,
    val allowSelling: Boolean,
    val minimumTrade: Double,
    val status: String,
    val driftBefore: Double? = null,
    val driftAfter: Double? = null,
    val remainingCash: Double? = null,
)

@Entity(
    tableName = "rebalance_plan_items",
    primaryKeys = ["planId", "symbol", "action"],
    foreignKeys = [ForeignKey(
        entity = RebalancePlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class RebalancePlanItemEntity(
    val planId: Long,
    val symbol: String,
    val action: String,
    val quantity: Long,
    val estimatedPrice: Double,
    val estimatedValue: Double,
    val currentWeight: Double,
    val targetWeight: Double,
    val projectedWeight: Double,
)

@Entity(tableName = "fundamental_metrics", indices = [Index("symbol"), Index(value = ["symbol", "metricCode", "periodEnd"])])
data class FundamentalMetricEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val securityId: Long? = null,
    val periodEnd: String,
    val periodType: String,
    val metricCode: String,
    val value: Double,
    val unit: String,
    val source: String,
    val retrievedAt: Long,
)

@Entity(tableName = "provider_status", primaryKeys = ["providerId", "capability"])
data class ProviderStatusEntity(
    val providerId: String,
    val capability: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val cachedRecordCount: Int = 0,
)
