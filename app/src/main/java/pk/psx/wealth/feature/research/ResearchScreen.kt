package pk.psx.wealth.feature.research

import androidx.compose.foundation.clickable
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
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.ui.design.AllocationComparisonBar
import pk.psx.wealth.ui.design.ChartPoint
import pk.psx.wealth.ui.design.LabelValue
import pk.psx.wealth.ui.design.LongTermLineChart
import pk.psx.wealth.ui.design.MetricCard
import pk.psx.wealth.ui.design.YearBarChart
import pk.psx.wealth.ui.design.percentFraction
import pk.psx.wealth.ui.design.percentValue
import pk.psx.wealth.ui.design.pkr
import pk.psx.wealth.ui.design.profitColor
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ResearchScreen(
    onOpenStock: (String) -> Unit,
    viewModel: ResearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResearchSection.entries.forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { viewModel.selectSection(section) },
                    label = { Text(section.label()) },
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        when (state.section) {
            ResearchSection.INDICES -> IndexPane(state, viewModel::selectIndex, onOpenStock)
            ResearchSection.PERFORMANCE -> PerformancePane(state)
            ResearchSection.DIVIDENDS -> DividendPane(state, onOpenStock)
            ResearchSection.SCREENER -> ScreenerPane(state.screenerRows, onOpenStock)
            ResearchSection.WATCHLISTS -> WatchlistsPane(state, viewModel, onOpenStock)
        }
    }
}

