package pk.psx.wealth.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate

data class ChartPoint(val date: LocalDate, val value: BigDecimal, val comparison: BigDecimal? = null)

@Composable
fun LongTermLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text("No complete local history for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val values = points.flatMap { listOfNotNull(it.value.toDouble(), it.comparison?.toDouble()) }
    val low = values.minOrNull() ?: 0.0
    val high = values.maxOrNull() ?: low
    val range = (high - low).takeIf { it > 0 } ?: 1.0
    val description = "Line chart with ${points.size} observations from ${points.first().date} to ${points.last().date}; " +
        "value range ${low.toBigDecimal().stripTrailingZeros().toPlainString()} to ${high.toBigDecimal().stripTrailingZeros().toPlainString()}"
    Canvas(modifier.fillMaxWidth().height(190.dp).padding(vertical = 8.dp).semantics { contentDescription = description }) {
        fun x(index: Int) = if (points.size == 1) size.width / 2 else index * size.width / (points.size - 1)
        fun y(value: BigDecimal) = size.height - ((value.toDouble() - low) / range * size.height).toFloat()
        repeat(3) { step ->
            val gridY = size.height * step / 2f
            drawLine(
                color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = .22f),
                start = Offset(0f, gridY),
                end = Offset(size.width, gridY),
                strokeWidth = 1f,
            )
        }
        fun drawSeries(color: androidx.compose.ui.graphics.Color, value: (ChartPoint) -> BigDecimal?) {
            val path = Path()
            var started = false
            var hasPath = false
            points.forEachIndexed { index, point ->
                val amount = value(point)
                if (amount == null) {
                    started = false
                } else {
                    val offset = Offset(x(index), y(amount))
                    if (!started) {
                        path.moveTo(offset.x, offset.y)
                        started = true
                        hasPath = true
                    } else path.lineTo(offset.x, offset.y)
                }
            }
            if (hasPath) drawPath(path, color, style = Stroke(width = 4f))
        }
        drawSeries(primary) { it.value }
        drawSeries(secondary) { it.comparison }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.first().date.toString(), style = MaterialTheme.typography.labelSmall)
        Text("${quantity(low.toBigDecimal())} – ${quantity(high.toBigDecimal())}", style = MaterialTheme.typography.labelSmall)
        Text(points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AllocationComparisonBar(label: String, current: BigDecimal, target: BigDecimal) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        BarRow("Current", current, MaterialTheme.colorScheme.primary)
        BarRow("Target", target, MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun BarRow(label: String, value: BigDecimal, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(.18f))
        Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).height(10.dp).background(color))
        }
        Text(percentFraction(value), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun YearBarChart(values: Map<Int, BigDecimal>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) {
        Text("No dividend history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rows = values.toSortedMap().entries.toList()
    val maximum = rows.maxOf { it.value }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val slot = size.width / rows.size
        rows.forEachIndexed { index, row ->
            val height = row.value.divide(maximum, pk.psx.wealth.domain.MONEY_CONTEXT).toFloat() * size.height
            drawRect(color, topLeft = Offset(index * slot + slot * .18f, size.height - height),
                size = androidx.compose.ui.geometry.Size(slot * .64f, height))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        rows.forEach { Text(it.key.toString(), style = MaterialTheme.typography.labelSmall) }
    }
}
