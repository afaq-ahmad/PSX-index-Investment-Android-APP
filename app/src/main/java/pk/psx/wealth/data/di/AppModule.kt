package pk.psx.wealth.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import pk.psx.wealth.data.local.MIGRATION_1_2
import pk.psx.wealth.data.local.PsxDatabase
import pk.psx.wealth.data.local.seedReferenceData
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.data.repository.RoomPortfolioRepository
import pk.psx.wealth.domain.MarketDataProvider
import pk.psx.wealth.domain.PortfolioCalculator
import pk.psx.wealth.domain.RebalanceEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PsxDatabase =
        Room.databaseBuilder(context, PsxDatabase::class.java, "psx-wealth.db")
            .addMigrations(MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = seedReferenceData(db)
            })
            .build()

    @Provides fun portfolioDao(db: PsxDatabase) = db.portfolioDao()
    @Provides fun securityDao(db: PsxDatabase) = db.securityDao()
    @Provides fun transactionDao(db: PsxDatabase) = db.transactionDao()
    @Provides fun quoteDao(db: PsxDatabase) = db.quoteDao()
    @Provides fun priceDao(db: PsxDatabase) = db.priceDao()
    @Provides fun indexDao(db: PsxDatabase) = db.indexDao()
    @Provides fun targetAllocationDao(db: PsxDatabase) = db.targetAllocationDao()
    @Provides fun watchlistDao(db: PsxDatabase) = db.watchlistDao()
    @Provides fun rebalanceDao(db: PsxDatabase) = db.rebalanceDao()
    @Provides fun fundamentalDao(db: PsxDatabase) = db.fundamentalDao()
    @Provides fun diagnosticsDao(db: PsxDatabase) = db.diagnosticsDao()
    @Provides fun portfolioCalculator() = PortfolioCalculator()
    @Provides fun rebalanceEngine() = RebalanceEngine()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun portfolioRepository(implementation: RoomPortfolioRepository): PortfolioRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderSetModule {
    @Multibinds abstract fun providers(): Set<MarketDataProvider>
}
