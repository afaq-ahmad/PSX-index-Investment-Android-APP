package pk.psx.wealth.domain

import java.math.BigDecimal

enum class TargetMode { CUSTOM, INDEX_WEIGHT, SELECTED_INDEX, EQUAL_WEIGHT }

class TargetAllocationEngine {
    fun custom(percentages: Map<String, BigDecimal>, cashTargetPercent: BigDecimal = ZERO): Map<String, BigDecimal> {
        validateCash(cashTargetPercent)
        val clean = percentages.cleanSymbols().mapValues { it.value.movePointLeft(2) }
        val expected = BigDecimal(100).subtract(cashTargetPercent).movePointLeft(2)
        require(closeTo(clean.values.fold(ZERO, BigDecimal::add), expected)) {
            "Stock targets must total ${expected.movePointRight(2).stripTrailingZeros().toPlainString()}%"
        }
        return clean
    }

    fun index(
        weightsPercent: Map<String, BigDecimal>,
        cashTargetPercent: BigDecimal = ZERO,
        selectedSymbols: Set<String>? = null,
    ): Map<String, BigDecimal> {
        validateCash(cashTargetPercent)
        val selected = weightsPercent.cleanSymbols().filterKeys { selectedSymbols == null || it in selectedSymbols }
        require(selected.isNotEmpty()) { "Select at least one index constituent" }
        require(selected.values.all { it.signum() >= 0 }) { "Index weights cannot be negative" }
        val sum = selected.values.fold(ZERO, BigDecimal::add)
        require(sum.signum() > 0) { "Selected index weights total zero" }
        val stockShare = BigDecimal.ONE.subtract(cashTargetPercent.movePointLeft(2))
        return selected.mapValues { (_, weight) -> weight.divide(sum, MONEY_CONTEXT).multiply(stockShare) }
    }

    fun equal(symbols: Set<String>, cashTargetPercent: BigDecimal = ZERO): Map<String, BigDecimal> {
        validateCash(cashTargetPercent)
        val clean = symbols.map(String::trim).map(String::uppercase).filter(String::isNotBlank).toSortedSet()
        require(clean.isNotEmpty()) { "Select at least one security" }
        val stockShare = BigDecimal.ONE.subtract(cashTargetPercent.movePointLeft(2))
        val each = stockShare.divide(clean.size.toBigDecimal(), MONEY_CONTEXT)
        return clean.associateWith { each }
    }

    private fun Map<String, BigDecimal>.cleanSymbols() = entries.associate { (symbol, value) ->
        val clean = symbol.trim().uppercase()
        require(clean.isNotEmpty()) { "Target symbol is empty" }
        require(value.signum() >= 0) { "$clean target cannot be negative" }
        clean to value
    }.filterValues { it.signum() > 0 }

    private fun validateCash(value: BigDecimal) {
        require(value >= ZERO && value < BigDecimal(100)) { "Cash target must be from 0% to less than 100%" }
    }

    private fun closeTo(left: BigDecimal, right: BigDecimal) =
        left.subtract(right).abs() <= BigDecimal("0.000001")
}
