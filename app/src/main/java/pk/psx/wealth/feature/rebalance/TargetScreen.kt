package pk.psx.wealth.feature.rebalance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.psx.wealth.domain.TargetMode
import pk.psx.wealth.ui.design.percentValue
import java.math.BigDecimal

@Composable
fun TargetScreen(
    onFinished: () -> Unit,
    viewModel: TargetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var initializedFor by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf(TargetMode.CUSTOM) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var custom by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cashTarget by remember { mutableStateOf("0") }
    var addedSymbols by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newSymbol by remember { mutableStateOf("") }

    LaunchedEffect(state.portfolioId, state.currentTargets) {
        if (state.portfolioId != null && initializedFor != state.portfolioId) {
            selected = state.currentTargets.keys.ifEmpty { state.holdingSymbols }
            custom = state.currentTargets.mapValues { (_, weight) -> weight.movePointRight(2).stripTrailingZeros().toPlainString() }
            val stockTotal = state.currentTargets.values.fold(BigDecimal.ZERO, BigDecimal::add).movePointRight(2)
            cashTarget = BigDecimal(100).subtract(stockTotal).coerceAtLeast(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
            initializedFor = state.portfolioId
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }

    val indexSymbols = state.indexRows.map { it.symbol }.toSet()
    val candidates = (state.holdingSymbols + state.currentTargets.keys + indexSymbols + addedSymbols).toSortedSet()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Target allocation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Saved targets guide rebalancing; they never create transactions by themselves.")
        Text("Strategy", style = MaterialTheme.typography.labelLarge)
        TargetMode.entries.forEach { candidate ->
            FilterChip(
                selected = mode == candidate,
                onClick = { mode = candidate },
                label = { Text(candidate.label()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = cashTarget,
            onValueChange = { cashTarget = it },
            label = { Text("Cash target (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (mode) {
            TargetMode.INDEX_WEIGHT -> {
                Text("Use all ${state.benchmark} constituents in proportion to the latest cached index weights.")
                if (state.indexRows.isEmpty()) Text("No cached index snapshot. Refresh market data first.", color = MaterialTheme.colorScheme.error)
                state.indexRows.forEach { row -> TargetIndexRow(row.symbol, row.companyName, row.weightPercent?.toBigDecimal()) }
            }
            TargetMode.SELECTED_INDEX -> {
                Text("Select ${state.benchmark} constituents. Their index weights are renormalized across the selection.")
                if (state.indexRows.isEmpty()) Text("No cached index snapshot. Refresh market data first.", color = MaterialTheme.colorScheme.error)
                state.indexRows.forEach { row ->
                    SelectableSymbolRow(
                        symbol = row.symbol,
                        detail = "${row.companyName} · ${percentValue(row.weightPercent?.toBigDecimal())}",
                        checked = row.symbol in selected,
                        onChecked = { checked -> selected = if (checked) selected + row.symbol else selected - row.symbol },
                    )
                }
            }
            TargetMode.EQUAL_WEIGHT -> {
                Text("Select securities to receive equal stock weights after the cash target.")
                AddSymbolRow(newSymbol, { newSymbol = it.uppercase() }) {
                    val clean = newSymbol.trim().uppercase()
                    if (clean.isNotEmpty()) { addedSymbols = addedSymbols + clean; selected = selected + clean; newSymbol = "" }
                }
                candidates.forEach { symbol ->
                    SelectableSymbolRow(
                        symbol,
                        if (symbol in indexSymbols) "${state.benchmark} constituent" else "Portfolio security",
                        symbol in selected,
                    ) { checked -> selected = if (checked) selected + symbol else selected - symbol }
                }
            }
            TargetMode.CUSTOM -> {
                Text("Enter stock target percentages. Together they must equal 100% minus the cash target.")
                AddSymbolRow(newSymbol, { newSymbol = it.uppercase() }) {
                    val clean = newSymbol.trim().uppercase()
                    if (clean.isNotEmpty()) { addedSymbols = addedSymbols + clean; newSymbol = "" }
                }
                candidates.forEach { symbol ->
                    OutlinedTextField(
                        value = custom[symbol].orEmpty(),
                        onValueChange = { value -> custom = custom + (symbol to value) },
                        label = { Text("$symbol target (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { viewModel.save(mode, selected, custom, cashTarget) },
            enabled = state.portfolioId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save targets") }
    }
}

@Composable
private fun TargetIndexRow(symbol: String, name: String, weight: BigDecimal?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(symbol, fontWeight = FontWeight.Bold)
                Text(name, style = MaterialTheme.typography.bodySmall)
            }
            Text(percentValue(weight))
        }
    }
}

@Composable
private fun SelectableSymbolRow(symbol: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(4.dp)) {
                Text(symbol, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun AddSymbolRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onValueChange, label = { Text("Add PSX symbol") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onAdd, enabled = value.isNotBlank()) { Text("Add") }
    }
}

private fun TargetMode.label(): String = when (this) {
    TargetMode.CUSTOM -> "Custom percentages"
    TargetMode.INDEX_WEIGHT -> "Full index weights"
    TargetMode.SELECTED_INDEX -> "Selected index constituents"
    TargetMode.EQUAL_WEIGHT -> "Equal weight selection"
}
