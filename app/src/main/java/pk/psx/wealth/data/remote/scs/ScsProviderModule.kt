package pk.psx.wealth.data.remote.scs

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import pk.psx.wealth.domain.MarketDataProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class ScsProviderModule {
    @Binds
    @IntoSet
    abstract fun bindScsProvider(provider: ScsMarketDataProvider): MarketDataProvider
}
