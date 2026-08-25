package pk.psx.wealth.data.remote.psx

import pk.psx.wealth.domain.IndexConstituent
import java.math.BigDecimal
import javax.inject.Inject

class IndexSnapshotValidator @Inject constructor() {
    fun validate(code: String, rows: List<IndexConstituent>) {
        val range = when (code) {
            "KMI30" -> 25..35
            "KSE100" -> 85..115
            "KMIALLSHR" -> 80..500
            else -> 1..500
        }
        require(rows.size in range) { "$code returned ${rows.size} members; expected ${range.first}–${range.last}" }
        require(rows.none { it.symbol.isBlank() }) { "$code contains an empty symbol" }
        require(rows.map(IndexConstituent::symbol).distinct().size == rows.size) { "$code contains duplicate symbols" }
        require(rows.mapNotNull(IndexConstituent::price).all { it.signum() > 0 }) { "$code contains an invalid price" }
        val weights = rows.mapNotNull(IndexConstituent::weightPercent)
        if (weights.isNotEmpty()) {
            require(weights.all { it.signum() >= 0 }) { "$code contains a negative weight" }
            val sum = weights.fold(BigDecimal.ZERO, BigDecimal::add)
            require(sum >= BigDecimal("95") && sum <= BigDecimal("105")) {
                "$code weights total $sum%, outside the accepted rounding range"
            }
        }
    }
}
