package pk.psx.wealth.feature.research

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.psx.wealth.ui.design.ChartPoint
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.LongTermLineChart
import pk.psx.wealth.ui.design.percentFraction
import pk.psx.wealth.ui.design.percentValue
import pk.psx.wealth.ui.design.pkr
import pk.psx.wealth.ui.design.profitColor
import pk.psx.wealth.ui.design.quantity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StockResearchScreen(viewModel: StockResearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMetricDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(state.symbol, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(state.security?.companyName ?: "Company metadata not cached")
                    Text(state.security?.sector ?: "Sector unknown", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = viewModel::refresh, enabled = !state.refreshing) { Text(if (state.refreshing) "Refreshing…" else "Refresh") }
            }
            if (state.memberships.isNotEmpty()) Text("Indexes: ${state.memberships.sorted().joinToString()}")
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pkr(state.quote?.price?.toBigDecimal()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    LabelValue("Change", state.quote?.change?.toBigDecimal()?.let(::pkr) ?: "—",
                        profitColor(state.quote?.change?.toBigDecimal()))
                    LabelValue("Change %", percentValue(state.quote?.changePercent?.toBigDecimal()))
                    LabelValue("Volume", state.quote?.volume?.toString() ?: "—")
                    LabelValue("Source", state.quote?.source ?: "—")
                    LabelValue("Retrieved", state.quote?.fetchedAt?.let(::formatTimestamp) ?: "—")
                    if (state.quote?.isManual == true) Text("Manual price", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Price history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StockPeriod.entries.forEach { period ->
                    FilterChip(state.period == period, { viewModel.selectPeriod(period) }, label = { Text(period.label()) })
                }
            }
            LongTermLineChart(state.history.map { ChartPoint(it.date, it.close) })
            Text(
                state.history.lastOrNull()?.let { "${it.date} · ${it.source}${if (it.adjusted == true) " · adjusted" else ""}" }
                    ?: "No cached daily prices for this period.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text("Market and fundamental metrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricLine(state, "MARKET_CAP", "Market cap", true)
                    MetricLine(state, "FREE_FLOAT", "Free float")
                    MetricLine(state, "PE", "P/E")
                    MetricLine(state, "PB", "P/B")
                    MetricLine(state, "DIVIDEND_YIELD", "Dividend yield", percent = true)
                    MetricLine(state, "ROE", "ROE", percent = true)
                    MetricLine(state, "DEBT_EQUITY", "Debt/equity")
                }
            }
        }
        item {
            Text("Portfolio context", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val holding = state.portfolioContext.holding
            if (holding == null) Text("Not currently held in the selected portfolio.")
            else Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelValue("Quantity", quantity(holding.quantity))
                    LabelValue("Market value", pkr(holding.marketValue))
                    LabelValue("Average cost", pkr(holding.averageCost))
                    LabelValue("Unrealized P/L", pkr(holding.unrealizedProfit), profitColor(holding.unrealizedProfit))
                    LabelValue("Portfolio weight", percentFraction(state.portfolioContext.portfolioWeight))
                    LabelValue("Target weight", percentFraction(state.portfolioContext.targetWeight))
                }
            }
        }
        item {
            Text("Transparent fundamental score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelValue("Score", state.score.score.scoreText())
                    LabelValue("Data confidence", percentFraction(state.score.confidence))
                    LabelValue("Profile", state.score.profile)
                    state.score.components.forEach { component ->
                        Text("${component.component.label}: ${component.score.setScale(0, RoundingMode.HALF_UP)}/100", fontWeight = FontWeight.Medium)
                        Text(component.evidence.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.score.missingComponents.isNotEmpty()) {
                        Text("Missing: ${state.score.missingComponents.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Missing data is excluded and reduces confidence; this is not a buy/sell signal.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Watchlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.watchlists.isEmpty()) {
                OutlinedButton(onClick = { viewModel.addToWatchlist(null) }, modifier = Modifier.fillMaxWidth()) { Text("Create Research list and add") }
            } else state.watchlists.forEach { list ->
                val watched = list.id in state.watchedIn
                OutlinedButton(
                    onClick = { if (watched) viewModel.removeFromWatchlist(list.id) else viewModel.addToWatchlist(list.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (watched) "Remove from ${list.name}" else "Add to ${list.name}") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dated observations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showMetricDialog = true }) { Text("Add metric") }
            }
        }
        if (state.fundamentals.isEmpty()) item { Text("No fundamental observations are stored yet. Manual entry keeps research useful offline.") }
        items(state.fundamentals, key = { it.id }) { metric ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(metric.metricCode, fontWeight = FontWeight.Bold)
                        Text("${BigDecimal.valueOf(metric.value).stripTrailingZeros().toPlainString()} ${metric.unit}")
                    }
                    Text("${metric.periodEnd} · ${metric.periodType} · ${metric.source}", style = MaterialTheme.typography.bodySmall)
                    Text("Retrieved ${formatTimestamp(metric.retrievedAt)}", style = MaterialTheme.typography.bodySmall)
                    if (metric.source == "Manual") TextButton(onClick = { viewModel.deleteMetric(metric.id) }) { Text("Delete") }
                }
            }
        }
    }

    if (showMetricDialog) FundamentalDialog(
        onDismiss = { showMetricDialog = false },
        onSave = { code, value, unit, date, type ->
            viewModel.saveMetric(code, value, unit, date, type)
            showMetricDialog = false
        },
    )
}

@Composable
private fun MetricLine(state: StockResearchUiState, code: String, label: String, money: Boolean = false, percent: Boolean = false) {
    val metric = state.latestMetrics[code]
    val value = metric?.value?.let { BigDecimal.valueOf(it) }
    LabelValue(label, when {
        money -> pkr(value)
        percent -> percentValue(value)
        else -> value?.stripTrailingZeros()?.toPlainString() ?: "—"
    })
    metric?.let { Text("${it.periodEnd} · ${it.source}", style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun FundamentalDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var code by remember { mutableStateOf("ROE") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("%") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var type by remember { mutableStateOf("ANNUAL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fundamental observation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Metric code, e.g. ROE or EPS") })
                OutlinedTextField(value, { value = it }, label = { Text("Value") })
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") })
                OutlinedTextField(date, { date = it }, label = { Text("Period end (YYYY-MM-DD)") })
                OutlinedTextField(type, { type = it.uppercase() }, label = { Text("Period type") })
            }
        },
        confirmButton = { Button(onClick = { onSave(code, value, unit, date, type) }, enabled = code.isNotBlank() && value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun StockPeriod.label() = when (this) {
    StockPeriod.ONE_MONTH -> "1M"
    StockPeriod.SIX_MONTHS -> "6M"
    StockPeriod.YTD -> "YTD"
    StockPeriod.ONE_YEAR -> "1Y"
    StockPeriod.THREE_YEARS -> "3Y"
    StockPeriod.FIVE_YEARS -> "5Y"
}

private fun BigDecimal?.scoreText() = this?.setScale(0, RoundingMode.HALF_UP)?.toPlainString()?.let { "$it/100" } ?: "Not scored"
private fun formatTimestamp(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
