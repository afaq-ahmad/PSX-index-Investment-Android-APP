package pk.psx.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

enum class IndexGapAction { BUY, SELL, BALANCED, PRICE_REQUIRED }

data class IndexAllocationInput(
    val symbol: String,
    val companyName: String,
    val defaultWeightPercent: BigDecimal,
    val price: BigDecimal?,
    val ownedShares: BigDecimal = ZERO,
    val isIndexConstituent: Boolean = true,
)

data class IndexAllocationRow(
    val symbol: String,
    val companyName: String,
    /** The percentage published by the selected index source. */
    val defaultWeightPercent: BigDecimal,
    /** The normalized fraction used for calculations; e.g. 0.05 means 5%. */
    val targetWeight: BigDecimal,
    val price: BigDecimal?,
    val ownedShares: BigDecimal,
    val currentValue: BigDecimal?,
    val currentWeight: BigDecimal?,
    val targetValue: BigDecimal,
    val targetShares: BigDecimal?,
    val shareGap: BigDecimal?,
    val estimatedTradeValue: BigDecimal?,
    val action: IndexGapAction,
    val isIndexConstituent: Boolean,
)

data class IndexInvestmentPlan(
    val currentPortfolioValue: BigDecimal,
    val additionalFunds: BigDecimal,
    val targetCapital: BigDecimal,
    val publishedWeightTotal: BigDecimal,
    val estimatedBuyValue: BigDecimal,
    val estimatedSellValue: BigDecimal,
    val roundingCash: BigDecimal,
    val rows: List<IndexAllocationRow>,
    val warnings: List<String>,
) {
    val buyCount: Int get() = rows.count { it.action == IndexGapAction.BUY }
    val sellCount: Int get() = rows.count { it.action == IndexGapAction.SELL }
    val balancedCount: Int get() = rows.count { it.action == IndexGapAction.BALANCED }
}

/**
 * Converts published index percentages into an explainable whole-share plan.
 *
 * The engine never writes transactions. It deliberately keeps the published
 * percentage next to the normalized calculation weight so rounding or a source
 * total such as 99.99% cannot hide what the index actually reported.
 */
