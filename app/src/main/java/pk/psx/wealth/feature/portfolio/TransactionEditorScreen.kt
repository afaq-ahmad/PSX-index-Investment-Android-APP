package pk.psx.wealth.feature.portfolio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import pk.psx.wealth.domain.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun TransactionEditorScreen(
    onFinished: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(state.initialType) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var grossAmount by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var cashAmount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var allowNegativeCash by remember { mutableStateOf(false) }

    LaunchedEffect(state.initialType, state.existing?.id) {
        type = state.existing?.type ?: state.initialType
        state.existing?.let { existing ->
            date = existing.tradeDate.toString()
            symbol = existing.symbol.orEmpty()
            quantity = existing.quantity.formValue()
            price = existing.price.formValue()
            grossAmount = existing.grossAmount?.formValue().orEmpty()
            fees = existing.fees.formValue()
            tax = existing.tax.formValue()
            cashAmount = existing.cashAmount?.formValue().orEmpty()
            notes = existing.notes.orEmpty()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (state.existing == null) "Add ledger entry" else "Edit ledger entry", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Transaction type", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionType.entries.forEach { candidate ->
                FilterChip(
                    selected = type == candidate,
                    onClick = { type = candidate },
                    label = { Text(candidate.displayName()) },
                )
            }
        }
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Trade date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (type.requiresSymbol()) {
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it.uppercase() },
                label = { Text("PSX symbol") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (type.requiresQuantity()) {
            NumberField(
                value = quantity,
                onValueChange = { quantity = it },
                label = if (type == TransactionType.SPLIT) "Split factor" else "Quantity",
            )
        }
        if (type.requiresPrice()) {
            NumberField(price, { price = it }, "Price per share")
            NumberField(grossAmount, { grossAmount = it }, "Gross amount (optional override)")
        }
        if (type == TransactionType.DIVIDEND) {
            NumberField(grossAmount, { grossAmount = it }, "Gross dividend")
            NumberField(cashAmount, { cashAmount = it }, "Net cash received (optional override)")
        } else if (type.usesCashAmount()) {
            NumberField(cashAmount, { cashAmount = it }, "Cash amount")
        }
        if (type.hasCosts()) {
            NumberField(fees, { fees = it }, "Fees")
            NumberField(tax, { tax = it }, "Tax")
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Allow negative cash", fontWeight = FontWeight.Medium)
                Text("Use only to record an accounting correction.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = allowNegativeCash, onCheckedChange = { allowNegativeCash = it })
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                viewModel.save(
                    TransactionFormData(type, date, symbol, quantity, price, grossAmount, fees, tax, cashAmount, notes, allowNegativeCash),
                )
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.saving) "Saving…" else "Save ledger entry") }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun TransactionType.requiresSymbol() = this in setOf(
    TransactionType.BUY,
    TransactionType.SELL,
    TransactionType.DIVIDEND,
    TransactionType.BONUS_SHARES,
    TransactionType.RIGHT_SHARES,
    TransactionType.SPLIT,
)

private fun TransactionType.requiresQuantity() = this in setOf(
    TransactionType.BUY,
    TransactionType.SELL,
    TransactionType.BONUS_SHARES,
    TransactionType.RIGHT_SHARES,
    TransactionType.SPLIT,
)

private fun TransactionType.requiresPrice() = this in setOf(
    TransactionType.BUY,
    TransactionType.SELL,
    TransactionType.RIGHT_SHARES,
)

private fun TransactionType.usesCashAmount() = this in setOf(
    TransactionType.CASH_DEPOSIT,
    TransactionType.CASH_WITHDRAWAL,
    TransactionType.FEE,
    TransactionType.TAX,
    TransactionType.ADJUSTMENT,
)

private fun TransactionType.hasCosts() = this in setOf(
    TransactionType.BUY,
    TransactionType.SELL,
    TransactionType.RIGHT_SHARES,
    TransactionType.DIVIDEND,
)

private fun BigDecimal.formValue(): String = if (signum() == 0) "" else stripTrailingZeros().toPlainString()
