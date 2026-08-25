package pk.psx.wealth.domain

import kotlin.math.floor

data class RebalanceRequest(val holdings: List<Holding>, val cashToInvest: Double, val targetWeights: Map<String, Double>, val minimumTrade: Double = 0.0, val cashReserve: Double = 0.0)
data class SuggestedTrade(val symbol: String, val quantity: Double, val estimatedValue: Double, val targetWeight: Double, val action: String = "BUY")

/** Cash-only optimizer: repeatedly funds the largest monetary deficit and never sells. */
class RebalanceEngine {
    fun cashOnly(request: RebalanceRequest): List<SuggestedTrade> {
        require(request.cashToInvest >= 0 && request.cashReserve >= 0)
        require(request.targetWeights.values.all { it >= 0.0 })
        require(request.targetWeights.values.sum() <= 1.000_001) { "Target weights cannot exceed 100%" }
        var available = (request.cashToInvest - request.cashReserve).coerceAtLeast(0.0)
        val current = request.holdings.associate { it.symbol to it.marketValue }.toMutableMap()
        val prices = request.holdings.associate { it.symbol to it.currentPrice }
        val finalValue = current.values.sum() + available
        val suggestions = mutableMapOf<String, Double>()

        while (available > 0.0) {
            val symbol = request.targetWeights.keys.maxByOrNull { key -> finalValue * request.targetWeights.getValue(key) - current.getOrDefault(key, 0.0) } ?: break
            val price = prices[symbol] ?: break // new symbols need a quote before planning
            if (price <= 0.0) break
            val deficit = (finalValue * request.targetWeights.getValue(symbol) - current.getOrDefault(symbol, 0.0)).coerceAtLeast(0.0)
            val quantity = floor(minOf(deficit, available) / price)
            if (quantity < 1.0) break
            val spend = quantity * price
            suggestions[symbol] = suggestions.getOrDefault(symbol, 0.0) + quantity
            current[symbol] = current.getOrDefault(symbol, 0.0) + spend
            available -= spend
        }
        return suggestions.mapNotNull { (symbol, quantity) ->
            val value = quantity * prices.getValue(symbol)
            if (value < request.minimumTrade) null else SuggestedTrade(symbol, quantity, value, request.targetWeights.getValue(symbol))
        }.sortedByDescending { it.estimatedValue }
    }
}

