package pk.psx.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home), Portfolio("Portfolio", Icons.Default.PieChart), Research("Research", Icons.Default.Search), Rebalance("Rebalance", Icons.Default.Balance), More("More", Icons.Default.MoreHoriz)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PsxWealthApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(destination.label, fontWeight = FontWeight.SemiBold) }, actions = { IconButton(onClick = {}) { Icon(Icons.Default.Refresh, "Refresh market data") } }) },
        bottomBar = { NavigationBar { Destination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (destination) {
        Destination.Home -> HomeScreen(); Destination.Portfolio -> PortfolioScreen(); Destination.Research -> EmptyScreen("Search stocks and browse index constituents", Icons.Default.Search)
        Destination.Rebalance -> RebalanceScreen(); Destination.More -> EmptyScreen("Backups, CSV export and preferences", Icons.Default.Settings)
    } } }
}

@Composable private fun HomeScreen() = LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item { Text("Your wealth at a glance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
    item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Total portfolio value", style = MaterialTheme.typography.labelLarge); Text(rupees(0.0), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Add a cash deposit to get started", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("Invested", rupees(0.0), Modifier.weight(1f)); Metric("Cash", rupees(0.0), Modifier.weight(1f)) } }
    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("Offline ready", fontWeight = FontWeight.SemiBold); Text("Your ledger and the last successful market snapshot stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}

@Composable private fun PortfolioScreen() = EmptyScreen("Create a portfolio, deposit cash, then record your first buy", Icons.Default.AccountBalanceWallet)

@Composable private fun RebalanceScreen() = Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("SIP cash-first rebalancing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("New cash is assigned to the most underweight holdings. Nothing is sold unless you explicitly choose a full rebalance.")
    OutlinedTextField(value = "", onValueChange = {}, label = { Text("New cash amount (Rs)") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Preview plan") }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) = Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptyScreen(message: String, icon: ImageVector) = Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(message, style = MaterialTheme.typography.titleMedium) }
private fun rupees(value: Double) = NumberFormat.getCurrencyInstance(Locale("en", "PK")).format(value)
