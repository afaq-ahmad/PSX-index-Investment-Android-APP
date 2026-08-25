package pk.psx.wealth.domain

import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.abs

enum class ScoreComponent(val label: String) {
    VALUATION("Valuation"),
    QUALITY("Profitability / quality"),
    BALANCE_SHEET("Balance sheet"),
    GROWTH("Growth"),
    DIVIDEND("Dividend"),
    STABILITY("Stability"),
}

data class ScoreProfile(val name: String, val weights: Map<ScoreComponent, BigDecimal>)

data class ComponentScore(
    val component: ScoreComponent,
    val score: BigDecimal,
    val configuredWeight: BigDecimal,
    val appliedWeight: BigDecimal,
    val evidence: List<String>,
)

data class FundamentalScore(
    val score: BigDecimal?,
    val confidence: BigDecimal,
    val profile: String,
    val components: List<ComponentScore>,
    val missingComponents: List<ScoreComponent>,
)

class FundamentalScoreEngine @Inject constructor() {
    fun score(sector: String?, observations: Map<String, BigDecimal>): FundamentalScore {
        val metrics = observations.mapKeys { it.key.trim().uppercase() }
        val profile = profileFor(sector)
        val raw = ScoreComponent.entries.associateWith { component -> componentScore(component, metrics) }
        val availableWeight = raw.filterValues { it != null }.keys
            .map { profile.weights.getValue(it) }.fold(ZERO, BigDecimal::add)
        val components = raw.mapNotNull { (component, result) ->
            result?.let { (value, evidence) ->
                val configured = profile.weights.getValue(component)
                ComponentScore(
                    component = component,
                    score = BigDecimal.valueOf(value),
                    configuredWeight = configured,
                    appliedWeight = if (availableWeight.signum() == 0) ZERO else configured.divide(availableWeight, MONEY_CONTEXT),
                    evidence = evidence,
                )
            }
        }
        val finalScore = if (components.isEmpty()) null else components.fold(ZERO) { total, component ->
            total.add(component.score.multiply(component.appliedWeight, MONEY_CONTEXT))
        }
        return FundamentalScore(
            score = finalScore,
            confidence = availableWeight,
            profile = profile.name,
            components = components.sortedBy { it.component.ordinal },
            missingComponents = raw.filterValues { it == null }.keys.sortedBy(ScoreComponent::ordinal),
        )
    }

    fun profileFor(sector: String?): ScoreProfile {
        val value = sector.orEmpty().uppercase()
        return when {
            "BANK" in value -> profile("Banks", 25, 30, 20, 10, 10, 5)
            "FERTILIZER" in value -> profile("Fertilizer", 20, 25, 15, 10, 20, 10)
            "CEMENT" in value -> profile("Cement", 20, 20, 25, 15, 5, 15)
            "EXPLORATION" in value || "OIL & GAS" in value || "E&P" in value ->
                profile("Exploration & production", 15, 20, 20, 10, 20, 15)
            else -> profile("Generic", 25, 20, 20, 15, 10, 10)
        }
    }

    private fun profile(name: String, valuation: Int, quality: Int, balance: Int, growth: Int, dividend: Int, stability: Int) =
        ScoreProfile(name, mapOf(
            ScoreComponent.VALUATION to valuation.percent(),
            ScoreComponent.QUALITY to quality.percent(),
            ScoreComponent.BALANCE_SHEET to balance.percent(),
            ScoreComponent.GROWTH to growth.percent(),
            ScoreComponent.DIVIDEND to dividend.percent(),
            ScoreComponent.STABILITY to stability.percent(),
        ))