class IndexInvestmentEngine @Inject constructor() {
    fun plan(
        currentPortfolioValue: BigDecimal,
        additionalFunds: BigDecimal,
        rows: List<IndexAllocationInput>,
    ): IndexInvestmentPlan {
        require(currentPortfolioValue.signum() >= 0) { "Current portfolio value cannot be negative" }
        require(additionalFunds.signum() >= 0) { "New funds cannot be negative" }
        val targetCapital = currentPortfolioValue.add(additionalFunds)
        require(targetCapital.signum() > 0) { "Enter an investment amount greater than zero" }

        val cleaned = rows.map { row ->
            row.copy(
                symbol = row.symbol.trim().uppercase(),
                companyName = row.companyName.trim().ifBlank { row.symbol.trim().uppercase() },
            )
        }
        require(cleaned.none { it.symbol.isBlank() }) { "An index constituent has no symbol" }
        require(cleaned.map(IndexAllocationInput::symbol).distinct().size == cleaned.size) { "Allocation contains duplicate symbols" }
        require(cleaned.all { it.defaultWeightPercent.signum() >= 0 && it.ownedShares.signum() >= 0 }) {
            "Weights and owned shares cannot be negative"
        }

        val constituents = cleaned.filter { it.isIndexConstituent && it.defaultWeightPercent.signum() > 0 }
        require(constituents.isNotEmpty()) { "The selected index has no usable published weights" }
        val publishedTotal = constituents.map(IndexAllocationInput::defaultWeightPercent).fold(ZERO, BigDecimal::add)
        require(publishedTotal.signum() > 0) { "The selected index weights total zero" }

        val warnings = mutableListOf<String>()
        if (publishedTotal < BigDecimal("99.5") || publishedTotal > BigDecimal("100.5")) {
            warnings += "Published weights total ${publishedTotal.stripTrailingZeros().toPlainString()}%; calculations normalize them to 100%."
        }

        val resultRows = cleaned.map { row ->
            val targetWeight = if (row.isIndexConstituent && row.defaultWeightPercent.signum() > 0) {
                row.defaultWeightPercent.divide(publishedTotal, MONEY_CONTEXT)
            } else ZERO
            val targetValue = targetCapital.multiply(targetWeight, MONEY_CONTEXT)
            val usablePrice = row.price?.takeIf { it.signum() > 0 }
            val targetShares = when {
                targetWeight.signum() == 0 -> ZERO
                usablePrice == null -> null
                else -> targetValue.divide(usablePrice, 0, RoundingMode.FLOOR)
            }
            val shareGap = targetShares?.subtract(row.ownedShares)
            val currentValue = usablePrice?.multiply(row.ownedShares, MONEY_CONTEXT)
            val currentWeight = currentValue?.let { value ->
                if (currentPortfolioValue.signum() > 0) value.divide(currentPortfolioValue, MONEY_CONTEXT) else ZERO
            }
            val action = when {
                targetWeight.signum() > 0 && usablePrice == null -> IndexGapAction.PRICE_REQUIRED
                shareGap == null -> IndexGapAction.PRICE_REQUIRED
                shareGap.signum() > 0 -> IndexGapAction.BUY
                shareGap.signum() < 0 -> IndexGapAction.SELL
                else -> IndexGapAction.BALANCED
            }
            IndexAllocationRow(
                symbol = row.symbol,
                companyName = row.companyName,
                defaultWeightPercent = row.defaultWeightPercent,
                targetWeight = targetWeight,
                price = usablePrice,
                ownedShares = row.ownedShares,
                currentValue = currentValue,
                currentWeight = currentWeight,
                targetValue = targetValue,
                targetShares = targetShares,
                shareGap = shareGap,
                estimatedTradeValue = if (usablePrice == null || shareGap == null) null
                    else usablePrice.multiply(shareGap.abs(), MONEY_CONTEXT),
                action = action,
                isIndexConstituent = row.isIndexConstituent,
            )
        }.sortedWith(
            compareBy<IndexAllocationRow> { !it.isIndexConstituent }
                .thenByDescending { it.defaultWeightPercent }
                .thenBy { it.symbol },
        )

        val missingPrices = resultRows.filter { it.action == IndexGapAction.PRICE_REQUIRED }.map { it.symbol }
        if (missingPrices.isNotEmpty()) warnings +=
            "Missing usable prices for ${missingPrices.joinToString()}; their share gaps remain unavailable."

        val estimatedBuys = resultRows.filter { it.action == IndexGapAction.BUY }
            .mapNotNull(IndexAllocationRow::estimatedTradeValue).fold(ZERO, BigDecimal::add)
        val estimatedSells = resultRows.filter { it.action == IndexGapAction.SELL }
            .mapNotNull(IndexAllocationRow::estimatedTradeValue).fold(ZERO, BigDecimal::add)
        val wholeShareTargetValue = resultRows.filter(IndexAllocationRow::isIndexConstituent).mapNotNull { row ->
            if (row.price == null || row.targetShares == null) null else row.price.multiply(row.targetShares, MONEY_CONTEXT)
        }.fold(ZERO, BigDecimal::add)

        return IndexInvestmentPlan(
            currentPortfolioValue = currentPortfolioValue,
            additionalFunds = additionalFunds,
            targetCapital = targetCapital,
            publishedWeightTotal = publishedTotal,
            estimatedBuyValue = estimatedBuys,
            estimatedSellValue = estimatedSells,
            roundingCash = targetCapital.subtract(wholeShareTargetValue).coerceAtLeast(ZERO),
            rows = resultRows,
            warnings = warnings,
        )
    }
}
