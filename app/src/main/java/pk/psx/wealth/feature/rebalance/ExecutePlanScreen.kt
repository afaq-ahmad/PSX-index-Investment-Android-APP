package pk.psx.wealth.feature.rebalance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.psx.wealth.domain.TradeAction
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.pkr
import java.math.BigDecimal

@Composable
fun ExecutePlanScreen(
    onFinished: () -> Unit,
    viewModel: ExecutePlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var initializedPlan by remember { mutableStateOf<Long?>(null) }
    var inputs by remember { mutableStateOf<List<ExecutionInput>>(emptyList()) }
    var showConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(state.plan?.plan?.id) {
        state.plan?.let { plan ->
            if (initializedPlan != plan.plan.id) {
                inputs = plan.items.map { item ->
                    ExecutionInput(
                        symbol = item.symbol,
                        action = TradeAction.valueOf(item.action),
                        quantity = item.quantity.toString(),
                        price = BigDecimal.valueOf(item.estimatedPrice).stripTrailingZeros().toPlainString(),
                    )
                }
                initializedPlan = plan.plan.id
            }
        }
    }
    LaunchedEffect(state.completed) { if (state.completed) onFinished() }

    val plan = state.plan
    if (plan == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("Loading saved plan…", modifier = Modifier.padding(12.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Record plan execution", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Replace estimates with the contract-note quantities, prices, fees and taxes. Only this confirmation writes ledger transactions.")
        }
        if (plan.plan.status != "SAVED") {
            item { Text("This plan is ${plan.plan.status.lowercase()} and cannot be executed.", color = MaterialTheme.colorScheme.error) }
        }
        itemsIndexed(inputs, key = { _, input -> "${input.action}-${input.symbol}" }) { index, input ->
            ExecutionCard(input) { updated -> inputs = inputs.toMutableList().also { it[index] = updated } }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = { showConfirmation = true },
                enabled = !state.executing && plan.plan.status == "SAVED" && inputs.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.executing) "Recording…" else "Review and confirm execution") }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Write ${inputs.size} trade(s) to the ledger?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    inputs.forEach { input ->
                        val value = runCatching { input.quantity.toBigDecimal().multiply(input.price.toBigDecimal()) }.getOrNull()
                        LabelValue("${input.action} ${input.symbol}", pkr(value))
                    }
                    Text("This uses your actual values and marks the saved plan as executed.")
                }
            },
            confirmButton = {
                Button(onClick = { showConfirmation = false; viewModel.execute(inputs) }) { Text("Confirm execution") }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Go back") } },
        )
    }
}

@Composable
private fun ExecutionCard(input: ExecutionInput, onChange: (ExecutionInput) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${input.action} ${input.symbol}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutionNumberField(input.quantity, { onChange(input.copy(quantity = it)) }, "Actual quantity", Modifier.weight(1f))
                ExecutionNumberField(input.price, { onChange(input.copy(price = it)) }, "Actual price", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExecutionNumberField(input.fees, { onChange(input.copy(fees = it)) }, "Fees", Modifier.weight(1f))
                ExecutionNumberField(input.tax, { onChange(input.copy(tax = it)) }, "Tax", Modifier.weight(1f))
            }
            OutlinedTextField(
                input.date,
                { onChange(input.copy(date = it)) },
                label = { Text("Execution date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExecutionNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}
