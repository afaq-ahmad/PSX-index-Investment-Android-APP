package pk.psx.wealth.data.refresh

import kotlinx.coroutines.flow.first
import pk.psx.wealth.data.local.TransactionDao
import pk.psx.wealth.data.local.WatchlistDao
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.RefreshItemResult
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshSummary(val results: List<RefreshItemResult>) {
    val updated: Int = results.count(RefreshItemResult::success)
    val failed: Int = results.size - updated
    val message: String = when {
        results.isEmpty() -> "Online refresh is disabled or no refresh categories are selected. Cached and manual data remain available."
        failed == 0 -> "$updated updated"
        else -> "$updated updated, $failed failed. Last good cached data remains in use for failed items."
    }
}

@Singleton
class RefreshCoordinator @Inject constructor(
    private val market: MarketRepository,
    private val transactions: TransactionDao,
    private val watchlists: WatchlistDao,
    private val settingsRepository: AppSettingsRepository,
) {
    suspend fun refreshAll(): RefreshSummary {
        val settings = settingsRepository.settings.first()
        if (!settings.remoteMarketDataEnabled) return RefreshSummary(emptyList())
        val results = mutableListOf<RefreshItemResult>()
        if (settings.psxProviderEnabled) {
            buildList {
                if (settings.refreshKmi30) add("KMI30")
                if (settings.refreshKse100) add("KSE100")
                if (settings.refreshKmiAllShare) add("KMIALLSHR")
            }.forEach { results += market.refreshIndex(it) }
        }
        val quoteProviderEnabled = settings.psxProviderEnabled || settings.scsQuoteFallbackEnabled
        val held = if (quoteProviderEnabled && settings.refreshPortfolioQuotes) transactions.distinctSymbols() else emptyList()
        val watched = if (quoteProviderEnabled && settings.refreshWatchlistQuotes) watchlists.allSymbols() else emptyList()
        val symbols = (held + watched).distinct().sorted().take(100)
        symbols.forEach { results += market.refreshQuote(it) }
        return RefreshSummary(results)
    }
}
