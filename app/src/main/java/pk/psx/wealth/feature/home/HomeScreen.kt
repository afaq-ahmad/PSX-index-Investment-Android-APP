package pk.psx.wealth.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.psx.wealth.feature.portfolio.PortfolioViewModel
import pk.psx.wealth.ui.design.EmptyState
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.MetricCard
import pk.psx.wealth.ui.design.pkr
import pk.psx.wealth.ui.design.profitColor

@Composable
fun HomeScreen(
    onAddDeposit: () -> Unit,
    onAddBuy: () -> Unit,
    onAddDividend: () -> Unit,
    onRebalance: () -> Unit,
    onCreatePortfolio: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    if (state.portfolio == null || snapshot == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState("Create a portfolio to start your local investment ledger", Icons.Default.AccountBalanceWallet)
            Button(onClick = onCreatePortfolio, modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth()) {
                Text("Create portfolio")
            }
        }
        return
    }

    val missingPrices = snapshot.holdings.count { it.marketPrice == null }
    val maximumDrift = state.rows.mapNotNull { it.drift?.abs() }.maxOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(state.portfolio?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Benchmark ${state.portfolio?.benchmark}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Portfolio value", pkr(snapshot.totalPortfolioValue), Modifier.weight(1f))
                MetricCard(
                    "Total profit",
                    pkr(snapshot.totalProfit),
                    Modifier.weight(1f),
                    supporting = if (!snapshot.hasCompletePrices) "Waiting for all prices" else null,
                    valueColor = profitColor(snapshot.totalProfit),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Cash", pkr(snapshot.cashBalance), Modifier.weight(1f))
                MetricCard("Stock value", pkr(snapshot.stockMarketValue), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wealth decomposition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LabelValue("Net contributions", pkr(snapshot.netContributions))
                    LabelValue("Unrealized profit", pkr(snapshot.unrealizedProfit), profitColor(snapshot.unrealizedProfit))
                    LabelValue("Realized profit", pkr(snapshot.realizedProfit), profitColor(snapshot.realizedProfit))
                    LabelValue("Dividend income", pkr(snapshot.dividendIncome), profitColor(snapshot.dividendIncome))
                    LabelValue("Fees and taxes", pkr(snapshot.feesAndTaxes))
                }
            }
        }
        item {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddDeposit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Text("Deposit")
                    }
                    Button(onClick = onAddBuy, modifier = Modifier.weight(1f)) { Text("Record buy") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddDividend, modifier = Modifier.weight(1f)) { Text("Dividend") }
                    OutlinedButton(onClick = onRebalance, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Balance, null)
                        Text("Rebalance")
                    }
                }
            }
        }
        if (missingPrices > 0 || state.targets.isEmpty() || (maximumDrift?.toDouble() ?: 0.0) > 0.05) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                            Text("Attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (missingPrices > 0) Text("$missingPrices holding(s) have no current price. Values that depend on them remain unavailable.")
                        if (state.targets.isEmpty()) Text("Set target allocations before calculating a rebalance plan.")
                        if ((maximumDrift?.toDouble() ?: 0.0) > 0.05) Text("At least one holding is more than 5% away from its target.")
                    }
                }
            }
        }
    }
}
