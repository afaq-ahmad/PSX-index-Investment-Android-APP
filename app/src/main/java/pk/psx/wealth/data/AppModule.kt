package pk.psx.wealth.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pk.psx.wealth.domain.PortfolioCalculator
import pk.psx.wealth.domain.RebalanceEngine
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): PsxDatabase =
        Room.databaseBuilder(context, PsxDatabase::class.java, "psx-wealth.db").build()
    @Provides fun portfolioDao(db: PsxDatabase) = db.portfolioDao()
    @Provides fun transactionDao(db: PsxDatabase) = db.transactionDao()
    @Provides fun marketDao(db: PsxDatabase) = db.marketDao()
    @Provides fun calculator() = PortfolioCalculator()
    @Provides fun rebalanceEngine() = RebalanceEngine()
}

