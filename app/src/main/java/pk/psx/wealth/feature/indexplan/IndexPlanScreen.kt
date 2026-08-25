package pk.psx.wealth.feature.indexplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.psx.wealth.domain.IndexAllocationRow
import pk.psx.wealth.domain.IndexGapAction
import pk.psx.wealth.domain.TransactionType
import pk.psx.wealth.ui.design.AllocationComparisonBar
import pk.psx.wealth.ui.design.EmptyState
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.MetricCard
import pk.psx.wealth.ui.design.percentFraction
import pk.psx.wealth.ui.design.percentValue
import pk.psx.wealth.ui.design.pkr
import pk.psx.wealth.ui.design.quantity
import java.math.BigDecimal

private enum class PlanRowFilter { ALL, BUY, SELL, BALANCED }

@Composable
fun IndexPlanScreen(
    onRecordFunds: (String) -> Unit,
    onRecordTrade: (TransactionType, String, String, String) -> Unit,
    onAdvancedRebalance: () -> Unit,
    viewModel: IndexPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.portfolio == null || state.snapshot == null) {
        EmptyState("Create or select a portfolio before building an index plan", Icons.Default.AccountBalance)
        return
    }

    var query by remember(state.indexCode) { mutableStateOf("") }
    var filter by remember(state.indexCode) { mutableStateOf(PlanRowFilter.ALL) }
    val plan = state.plan
    val filteredRows = plan?.rows.orEmpty().filter { row ->
        (query.isBlank() || row.symbol.contains(query, true) || row.companyName.contains(query, true)) && when (filter) {
            PlanRowFilter.ALL -> true
            PlanRowFilter.BUY -> row.action == IndexGapAction.BUY
            PlanRowFilter.SELL -> row.action == IndexGapAction.SELL
            PlanRowFilter.BALANCED -> row.action == IndexGapAction.BALANCED
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Build your index portfolio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Choose an index and new funds. The published percentages stay visible while the app calculates whole-share buy or sell gaps.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item {
            Text("1 · Index and investment amount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("KMI30", "KSE100", "KMIALLSHR").forEach { code ->
                    FilterChip(
                        selected = state.indexCode == code,
                        onClick = { viewModel.selectIndex(code) },
                        label = { Text(code.displayIndexName()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(state.indexCode.indexDescription(), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.additionalFunds,
                onValueChange = viewModel::setAdditionalFunds,
                label = { Text("New funds to invest (PKR)") },
                supportingText = { Text("Use 0 to rebalance only the current portfolio.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Current value", pkr(state.calculatedCurrentValue), Modifier.weight(1f))
                MetricCard("After new funds", pkr(plan?.targetCapital), Modifier.weight(1f))
            }
            val deposit = state.additionalFunds.toBigDecimalOrNull()
            OutlinedButton(
                onClick = { onRecordFunds(state.additionalFunds) },
                enabled = deposit != null && deposit.signum() > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AddCircle, null)
                Text("Record these funds as a cash deposit")
            }
            if (!state.valuationComplete) {
                Text(
                    "Some existing holdings have no usable price, so current value and percentage comparisons are incomplete.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        item {
            Text("2 · Published index stocks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelValue("Selected index", state.indexCode)
                    LabelValue("Snapshot", state.snapshotDate ?: "Not downloaded")
                    LabelValue("Constituents", state.indexRows.size.toString())
                    LabelValue("Source", state.source ?: "No cached source")
                    Button(
                        onClick = viewModel::refreshIndex,
                        enabled = !state.refreshing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Text(if (state.refreshing) "Loading ${state.indexCode}…" else "Load / refresh ${state.indexCode} stocks")
                    }
                }
            }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.indexRows.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No ${state.indexCode} stocks cached yet", fontWeight = FontWeight.Bold)
                        Text("Tap Load / refresh above while online. A valid snapshot is saved locally and remains usable offline.")
                    }
                }
            }
        }
        if (plan != null) {
            item {
                OutlinedButton(
                    onClick = viewModel::saveIndexTargets,
                    enabled = !state.savingTargets,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Save, null)
                    Text(if (state.savingTargets) "Saving targets…" else "Use ${state.indexCode} percentages as portfolio targets")
                }
            }
            item {
                Text("3 · Buy, sell, or hold", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanMetric("Buy", plan.buyCount.toString(), buyColor(), Modifier.weight(1f))
                    PlanMetric("Sell", plan.sellCount.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    PlanMetric("Matched", plan.balancedCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LabelValue("Published weight total", percentValue(plan.publishedWeightTotal))
                        LabelValue("Estimated buys", pkr(plan.estimatedBuyValue), buyColor())
                        LabelValue("Estimated sells", pkr(plan.estimatedSellValue), MaterialTheme.colorScheme.error)
                        LabelValue("Whole-share rounding cash", pkr(plan.roundingCash))
                    }
                }
                plan.warnings.forEach { warning ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(warning, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find a stock") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanRowFilter.entries.forEach { value ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = { Text(value.name.lowercase().replaceFirstChar(Char::titlecase)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("${filteredRows.size} of ${plan.rows.size} securities", style = MaterialTheme.typography.bodySmall)
            }
            items(filteredRows, key = { it.symbol }) { row ->
                IndexAllocationCard(row, onRecordTrade)
            }
            item {
                OutlinedButton(onClick = onAdvancedRebalance, modifier = Modifier.fillMaxWidth()) {
                    Text("Advanced rebalance constraints and saved plans")
                }
                Text(
                    "Green and red are planning signals, not broker orders. Record only actual executions; fees, taxes and the final price can be edited before saving.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IndexAllocationCard(
    row: IndexAllocationRow,
    onRecordTrade: (TransactionType, String, String, String) -> Unit,
) {
    val color = actionColor(row.action)
    var tradeShares by remember(row.symbol, row.shareGap) {
        mutableStateOf(row.shareGap?.abs()?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .08f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(row.companyName, style = MaterialTheme.typography.bodySmall)
                    if (!row.isIndexConstituent) Text("Not in the selected index", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(actionIcon(row.action), null, tint = color)
                    Text(row.action.label(), color = color, fontWeight = FontWeight.Bold)
                }
            }
            LabelValue("Default index percentage", percentValue(row.defaultWeightPercent), color)
            AllocationComparisonBar("Current vs index", row.currentWeight ?: BigDecimal.ZERO, row.targetWeight)
            LabelValue("Current shares", quantity(row.ownedShares))
            LabelValue("Target whole shares", quantity(row.targetShares))
            LabelValue("Share gap", row.shareGap?.let(::quantity) ?: "Price required", color)
            LabelValue("Market price", pkr(row.price))
            LabelValue("Current value", pkr(row.currentValue))
            LabelValue("Target value", pkr(row.targetValue))
            LabelValue("Estimated trade", pkr(row.estimatedTradeValue), color)
            if (row.action == IndexGapAction.BUY || row.action == IndexGapAction.SELL) {
                OutlinedTextField(
                    value = tradeShares,
                    onValueChange = { tradeShares = it },
                    label = { Text("Shares to record") },
                    supportingText = { Text("The calculated gap is prefilled; replace it with the shares actually executed.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val quantityValue = tradeShares.toBigDecimalOrNull()
                Button(
                    onClick = {
                        val type = if (row.action == IndexGapAction.BUY) TransactionType.BUY else TransactionType.SELL
                        onRecordTrade(type, row.symbol, tradeShares, row.price!!.stripTrailingZeros().toPlainString())
                    },
                    enabled = row.price != null && quantityValue != null && quantityValue.signum() > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record ${row.action.label()} in ledger") }
            }
            if (row.action == IndexGapAction.PRICE_REQUIRED) {
                Text("Refresh the index/quote or enter a manual price before calculating shares.", color = color)
            }
        }
    }
}

@Composable
private fun PlanMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .10f))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
            Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun buyColor() = Color(0xFF18794E)

@Composable
private fun actionColor(action: IndexGapAction): Color = when (action) {
    IndexGapAction.BUY -> buyColor()
    IndexGapAction.SELL -> MaterialTheme.colorScheme.error
    IndexGapAction.BALANCED -> MaterialTheme.colorScheme.primary
    IndexGapAction.PRICE_REQUIRED -> MaterialTheme.colorScheme.tertiary
}

private fun actionIcon(action: IndexGapAction): ImageVector = when (action) {
    IndexGapAction.BUY -> Icons.Default.TrendingUp
    IndexGapAction.SELL -> Icons.Default.TrendingDown
    IndexGapAction.BALANCED -> Icons.Default.CheckCircle
    IndexGapAction.PRICE_REQUIRED -> Icons.Default.Warning
}

private fun IndexGapAction.label() = when (this) {
    IndexGapAction.BUY -> "BUY"
    IndexGapAction.SELL -> "SELL"
    IndexGapAction.BALANCED -> "MATCHED"
    IndexGapAction.PRICE_REQUIRED -> "PRICE NEEDED"
}

private fun String.displayIndexName() = when (this) {
    "KMI30" -> "KMI 30"
    "KSE100" -> "KSE 100"
    else -> "KMI All"
}

private fun String.indexDescription() = when (this) {
    "KMI30" -> "Thirty liquid Shariah-compliant companies, weighted using the published KMI-30 methodology."
    "KSE100" -> "The broad KSE-100 benchmark across major PSX sectors."
    else -> "The broader PSX-KMI All Share universe of Shariah-compliant companies."
}
