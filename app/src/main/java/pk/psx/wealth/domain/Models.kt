package pk.psx.wealth.domain

import java.time.LocalDate
import java.time.Instant

enum class TransactionType { BUY, SELL, CASH_DEPOSIT, CASH_WITHDRAWAL, DIVIDEND, FEE, TAX, BONUS_SHARES, RIGHT_SHARES, SPLIT, ADJUSTMENT }

data class PortfolioTransaction(
    val id: Long = 0,
    val portfolioId: Long,
    val type: TransactionType,
    val date: LocalDate,
    val symbol: String? = null,
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val amount: Double? = null,
    val notes: String? = null,
)

data class Quote(val symbol: String, val price: Double, val change: Double? = null, val asOf: Instant)
data class DailyPrice(val symbol: String, val date: LocalDate, val close: Double)
data class IndexConstituent(val indexCode: String, val symbol: String, val companyName: String, val weight: Double, val price: Double?, val snapshotDate: LocalDate)
data class StockSnapshot(val symbol: String, val companyName: String, val sector: String?, val price: Double, val peRatio: Double?, val dividendYield: Double?, val asOf: Instant)
data class Holding(val symbol: String, val quantity: Double, val averageCost: Double, val currentPrice: Double) {
    val marketValue get() = quantity * currentPrice
    val unrealizedGain get() = marketValue - quantity * averageCost
}
data class PortfolioSnapshot(val cash: Double, val holdings: List<Holding>, val realizedGain: Double, val dividends: Double) {
    val marketValue get() = holdings.sumOf(Holding::marketValue)
    val totalValue get() = cash + marketValue
}

