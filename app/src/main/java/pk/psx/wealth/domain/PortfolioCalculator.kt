package pk.psx.wealth.domain

import kotlin.math.abs

class PortfolioCalculator {
    fun calculate(transactions: List<PortfolioTransaction>, prices: Map<String, Double>): PortfolioSnapshot {
        data class Position(var quantity: Double = 0.0, var cost: Double = 0.0)
        val positions = mutableMapOf<String, Position>()
        var cash = 0.0
        var realized = 0.0
        var dividends = 0.0

        transactions.sortedWith(compareBy<PortfolioTransaction> { it.date }.thenBy { it.id }).forEach { tx ->
            val value = tx.amount ?: tx.quantity * tx.price
            when (tx.type) {
                TransactionType.CASH_DEPOSIT -> cash += value
                TransactionType.CASH_WITHDRAWAL, TransactionType.FEE, TransactionType.TAX -> cash -= value
                TransactionType.DIVIDEND -> { cash += value; dividends += value }
                TransactionType.BUY, TransactionType.RIGHT_SHARES -> {
                    requireSymbol(tx)
                    positions.getOrPut(tx.symbol!!) { Position() }.apply { quantity += tx.quantity; cost += value }
                    cash -= value
                }
                TransactionType.SELL -> {
                    requireSymbol(tx)
                    val position = positions.getOrPut(tx.symbol!!) { Position() }
                    require(tx.quantity <= position.quantity + EPSILON) { "Cannot sell more ${tx.symbol} than owned" }
                    val averageCost = if (position.quantity == 0.0) 0.0 else position.cost / position.quantity
                    realized += value - tx.quantity * averageCost
                    position.quantity -= tx.quantity
                    position.cost -= tx.quantity * averageCost
                    cash += value
                }
                TransactionType.BONUS_SHARES -> { requireSymbol(tx); positions.getOrPut(tx.symbol!!) { Position() }.quantity += tx.quantity }
                TransactionType.SPLIT -> {
                    requireSymbol(tx); require(tx.quantity > 0) { "Split factor must be positive" }
                    positions.getOrPut(tx.symbol!!) { Position() }.quantity *= tx.quantity
                }
                TransactionType.ADJUSTMENT -> cash += value
            }
        }
        val holdings = positions.mapNotNull { (symbol, p) ->
            if (abs(p.quantity) < EPSILON) null else Holding(symbol, p.quantity, p.cost / p.quantity, prices[symbol] ?: 0.0)
        }.sortedBy { it.symbol }
        return PortfolioSnapshot(cash, holdings, realized, dividends)
    }

    private fun requireSymbol(tx: PortfolioTransaction) = require(!tx.symbol.isNullOrBlank()) { "${tx.type} requires a symbol" }
    private companion object { const val EPSILON = 0.000_001 }
}