@Composable
private fun IndexPane(state: ResearchUiState, onSelectIndex: (String) -> Unit, onOpenStock: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("KMI30", "KSE100", "KMIALLSHR").forEach { code ->
                    FilterChip(selected = state.indexCode == code, onClick = { onSelectIndex(code) }, label = { Text(code) })
                }
            }
        }
        val latest = state.latestIndex
        if (latest == null) {
            item { Text("No cached ${state.indexCode} snapshot. Use Refresh while online; existing research remains available offline.") }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.indexCode, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        LabelValue("Snapshot date", latest.header.snapshotDate)
                        LabelValue("Constituents", latest.constituents.size.toString())
                        LabelValue("Source", latest.header.source)
                    }
                }
            }
            if (state.addedSymbols.isNotEmpty() || state.removedSymbols.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Recomposition since prior snapshot", fontWeight = FontWeight.Bold)
                            if (state.addedSymbols.isNotEmpty()) Text("Added: ${state.addedSymbols.sorted().joinToString()}")
                            if (state.removedSymbols.isNotEmpty()) Text("Removed: ${state.removedSymbols.sorted().joinToString()}")
                        }
                    }
                }
            }
            if (state.sectors.isNotEmpty()) {
                item {
                    Text("Sector exposure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.sectors.forEach { row -> AllocationComparisonBar(row.sector, row.portfolioWeight, row.indexWeight) }
                    }
                }
            }
            item {
                Text("Portfolio vs index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Portfolio weights include cash; differences therefore expose both security and cash drift.")
            }
            items(state.comparison, key = { it.symbol }) { row ->
                Card(Modifier.fillMaxWidth().clickable { onOpenStock(row.symbol) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(row.symbol, fontWeight = FontWeight.Bold)
                                Text(row.companyName, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(row.membership.label())
                        }
                        AllocationComparisonBar("Allocation", row.portfolioWeight, row.indexWeight)
                        LabelValue("Difference", percentFraction(row.difference), profitColor(row.difference.negate()))
                        LabelValue("Cached price", pkr(row.price))
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformancePane(state: ResearchUiState) {
    val result = state.performance
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Performance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (result == null) {
            item { Text("Create a portfolio and record contributions to calculate performance.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Current value", pkr(result.currentValue), Modifier.weight(1f))
                    MetricCard("Total profit", pkr(result.totalProfit), Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelValue("Net contributions", pkr(result.netContributions))
                        LabelValue("Realized P/L", pkr(result.realizedProfit), profitColor(result.realizedProfit))
                        LabelValue("Unrealized P/L", pkr(result.unrealizedProfit), profitColor(result.unrealizedProfit))
                        LabelValue("Dividends", pkr(result.dividends))
                        LabelValue("Absolute return", percentFraction(result.absoluteReturn))
                        LabelValue("Money-weighted return (XIRR)", percentFraction(result.xirr))
                    }
                }
            }
            item {
                Text("Wealth history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Portfolio value uses the most recent locally cached close on each event date. Incomplete-price dates are omitted.")
                LongTermLineChart(state.wealthHistory.map { ChartPoint(it.date, it.value, it.netContributions) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Portfolio", color = MaterialTheme.colorScheme.primary)
                    Text("Contributions", color = MaterialTheme.colorScheme.tertiary)
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Portfolio vs benchmark", fontWeight = FontWeight.Bold)
                        Text(state.benchmarkMessage)
                        Text("No simple percentage comparison is shown because contribution dates must be simulated fairly.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DividendPane(state: ResearchUiState, onOpenStock: (String) -> Unit) {
    val result = state.dividends
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Dividend income", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (result == null) item { Text("Create a portfolio to track dividend income.") }
        else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Trailing 12 months", pkr(result.trailingTwelveMonths), Modifier.weight(1f))
                    MetricCard("Monthly average", pkr(result.trailingMonthlyAverage), Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelValue("Lifetime dividends", pkr(result.total))
                        LabelValue("This year", pkr(result.thisYear))
                        LabelValue("Contribution to total return", percentFraction(result.contributionToTotalReturn))
                    }
                }
            }
            item {
                Text("Income by year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                YearBarChart(result.byYear)
            }
            item { Text("By security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(result.bySecurity, key = { it.symbol }) { row ->
                Card(Modifier.fillMaxWidth().clickable { onOpenStock(row.symbol) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.symbol, fontWeight = FontWeight.Bold)
                        LabelValue("Lifetime", pkr(row.total))
                        LabelValue("This year", pkr(row.thisYear))
                        LabelValue("Yield on current remaining cost", percentFraction(row.yieldOnCost))
                    }
                }
            }
            item { Text("Dividends increase cash. A separate buy records any reinvestment.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private enum class ScreenerSort { SCORE, PE, DIVIDEND, ROE, ONE_YEAR, MARKET_CAP }

@Composable
private fun ScreenerPane(rows: List<ScreenerRow>, onOpenStock: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("All") }
    var index by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(ScreenerSort.SCORE) }
    val sectors = listOf("All") + rows.mapNotNull { it.security.sector }.distinct().sorted()
    val filtered = rows.filter { row ->
        (query.isBlank() || row.security.symbol.contains(query, true) || row.security.companyName.contains(query, true)) &&
            (sector == "All" || row.security.sector == sector) &&
            (index == "All" || index in row.indexMemberships)
    }.let { available ->
        when (sort) {
            ScreenerSort.SCORE -> available.sortedByDescending { it.score.score?.toDouble() ?: -1.0 }
            ScreenerSort.PE -> available.sortedBy { it.metric("PE")?.toDouble() ?: Double.MAX_VALUE }
            ScreenerSort.DIVIDEND -> available.sortedByDescending { it.metric("DIVIDEND_YIELD")?.toDouble() ?: -1.0 }
            ScreenerSort.ROE -> available.sortedByDescending { it.metric("ROE")?.toDouble() ?: -1.0 }
            ScreenerSort.ONE_YEAR -> available.sortedByDescending { it.oneYearReturn?.toDouble() ?: -Double.MAX_VALUE }
            ScreenerSort.MARKET_CAP -> available.sortedByDescending { it.metric("MARKET_CAP")?.toDouble() ?: -1.0 }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Local screener", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Filters use cached rows only; scrolling never launches remote requests.")
            OutlinedTextField(query, { query = it }, label = { Text("Symbol or company") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Text("Index", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "KMI30", "KSE100", "KMIALLSHR").forEach { value ->
                    FilterChip(index == value, { index = value }, label = { Text(value) })
                }
            }
            Text("Sector", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sectors.forEach { value -> FilterChip(sector == value, { sector = value }, label = { Text(value) }) }
            }
            Text("Sort", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenerSort.entries.forEach { value -> FilterChip(sort == value, { sort = value }, label = { Text(value.label()) }) }
            }
        }
        item { Text("${filtered.size} cached securities", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filtered, key = { it.security.symbol }) { row ->
            Card(Modifier.fillMaxWidth().clickable { onOpenStock(row.security.symbol) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.security.symbol, fontWeight = FontWeight.Bold)
                            Text(row.security.companyName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(row.score.score.scoreText())
                    }
                    LabelValue("Price", row.quote?.price?.toBigDecimal()?.let(::pkr) ?: "—")
                    LabelValue("P/E", row.metric("PE")?.stripTrailingZeros()?.toPlainString() ?: "—")
                    LabelValue("Dividend yield", percentValue(row.metric("DIVIDEND_YIELD")))
                    LabelValue("ROE", percentValue(row.metric("ROE")))
                    LabelValue("1Y return", percentFraction(row.oneYearReturn))
                    LabelValue("Confidence", percentFraction(row.score.confidence))
                    Text("${row.score.profile} profile · ${row.indexMemberships.sorted().joinToString().ifBlank { "No cached index membership" }}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WatchlistsPane(state: ResearchUiState, viewModel: ResearchViewModel, onOpenStock: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Offline watchlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("New watchlist") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.createWatchlist(name); name = "" }, enabled = name.isNotBlank()) { Text("Create") }
            }
        }
        if (state.catalog.watchlists.isEmpty()) item { Text("Create a list for companies you may research later.") }
        items(state.catalog.watchlists, key = { it.id }) { list ->
            WatchlistCard(list, state, viewModel, onOpenStock)
        }
    }
}

@Composable
private fun WatchlistCard(
    list: WatchlistEntity,
    state: ResearchUiState,
    viewModel: ResearchViewModel,
    onOpenStock: (String) -> Unit,
) {
    var symbol by remember(list.id) { mutableStateOf("") }
    var notes by remember(list.id) { mutableStateOf("") }
    val items = state.catalog.watchlistItems.filter { it.watchlistId == list.id }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.deleteWatchlist(list.id) }) { Text("Delete") }
            }
            items.forEach { item ->
                val row = state.screenerRows.firstOrNull { it.security.symbol == item.symbol }
                Column(Modifier.fillMaxWidth().clickable { onOpenStock(item.symbol) }.padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.symbol, fontWeight = FontWeight.Bold)
                        Text(row?.quote?.price?.toBigDecimal()?.let(::pkr) ?: "—")
                    }
                    item.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("Score ${row?.score?.score.scoreText()} · P/E ${row?.metric("PE") ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.removeFromWatchlist(list.id, item.symbol) }) { Text("Remove") }
                }
            }
            OutlinedTextField(symbol, { symbol = it.uppercase() }, label = { Text("PSX symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { viewModel.addToWatchlist(list.id, symbol, notes); symbol = ""; notes = "" },
                enabled = symbol.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add to ${list.name}") }
        }
    }
}

private fun ResearchSection.label() = name.lowercase().replaceFirstChar { it.titlecase() }
private fun Membership.label() = when (this) {
    Membership.BOTH -> "Owned + index"
    Membership.OWNED_ONLY -> "Owned only"
    Membership.INDEX_ONLY -> "Index only"
}
private fun ScreenerSort.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
private fun BigDecimal?.scoreText(): String = this?.setScale(0, RoundingMode.HALF_UP)?.toPlainString()?.let { "$it/100" } ?: "Not scored"
