package pk.psx.wealth.domain

import java.math.BigDecimal

/**
 * Deterministic weighted-average-cost accounting engine.
 *
 * Buy fees and taxes are capitalised into acquisition cost. Sell fees and taxes
 * reduce disposal proceeds. Standalone fee/tax transactions reduce cash.
 */
class PortfolioCalculator {
    fun calculate(
        transactions: List<PortfolioTransaction>,
        prices: Map<String, BigDecimal?>,
    ): PortfolioSnapshot {
        data class Position(
            var quantity: BigDecimal = ZERO,
            var remainingCost: BigDecimal = ZERO,
            var realized: BigDecimal = ZERO,
            var dividends: BigDecimal = ZERO,
            var feesAndTaxes: BigDecimal = ZERO,
        )

        val positions = mutableMapOf<String, Position>()
        var cash = ZERO
        var contributions = ZERO
        var buys = ZERO
        var sells = ZERO
        var realized = ZERO
        var dividends = ZERO
        var feesAndTaxes = ZERO

        transactions.sortedWith(compareBy<PortfolioTransaction> { it.tradeDate }.thenBy { it.id }).forEach { tx ->
            validate(tx)
            val amount = tx.cashAmount ?: tx.grossAmount ?: tx.quantity.multiply(tx.price)
            when (tx.type) {
                TransactionType.CASH_DEPOSIT -> {
                    cash = cash.add(amount)
                    contributions = contributions.add(amount)
                }
                TransactionType.CASH_WITHDRAWAL -> {
                    cash = cash.subtract(amount)
                    contributions = contributions.subtract(amount)
                }
                TransactionType.FEE, TransactionType.TAX -> {
                    cash = cash.subtract(amount)
                    feesAndTaxes = feesAndTaxes.add(amount)
                }
                TransactionType.DIVIDEND -> {
                    val net = tx.cashAmount ?: (tx.grossAmount ?: ZERO).subtract(tx.tax).subtract(tx.fees)
                    cash = cash.add(net)
                    dividends = dividends.add(net)
                    tx.symbol?.let { positions.getOrPut(it) { Position() }.dividends += net }
                    feesAndTaxes = feesAndTaxes.add(tx.tax).add(tx.fees)
                }
                TransactionType.BUY, TransactionType.RIGHT_SHARES -> {
                    val symbol = requireSymbol(tx)
                    val gross = tx.grossAmount ?: tx.quantity.multiply(tx.price)
                    val acquisitionCost = gross.add(tx.fees).add(tx.tax)
                    positions.getOrPut(symbol) { Position() }.apply {
                        quantity = quantity.add(tx.quantity)
                        remainingCost = remainingCost.add(acquisitionCost)
                        feesAndTaxes = feesAndTaxes.add(tx.fees).add(tx.tax)
                    }
                    cash = cash.subtract(acquisitionCost)
                    buys = buys.add(acquisitionCost)
                    feesAndTaxes = feesAndTaxes.add(tx.fees).add(tx.tax)
                }
                TransactionType.SELL -> {
                    val symbol = requireSymbol(tx)
                    val position = positions.getOrPut(symbol) { Position() }
                    require(tx.quantity <= position.quantity) { "Cannot sell more $symbol than owned" }
                    val averageCost = if (position.quantity.signum() == 0) ZERO else
                        position.remainingCost.divide(position.quantity, MONEY_CONTEXT)
                    val releasedCost = averageCost.multiply(tx.quantity)
                    val gross = tx.grossAmount ?: tx.quantity.multiply(tx.price)
                    val net = gross.subtract(tx.fees).subtract(tx.tax)
                    val gain = net.subtract(releasedCost)
                    position.quantity = position.quantity.subtract(tx.quantity)
                    position.remainingCost = position.remainingCost.subtract(releasedCost)
                    position.realized = position.realized.add(gain)
                    position.feesAndTaxes = position.feesAndTaxes.add(tx.fees).add(tx.tax)
                    cash = cash.add(net)
                    sells = sells.add(net)
                    realized = realized.add(gain)
                    feesAndTaxes = feesAndTaxes.add(tx.fees).add(tx.tax)
                }
                TransactionType.BONUS_SHARES -> {
                    val symbol = requireSymbol(tx)
                    positions.getOrPut(symbol) { Position() }.run { quantity = quantity.add(tx.quantity) }
                }
                TransactionType.SPLIT -> {
                    val symbol = requireSymbol(tx)
                    positions.getOrPut(symbol) { Position() }.run { quantity = quantity.multiply(tx.quantity) }
                }
                TransactionType.ADJUSTMENT -> cash = cash.add(amount)
            }
        }

        val holdings = positions.mapNotNull { (symbol, position) ->
            if (position.quantity.signum() == 0) null else Holding(
                symbol = symbol,
                quantity = position.quantity,
                remainingCost = position.remainingCost,
                averageCost = position.remainingCost.divide(position.quantity, MONEY_CONTEXT),
                marketPrice = prices[symbol],
                realizedProfit = position.realized,
                dividends = position.dividends,
                feesAndTaxes = position.feesAndTaxes,
            )
        }.sortedBy(Holding::symbol)

        return PortfolioSnapshot(
            cashBalance = cash,
            holdings = holdings,
            netContributions = contributions,
            totalBuys = buys,
            totalSells = sells,
            realizedProfit = realized,
            dividendIncome = dividends,
            feesAndTaxes = feesAndTaxes,
        )
    }

    fun validate(tx: PortfolioTransaction) {
        require(tx.fees.signum() >= 0 && tx.tax.signum() >= 0) { "Fees and tax cannot be negative" }
        when (tx.type) {
            TransactionType.BUY, TransactionType.SELL, TransactionType.RIGHT_SHARES -> {
                requireSymbol(tx)
                require(tx.quantity.signum() > 0) { "Quantity must be greater than zero" }
                require(tx.price.signum() >= 0) { "Price cannot be negative" }
            }
            TransactionType.BONUS_SHARES -> {
                requireSymbol(tx)
                require(tx.quantity.signum() > 0) { "Bonus quantity must be greater than zero" }
            }
            TransactionType.SPLIT -> {
                requireSymbol(tx)
                require(tx.quantity.signum() > 0) { "Split factor must be greater than zero" }
            }
            TransactionType.DIVIDEND -> requireSymbol(tx)
            else -> require((tx.cashAmount ?: tx.grossAmount ?: ZERO).signum() >= 0) { "Amount cannot be negative" }
        }
    }

    private fun requireSymbol(tx: PortfolioTransaction): String {
        val normalized = tx.symbol?.trim()?.uppercase()
        require(!normalized.isNullOrBlank()) { "${tx.type} requires a symbol" }
        return normalized
    }
}
