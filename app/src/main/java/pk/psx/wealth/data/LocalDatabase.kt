package pk.psx.wealth.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.TransactionType
import java.time.LocalDate

@Entity(tableName = "portfolios")
data class PortfolioEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val benchmark: String = "KMI30")

@Entity(tableName = "transactions", indices = [Index("portfolioId"), Index("symbol")])
data class TransactionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val portfolioId: Long, val type: String, val date: String, val symbol: String?, val quantity: Double, val price: Double, val amount: Double?, val notes: String?)

@Entity(tableName = "quotes")
data class QuoteEntity(@PrimaryKey val symbol: String, val price: Double, val change: Double?, val fetchedAt: Long, val source: String)

@Entity(tableName = "index_snapshots", primaryKeys = ["indexCode", "symbol", "snapshotDate"])
data class IndexSnapshotEntity(val indexCode: String, val symbol: String, val companyName: String, val weight: Double, val price: Double?, val snapshotDate: String)

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios ORDER BY id") fun observePortfolios(): Flow<List<PortfolioEntity>>
    @Insert suspend fun insert(portfolio: PortfolioEntity): Long
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE portfolioId = :portfolioId ORDER BY date, id") fun observe(portfolioId: Long): Flow<List<TransactionEntity>>
    @Insert suspend fun insert(transaction: TransactionEntity): Long
}

@Dao
interface MarketDao {
    @Query("SELECT * FROM quotes") fun observeQuotes(): Flow<List<QuoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertQuotes(quotes: List<QuoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIndexSnapshot(rows: List<IndexSnapshotEntity>)
}

@Database(entities = [PortfolioEntity::class, TransactionEntity::class, QuoteEntity::class, IndexSnapshotEntity::class], version = 1, exportSchema = true)
abstract class PsxDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun transactionDao(): TransactionDao
    abstract fun marketDao(): MarketDao
}

fun TransactionEntity.toDomain() = PortfolioTransaction(id, portfolioId, TransactionType.valueOf(type), LocalDate.parse(date), symbol, quantity, price, amount, notes)

