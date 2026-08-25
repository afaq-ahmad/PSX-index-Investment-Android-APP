package pk.psx.wealth.data.refresh

import pk.psx.wealth.data.local.TransactionDao
import pk.psx.wealth.data.local.WatchlistDao
import pk.psx.wealth.data.repository.MarketRepository
import pk.psx.wealth.data.repository.RefreshItemResult
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshSummary(val results: List<RefreshItemResult>) {
    val updated: Int = results.count(RefreshItemResult::success)
    val failed: Int = results.size - updated
    val message: String = if (failed == 0) "$updated updated" else
        "$updated updated, $failed failed. Last good cached data remains in use for failed items."
}

@Singleton
class RefreshCoordinator @Inject constructor(
    private val market: MarketRepository,
    private val transactions: TransactionDao,
    private val watchlists: WatchlistDao,
) {
    suspend fun refreshAll(): RefreshSummary {
        val results = mutableListOf<RefreshItemResult>()
        listOf("KMI30", "KSE100", "KMIALLSHR").forEach { results += market.refreshIndex(it) }
        val symbols = (transactions.distinctSymbols() + watchlists.allSymbols()).distinct().sorted().take(100)
        symbols.forEach { results += market.refreshQuote(it) }
        return RefreshSummary(results)
    }
}
