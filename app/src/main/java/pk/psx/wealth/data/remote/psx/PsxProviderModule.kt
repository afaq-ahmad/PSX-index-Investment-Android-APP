package pk.psx.wealth.data.remote.psx

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import pk.psx.wealth.domain.MarketDataProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class PsxProviderModule {
    @Binds
    @IntoSet
    abstract fun bindPsxProvider(provider: PsxMarketDataProvider): MarketDataProvider
}
