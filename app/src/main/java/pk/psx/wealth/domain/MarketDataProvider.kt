package pk.psx.wealth.domain

import java.time.LocalDate

/** Keeps website-specific behavior out of portfolio and rebalancing calculations. */
interface MarketDataProvider {
    suspend fun getQuote(symbol: String): Quote
    suspend fun getIndexConstituents(indexCode: String): List<IndexConstituent>
    suspend fun getHistoricalPrices(symbol: String, from: LocalDate, to: LocalDate): List<DailyPrice>
    suspend fun getStockSnapshot(symbol: String): StockSnapshot
}

