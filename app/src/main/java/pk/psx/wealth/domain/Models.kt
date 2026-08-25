package pk.psx.wealth.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate

val ZERO: BigDecimal = BigDecimal.ZERO
val MONEY_CONTEXT: MathContext = MathContext.DECIMAL128

enum class TransactionType {
    BUY, SELL, CASH_DEPOSIT, CASH_WITHDRAWAL, DIVIDEND, FEE, TAX,
    BONUS_SHARES, RIGHT_SHARES, SPLIT, ADJUSTMENT
}

data class PortfolioTransaction(
    val id: Long = 0,
    val portfolioId: Long,
    val type: TransactionType,
    val tradeDate: LocalDate,
    val symbol: String? = null,
    val quantity: BigDecimal = ZERO,
    val price: BigDecimal = ZERO,
    val grossAmount: BigDecimal? = null,
    val fees: BigDecimal = ZERO,
    val tax: BigDecimal = ZERO,
    val cashAmount: BigDecimal? = null,
    val notes: String? = null,
)

data class MarketQuote(
    val symbol: String,
    val price: BigDecimal,
    val change: BigDecimal? = null,
    val changePercent: BigDecimal? = null,
    val volume: Long? = null,
    val marketTime: Instant? = null,
    val retrievedAt: Instant,
    val source: String,
    val isManual: Boolean = false,
)

data class DailyPrice(
    val symbol: String,
    val date: LocalDate,
    val open: BigDecimal? = null,
    val high: BigDecimal? = null,
    val low: BigDecimal? = null,
    val close: BigDecimal,
    val volume: Long? = null,
    val adjusted: Boolean? = null,
    val source: String,
    val retrievedAt: Instant,
)

data class IndexConstituent(
    val indexCode: String,
    val symbol: String,
    val companyName: String,
    val weightPercent: BigDecimal?,
    val price: BigDecimal?,
    val volume: Long? = null,
    val freeFloat: BigDecimal? = null,
    val marketCap: BigDecimal? = null,
    val snapshotDate: LocalDate,
    val source: String,
)

data class StockSnapshot(
    val symbol: String,
    val companyName: String,
    val sector: String?,
    val quote: MarketQuote?,
    val marketCap: BigDecimal? = null,
    val freeFloat: BigDecimal? = null,
    val peRatio: BigDecimal? = null,
    val dividendYield: BigDecimal? = null,
)

data class Holding(
    val symbol: String,
    val quantity: BigDecimal,
    val remainingCost: BigDecimal,
    val averageCost: BigDecimal,
    val marketPrice: BigDecimal?,
    val realizedProfit: BigDecimal,
    val dividends: BigDecimal,
    val feesAndTaxes: BigDecimal,
) {
    val marketValue: BigDecimal? get() = marketPrice?.multiply(quantity)
    val unrealizedProfit: BigDecimal? get() = marketValue?.subtract(remainingCost)
    val totalProfit: BigDecimal? get() = unrealizedProfit?.add(realizedProfit)?.add(dividends)
}

data class PortfolioSnapshot(
    val cashBalance: BigDecimal,
    val holdings: List<Holding>,
    val netContributions: BigDecimal,
    val totalBuys: BigDecimal,
    val totalSells: BigDecimal,
    val realizedProfit: BigDecimal,
    val dividendIncome: BigDecimal,
    val feesAndTaxes: BigDecimal,
) {
    val stockMarketValue: BigDecimal = holdings.mapNotNull { it.marketValue }.fold(ZERO, BigDecimal::add)
    val hasCompletePrices: Boolean = holdings.all { it.marketPrice != null }
    val totalPortfolioValue: BigDecimal = cashBalance.add(stockMarketValue)
    val unrealizedProfit: BigDecimal? = if (hasCompletePrices) {
        holdings.mapNotNull { it.unrealizedProfit }.fold(ZERO, BigDecimal::add)
    } else null
    val totalProfit: BigDecimal? = if (hasCompletePrices) totalPortfolioValue.subtract(netContributions) else null

    fun portfolioWeight(symbol: String): BigDecimal? {
        if (totalPortfolioValue.signum() <= 0) return null
        return holdings.firstOrNull { it.symbol == symbol }?.marketValue?.divide(totalPortfolioValue, MONEY_CONTEXT)
    }

    fun investedWeight(symbol: String): BigDecimal? {
        if (stockMarketValue.signum() <= 0) return null
        return holdings.firstOrNull { it.symbol == symbol }?.marketValue?.divide(stockMarketValue, MONEY_CONTEXT)
    }
}

data class TargetAllocation(val symbol: String, val targetWeight: BigDecimal)
