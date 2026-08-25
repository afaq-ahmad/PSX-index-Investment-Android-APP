package pk.psx.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class TradeAction { BUY, SELL, HOLD }

data class RebalanceRequest(
    val holdings: List<Holding>,
    val additionalCash: BigDecimal,
    val currentCash: BigDecimal,
    val targetWeights: Map<String, BigDecimal>,
    val minimumTrade: BigDecimal = ZERO,
    val cashReserve: BigDecimal = ZERO,
    val excludedSymbols: Set<String> = emptySet(),
    val noSellSymbols: Set<String> = emptySet(),
    val maximumWeights: Map<String, BigDecimal> = emptyMap(),
)

data class SuggestedTrade(
    val symbol: String,
    val action: TradeAction,
    val quantity: Long,
    val estimatedPrice: BigDecimal,
    val estimatedValue: BigDecimal,
    val currentWeight: BigDecimal,
    val targetWeight: BigDecimal,
    val projectedWeight: BigDecimal,
)

data class RebalanceResult(
    val trades: List<SuggestedTrade>,
    val cashBefore: BigDecimal,
    val cashAfter: BigDecimal,
    val driftBefore: BigDecimal,
    val driftAfter: BigDecimal,
    val warnings: List<String> = emptyList(),
)

class RebalanceEngine {
    fun cashOnly(request: RebalanceRequest): RebalanceResult = calculate(request, allowSelling = false)
    fun full(request: RebalanceRequest): RebalanceResult = calculate(request, allowSelling = true)

    private fun calculate(request: RebalanceRequest, allowSelling: Boolean): RebalanceResult {
        validate(request)
        val prices = request.holdings.associate { it.symbol to it.marketPrice }
        val missingPrices = request.targetWeights.keys.filter { prices[it] == null || prices[it]?.signum() != 1 }
        val warnings = missingPrices.map { "No usable price for $it" }.toMutableList()
        val values = request.holdings.associate { it.symbol to (it.marketValue ?: ZERO) }.toMutableMap()
        var available = request.currentCash.add(request.additionalCash).subtract(request.cashReserve).coerceAtLeast(ZERO)
        val initialCash = request.currentCash.add(request.additionalCash)
        val portfolioValue = values.values.fold(ZERO, BigDecimal::add).add(initialCash)
        val before = allocationDrift(values, portfolioValue, request.targetWeights)
        val quantities = mutableMapOf<Pair<String, TradeAction>, Long>()

        if (allowSelling) {
            request.targetWeights.keys.sorted().forEach { symbol ->
                if (symbol in request.excludedSymbols || symbol in request.noSellSymbols) return@forEach
                val price = prices[symbol] ?: return@forEach
                val targetValue = portfolioValue.multiply(request.targetWeights.getValue(symbol))
                val excess = values.getOrDefault(symbol, ZERO).subtract(targetValue)
                val shares = excess.divide(price, 0, RoundingMode.DOWN).toLong().coerceAtLeast(0)
                val value = price.multiply(shares.toBigDecimal())
                if (shares > 0 && value >= request.minimumTrade) {
                    quantities[symbol to TradeAction.SELL] = shares
                    values[symbol] = values.getOrDefault(symbol, ZERO).subtract(value)
                    available = available.add(value)
                }
            }
        }

        val ignored = mutableSetOf<String>()
        while (available.signum() > 0) {
            val candidate = request.targetWeights.keys
                .asSequence()
                .filterNot { it in request.excludedSymbols || it in ignored }
                .mapNotNull { symbol ->
                    val price = prices[symbol] ?: return@mapNotNull null
                    if (price.signum() <= 0) return@mapNotNull null
                    val cap = request.maximumWeights[symbol] ?: request.targetWeights.getValue(symbol)
                    val desired = portfolioValue.multiply(request.targetWeights.getValue(symbol).min(cap))
                    Triple(symbol, price, desired.subtract(values.getOrDefault(symbol, ZERO)))
                }
                .filter { it.third.signum() > 0 }
                .sortedWith(compareByDescending<Triple<String, BigDecimal, BigDecimal>> { it.third }.thenBy { it.first })
                .firstOrNull() ?: break
            val (symbol, price, deficit) = candidate
            val spendable = deficit.min(available)
            val shares = spendable.divide(price, 0, RoundingMode.DOWN).toLong()
            val value = price.multiply(shares.toBigDecimal())
            if (shares <= 0 || value < request.minimumTrade) {
                ignored += symbol
                continue
            }
            quantities[symbol to TradeAction.BUY] = quantities.getOrDefault(symbol to TradeAction.BUY, 0) + shares
            values[symbol] = values.getOrDefault(symbol, ZERO).add(value)
            available = available.subtract(value)
        }

        val finalTotal = values.values.fold(ZERO, BigDecimal::add).add(available).add(request.cashReserve)
        val after = allocationDrift(values, finalTotal, request.targetWeights)
        val currentValues = request.holdings.associate { it.symbol to (it.marketValue ?: ZERO) }
        val trades = quantities.map { (key, quantity) ->
            val (symbol, action) = key
            val price = requireNotNull(prices.getValue(symbol))
            SuggestedTrade(
                symbol = symbol,
                action = action,
                quantity = quantity,
                estimatedPrice = price,
                estimatedValue = price.multiply(quantity.toBigDecimal()),
                currentWeight = ratio(currentValues.getOrDefault(symbol, ZERO), portfolioValue),
                targetWeight = request.targetWeights.getValue(symbol),
                projectedWeight = ratio(values.getOrDefault(symbol, ZERO), finalTotal),
            )
        }.sortedWith(compareBy<SuggestedTrade> { it.action }.thenByDescending { it.estimatedValue }.thenBy { it.symbol })

        return RebalanceResult(trades, initialCash, available.add(request.cashReserve), before, after, warnings)
    }

    fun allocationDrift(
        currentValues: Map<String, BigDecimal>,
        totalValue: BigDecimal,
        targets: Map<String, BigDecimal>,
    ): BigDecimal = targets.keys.fold(ZERO) { total, symbol ->
        total.add(ratio(currentValues.getOrDefault(symbol, ZERO), totalValue).subtract(targets.getValue(symbol)).abs())
    }.divide(BigDecimal(2), MONEY_CONTEXT)

    private fun ratio(value: BigDecimal, total: BigDecimal): BigDecimal =
        if (total.signum() <= 0) ZERO else value.divide(total, MONEY_CONTEXT)

    private fun validate(request: RebalanceRequest) {
        require(request.additionalCash.signum() >= 0 && request.currentCash.signum() >= 0)
        require(request.cashReserve.signum() >= 0 && request.minimumTrade.signum() >= 0)
        require(request.targetWeights.isNotEmpty()) { "At least one target is required" }
        require(request.targetWeights.values.all { it.signum() >= 0 })
        require(request.targetWeights.values.fold(ZERO, BigDecimal::add) <= BigDecimal.ONE) {
            "Target weights cannot exceed 100%"
        }
    }
}
