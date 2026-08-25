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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pk.psx.wealth.domain.ProfitLossPoint
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

data class ChartPoint(val date: LocalDate, val value: BigDecimal, val comparison: BigDecimal? = null)

data class DatedValue(val date: LocalDate, val value: BigDecimal)
data class DatedLineSeries(val label: String, val color: Color, val points: List<DatedValue>)
data class AllocationChartSlice(
    val label: String,
    val value: BigDecimal,
    val weight: BigDecimal,
    val profit: BigDecimal? = null,
)
data class ProfitBarRow(val label: String, val value: BigDecimal, val returnFraction: BigDecimal? = null)

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
fun MultiSeriesLineChart(series: List<DatedLineSeries>, modifier: Modifier = Modifier) {
    val available = series.map { it.copy(points = it.points.sortedBy(DatedValue::date)) }.filter { it.points.isNotEmpty() }
    if (available.isEmpty()) {
        Text("No comparable local benchmark history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val allPoints = available.flatMap(DatedLineSeries::points)
    val firstDay = allPoints.minOf { it.date.toEpochDay() }
    val lastDay = allPoints.maxOf { it.date.toEpochDay() }
    val low = allPoints.minOf { it.value.toDouble() }
    val high = allPoints.maxOf { it.value.toDouble() }
    val dayRange = (lastDay - firstDay).takeIf { it > 0 } ?: 1L
    val valueRange = (high - low).takeIf { it > 0 } ?: 1.0
    val description = "Comparison chart from ${LocalDate.ofEpochDay(firstDay)} to ${LocalDate.ofEpochDay(lastDay)} " +
        "for ${available.joinToString { it.label }}"
    Canvas(modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp).semantics { contentDescription = description }) {
        repeat(3) { step ->
            val gridY = size.height * step / 2f
            drawLine(
                color = Color.Gray.copy(alpha = .22f),
                start = Offset(0f, gridY),
                end = Offset(size.width, gridY),
                strokeWidth = 1f,
            )
        }
        available.forEach { item ->
            val path = Path()
            item.points.forEachIndexed { index, point ->
                val x = ((point.date.toEpochDay() - firstDay).toDouble() / dayRange * size.width).toFloat()
                val y = size.height - ((point.value.toDouble() - low) / valueRange * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, item.color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(LocalDate.ofEpochDay(firstDay).toString(), style = MaterialTheme.typography.labelSmall)
        Text("${quantity(BigDecimal.valueOf(low))} – ${quantity(BigDecimal.valueOf(high))}", style = MaterialTheme.typography.labelSmall)
        Text(LocalDate.ofEpochDay(lastDay).toString(), style = MaterialTheme.typography.labelSmall)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        available.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(item.color))
                Text(item.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun AllocationDonutChart(slices: List<AllocationChartSlice>, modifier: Modifier = Modifier) {
    val positive = slices.filter { it.weight.signum() > 0 }.sortedByDescending(AllocationChartSlice::weight)
    if (positive.isEmpty()) {
        Text("No completely valued allocation is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFFD5A521),
        Color(0xFF2F6DAE),
        Color(0xFF8B5FBF),
        Color(0xFF2A9D8F),
        Color(0xFFE07A5F),
        Color(0xFF607D8B),
        Color(0xFF9A6B4A),
    )
    val visible = if (positive.size <= 8) positive else {
        val leading = positive.take(7)
        val remainder = positive.drop(7)
        leading + AllocationChartSlice(
            label = "Other (${remainder.size})",
            value = remainder.map(AllocationChartSlice::value).fold(BigDecimal.ZERO, BigDecimal::add),
            weight = remainder.map(AllocationChartSlice::weight).fold(BigDecimal.ZERO, BigDecimal::add),
            profit = remainder.mapNotNull(AllocationChartSlice::profit).fold(BigDecimal.ZERO, BigDecimal::add),
        )
    }
    val totalWeight = visible.map(AllocationChartSlice::weight).fold(BigDecimal.ZERO, BigDecimal::add)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier.size(210.dp).semantics {
                contentDescription = "Allocation doughnut chart: ${positive.joinToString { "${it.label} ${percentFraction(it.weight)}" }}"
            },
        ) {
            var startAngle = -90f
            val stroke = size.minDimension * .23f
            val inset = stroke / 2f
            visible.forEachIndexed { index, slice ->
                val sweep = slice.weight.divide(totalWeight, pk.psx.wealth.domain.MONEY_CONTEXT).toFloat() * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEachIndexed { index, slice ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(palette[index % palette.size]))
                    Text(slice.label, Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Medium)
                    Text(percentFraction(slice.weight), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun DailyProfitLossChart(points: List<ProfitLossPoint>, modifier: Modifier = Modifier) {
    val values = points.mapNotNull { point -> point.dailyProfitLoss?.let { point.date to it } }
    if (values.isEmpty()) {
        Text("At least two complete valuation dates are needed for daily P/L.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val visible = values.takeLast(90)
    val maximum = visible.maxOf { abs(it.second.toDouble()) }.takeIf { it > 0 } ?: 1.0
    val gain = MaterialTheme.colorScheme.primary
    val loss = MaterialTheme.colorScheme.error
    Canvas(
        modifier.fillMaxWidth().height(190.dp).padding(vertical = 8.dp).semantics {
            contentDescription = "Daily contribution-adjusted profit and loss bars for ${visible.size} complete valuation dates"
        },
    ) {
        val center = size.height / 2f
        val slot = size.width / visible.size
        drawLine(Color.Gray.copy(alpha = .45f), Offset(0f, center), Offset(size.width, center), 1.5f)
        visible.forEachIndexed { index, (_, amount) ->
            val height = (abs(amount.toDouble()) / maximum * (center - 4f)).toFloat()
            val top = if (amount.signum() >= 0) center - height else center
            drawRect(
                color = if (amount.signum() >= 0) gain else loss,
                topLeft = Offset(index * slot + slot * .12f, top),
                size = Size((slot * .76f).coerceAtLeast(1f), height.coerceAtLeast(1f)),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(visible.first().first.toString(), style = MaterialTheme.typography.labelSmall)
        Text("±${pkr(BigDecimal.valueOf(maximum))}", style = MaterialTheme.typography.labelSmall)
        Text(visible.last().first.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun HoldingGainBarChart(rows: List<ProfitBarRow>, modifier: Modifier = Modifier) {
    val visible = rows.sortedByDescending { it.value.abs() }.take(12)
    if (visible.isEmpty()) {
        Text("No valued holding gains are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maximum = visible.maxOf { it.value.abs() }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { row ->
            val fraction = row.value.abs().divide(maximum, pk.psx.wealth.domain.MONEY_CONTEXT).toFloat().coerceIn(0f, 1f)
            val color = if (row.value.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.label, Modifier.width(58.dp), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                Box(Modifier.weight(1f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(fraction).height(12.dp).background(color))
                }
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                    Text(pkr(row.value), color = color, style = MaterialTheme.typography.labelMedium)
                    row.returnFraction?.let { Text(percentFraction(it), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
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
