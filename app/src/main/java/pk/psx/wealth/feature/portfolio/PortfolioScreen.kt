package pk.psx.wealth.feature.portfolio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.TransactionType
import pk.psx.wealth.ui.design.EmptyState
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.MetricCard
import pk.psx.wealth.ui.design.percentFraction
import pk.psx.wealth.ui.design.pkr
import pk.psx.wealth.ui.design.profitColor
import pk.psx.wealth.ui.design.quantity
import java.time.ZoneId

@Composable
fun PortfolioScreen(
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (TransactionType, Long) -> Unit,
    onManualPrice: () -> Unit,
    onTargets: () -> Unit,
    onOpenStock: (String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PortfolioTransaction?>(null) }
    val snapshot = state.snapshot
    if (snapshot == null) {
        EmptyState("Create a portfolio from Home to use the ledger", Icons.Default.AccountBalanceWallet)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Portfolio", pkr(snapshot.totalPortfolioValue), Modifier.weight(1f))
                MetricCard("Available cash", pkr(snapshot.cashBalance), Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAddTransaction(TransactionType.BUY) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Text("Buy")
                    }
                    Button(onClick = { onAddTransaction(TransactionType.CASH_DEPOSIT) }, modifier = Modifier.weight(1f)) {
                        Text("Deposit")
                    }
                    OutlinedButton(onClick = { onAddTransaction(TransactionType.DIVIDEND) }, modifier = Modifier.weight(1f)) {
                        Text("Dividend")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onManualPrice, modifier = Modifier.weight(1f)) { Text("Manual price") }
                    OutlinedButton(onClick = onTargets, modifier = Modifier.weight(1f)) { Text("Targets") }
                }
            }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Text("Holdings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Quantities and cost basis are calculated from the ledger.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("Filter", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HoldingFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.displayName()) },
                    )
                }
            }
            Text("Sort", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HoldingSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { viewModel.setSort(sort) },
                        label = { Text(sort.displayName()) },
                    )
                }
            }
        }
        if (state.rows.isEmpty()) {
            item { Text("No holdings match this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.rows, key = { it.holding.symbol }) { row ->
            val holding = row.holding
            Card(Modifier.fillMaxWidth().clickable { onOpenStock(holding.symbol) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(holding.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(pkr(holding.marketValue), style = MaterialTheme.typography.titleMedium)
                    }
                    LabelValue("Quantity", quantity(holding.quantity))
                    LabelValue("Market price", pkr(holding.marketPrice))
                    state.quotes[holding.symbol]?.let { quote ->
                        val observed = quote.marketTime ?: quote.retrievedAt
                        LabelValue("Price source", "${quote.source} · ${observed.atZone(ZoneId.systemDefault()).toLocalDateTime()}")
                    }
                    LabelValue("Average cost", pkr(holding.averageCost))
                    LabelValue("Remaining cost", pkr(holding.remainingCost))
                    LabelValue("Unrealized P/L", pkr(holding.unrealizedProfit), profitColor(holding.unrealizedProfit))
                    LabelValue("Current weight", percentFraction(row.currentWeight))
                    LabelValue("Target weight", percentFraction(row.targetWeight))
                    LabelValue("Drift", percentFraction(row.drift), profitColor(row.drift?.negate()))
                }
            }
        }
        item {
            Text("Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (state.transactions.isEmpty()) {
            item { Text("No ledger entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.transactions, key = { it.id }) { transaction ->
            TransactionCard(
                transaction = transaction,
                onEdit = { onEditTransaction(transaction.type, transaction.id) },
                onDelete = { pendingDelete = transaction },
            )
        }
    }

    pendingDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ledger entry?") },
            text = { Text("This recalculates cash, holdings, cost basis and profit from the remaining history.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteTransaction(transaction.id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TransactionCard(transaction: PortfolioTransaction, onEdit: () -> Unit, onDelete: () -> Unit) {
    val amount = transaction.cashAmount ?: transaction.grossAmount
        ?: transaction.quantity.multiply(transaction.price)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(transaction.type.displayName(), fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(transaction.symbol, transaction.tradeDate.toString()).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (transaction.symbol != null && transaction.quantity.signum() != 0) {
                    Text("${quantity(transaction.quantity)} @ ${pkr(transaction.price)}")
                }
                Text(pkr(amount))
                transaction.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit transaction") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete transaction") }
        }
    }
}

internal fun TransactionType.displayName(): String = name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.titlecase() }

private fun HoldingSort.displayName(): String = name.lowercase().replaceFirstChar { it.titlecase() }
private fun HoldingFilter.displayName(): String = name.lowercase().replaceFirstChar { it.titlecase() }
