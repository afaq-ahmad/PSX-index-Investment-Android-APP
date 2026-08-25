package pk.psx.wealth.feature.rebalance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pk.psx.wealth.data.local.RebalancePlanEntity
import pk.psx.wealth.domain.SuggestedTrade
import pk.psx.wealth.ui.design.EmptyState
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.percentFraction
import pk.psx.wealth.ui.design.pkr
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RebalanceScreen(
    onConfigureTargets: () -> Unit,
    onExecutePlan: (Long) -> Unit,
    viewModel: RebalanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var additionalCash by remember { mutableStateOf("0") }
    var reserve by remember { mutableStateOf("0") }
    var minimumTrade by remember { mutableStateOf("0") }
    var allowSelling by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    if (snapshot == null) {
        EmptyState("Create a portfolio before planning a rebalance", Icons.Default.Balance)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Rebalance planner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Suggestions are drafts. Your ledger changes only after you confirm actual executions.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelValue("Current portfolio", pkr(snapshot.totalPortfolioValue))
                    LabelValue("Current cash", pkr(snapshot.cashBalance))
                    LabelValue("Configured stock targets", state.targets.size.toString())
                }
            }
        }
        if (state.targets.isEmpty()) {
            item {
                Text("Configure targets before calculating trades.", color = MaterialTheme.colorScheme.error)
                Button(onClick = onConfigureTargets, modifier = Modifier.fillMaxWidth()) { Text("Configure targets") }
            }
        } else {
            item {
                Text("Current allocation and drift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.targets.toSortedMap().forEach { (symbol, target) ->
                    val current = snapshot.portfolioWeight(symbol) ?: BigDecimal.ZERO
                    LabelValue(symbol, "${percentFraction(current)} → ${percentFraction(target)}  (${percentFraction(current - target)})")
                }
                OutlinedButton(onClick = onConfigureTargets, modifier = Modifier.fillMaxWidth()) { Text("Edit targets") }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RebalanceNumberField(additionalCash, { additionalCash = it }, "New cash to invest")
                    RebalanceNumberField(reserve, { reserve = it }, "Cash reserve")
                    RebalanceNumberField(minimumTrade, { minimumTrade = it }, "Minimum trade value")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow selling", fontWeight = FontWeight.Medium)
                            Text("Off uses cash-only rebalancing; on may suggest sales.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(allowSelling, onCheckedChange = { allowSelling = it })
                    }
                    Button(
                        onClick = { viewModel.calculate(additionalCash, reserve, minimumTrade, allowSelling) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Calculate plan") }
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        state.result?.let { result ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Plan impact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LabelValue("Allocation drift before", percentFraction(result.driftBefore))
                        LabelValue("Allocation drift after", percentFraction(result.driftAfter))
                        LabelValue("Cash before", pkr(result.cashBefore))
                        LabelValue("Estimated cash after", pkr(result.cashAfter))
                        LabelValue("Suggested trades", result.trades.size.toString())
                    }
                }
            }
            if (result.warnings.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Warnings", fontWeight = FontWeight.Bold)
                            result.warnings.forEach { Text("• $it") }
                        }
                    }
                }
            }
            if (result.trades.isEmpty()) {
                item { Text("No executable whole-share trades meet these constraints.") }
            }
            items(result.trades, key = { "${it.action}-${it.symbol}" }) { trade -> SuggestedTradeCard(trade) }
            item {
                Button(
                    onClick = viewModel::savePlan,
                    enabled = state.savedPlanId == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.savedPlanId == null) "Save plan for execution" else "Plan saved") }
            }
        }
        if (state.plans.isNotEmpty()) {
            item { Text("Saved plans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.plans, key = { it.id }) { plan ->
                SavedPlanCard(plan, onExecute = { onExecutePlan(plan.id) }, onCancel = { viewModel.cancelPlan(plan.id) })
            }
        }
    }
}

@Composable
private fun RebalanceNumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SuggestedTradeCard(trade: SuggestedTrade) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${trade.action} ${trade.symbol}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${trade.quantity} shares")
            }
            LabelValue("Estimated price", pkr(trade.estimatedPrice))
            LabelValue("Estimated value", pkr(trade.estimatedValue))
            LabelValue("Weight", "${percentFraction(trade.currentWeight)} → ${percentFraction(trade.projectedWeight)}")
            LabelValue("Target", percentFraction(trade.targetWeight))
        }
    }
}

@Composable
private fun SavedPlanCard(plan: RebalancePlanEntity, onExecute: () -> Unit, onCancel: () -> Unit) {
    val date = Instant.ofEpochMilli(plan.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ISO_DATE)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plan #${plan.id}", fontWeight = FontWeight.Bold)
                Text(plan.status)
            }
            Text("Created $date · ${if (plan.allowSelling) "Buy and sell" else "Cash only"}")
            plan.driftAfter?.let { LabelValue("Estimated drift", percentFraction(it.toBigDecimal())) }
            if (plan.status == "SAVED") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExecute, modifier = Modifier.weight(1f)) { Text("Record execution") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel plan") }
                }
            }
        }
    }
}