    private fun componentScore(
        component: ScoreComponent,
        metrics: Map<String, BigDecimal>,
    ): Pair<Double, List<String>>? {
        val evidence = mutableListOf<Pair<Double, String>>()
        fun add(code: String, score: (Double) -> Double, display: String = code) {
            metrics[code]?.toDouble()?.let { value -> evidence += score(value).clamp() to "$display ${metrics.getValue(code).stripTrailingZeros().toPlainString()}" }
        }
        when (component) {
            ScoreComponent.VALUATION -> {
                add("PE", ::lowerPositiveIsBetter, "P/E")
                add("PB", { lowerPositiveIsBetter(it, 1.2, 2.0, 3.5, 6.0) }, "P/B")
            }
            ScoreComponent.QUALITY -> {
                add("ROE", { higherPercent(it, 25, 18, 12, 7) })
                add("ROA", { higherPercent(it, 12, 8, 5, 2) })
                add("NET_MARGIN", { higherPercent(it, 25, 15, 8, 3) }, "Net margin")
            }
            ScoreComponent.BALANCE_SHEET -> {
                add("DEBT_EQUITY", { lowerIsBetter(it, .25, .75, 1.5, 3.0) }, "Debt/equity")
                add("CURRENT_RATIO", { higherRaw(it, 2.0, 1.5, 1.0, .7) }, "Current ratio")
            }
            ScoreComponent.GROWTH -> {
                add("EPS_GROWTH", { higherPercent(it, 20, 12, 5, 0) }, "EPS growth")
                add("REVENUE_GROWTH", { higherPercent(it, 20, 12, 5, 0) }, "Revenue growth")
                add("NET_PROFIT_GROWTH", { higherPercent(it, 20, 12, 5, 0) }, "Profit growth")
            }
            ScoreComponent.DIVIDEND -> {
                add("DIVIDEND_YIELD", { higherPercent(it, 10, 7, 4, 1) }, "Dividend yield")
                add("DIVIDEND_PAYOUT", { payoutScore(it) }, "Dividend payout")
            }
            ScoreComponent.STABILITY -> {
                add("OPERATING_MARGIN", { higherPercent(it, 25, 15, 8, 2) }, "Operating margin")
                add("OPERATING_CASH_FLOW", { if (it > 0) 80.0 else 20.0 }, "Operating cash flow")
                add("FREE_CASH_FLOW", { if (it > 0) 90.0 else 20.0 }, "Free cash flow")
            }
        }
        if (evidence.isEmpty()) return null
        return evidence.map { it.first }.average() to evidence.map { it.second }
    }

    private fun lowerPositiveIsBetter(value: Double, excellent: Double = 8.0, good: Double = 12.0, fair: Double = 18.0, weak: Double = 25.0) = when {
        value <= 0 -> 20.0
        value <= excellent -> 100.0
        value <= good -> 80.0
        value <= fair -> 60.0
        value <= weak -> 40.0
        else -> 20.0
    }

    private fun lowerIsBetter(value: Double, excellent: Double, good: Double, fair: Double, weak: Double) = when {
        value <= excellent -> 100.0
        value <= good -> 80.0
        value <= fair -> 60.0
        value <= weak -> 40.0
        else -> 20.0
    }

    private fun higherPercent(value: Double, excellent: Int, good: Int, fair: Int, weak: Int): Double {
        val percent = if (abs(value) <= 1.0) value * 100 else value
        return when {
            percent >= excellent -> 100.0
            percent >= good -> 80.0
            percent >= fair -> 60.0
            percent >= weak -> 40.0
            else -> 20.0
        }
    }

    private fun higherRaw(value: Double, excellent: Double, good: Double, fair: Double, weak: Double) = when {
        value >= excellent -> 100.0
        value >= good -> 80.0
        value >= fair -> 60.0
        value >= weak -> 40.0
        else -> 20.0
    }

    private fun payoutScore(value: Double): Double {
        val percent = if (abs(value) <= 1.0) value * 100 else value
        return when {
            percent in 25.0..70.0 -> 90.0
            percent in 10.0..<25.0 || percent in 70.0..90.0 -> 65.0
            percent > 100 || percent < 0 -> 20.0
            else -> 40.0
        }
    }

    private fun Double.clamp() = coerceIn(0.0, 100.0)
    private fun Int.percent() = BigDecimal(this).movePointLeft(2)
}
