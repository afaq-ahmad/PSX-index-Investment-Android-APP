package pk.psx.wealth.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pk.psx.wealth.feature.app.AppViewModel
import pk.psx.wealth.feature.home.HomeScreen
import pk.psx.wealth.feature.indexplan.IndexPlanScreen
import pk.psx.wealth.feature.more.MoreScreen
import pk.psx.wealth.feature.portfolio.ManualPriceScreen
import pk.psx.wealth.feature.portfolio.PortfolioScreen
import pk.psx.wealth.feature.portfolio.TransactionEditorScreen
import pk.psx.wealth.feature.rebalance.ExecutePlanScreen
import pk.psx.wealth.feature.rebalance.RebalanceScreen
import pk.psx.wealth.feature.rebalance.TargetScreen
import pk.psx.wealth.feature.research.ResearchScreen
import pk.psx.wealth.feature.research.StockResearchScreen
import pk.psx.wealth.feature.security.SecurityGate
import pk.psx.wealth.feature.security.SecurityViewModel
import pk.psx.wealth.ui.design.PsxWealthLogo

object Routes {
    const val HOME = "home"
    const val PORTFOLIO = "portfolio"
    const val RESEARCH = "research"
    const val INDEX_PLAN = "index-plan"
    const val REBALANCE = "rebalance"
    const val MORE = "more"
    const val TRANSACTION = "transaction?type={type}&id={id}&symbol={symbol}&quantity={quantity}&price={price}&cash={cash}"
    const val MANUAL_PRICE = "manual-price"
    const val TARGETS = "targets"
    const val EXECUTE_PLAN = "execute/{planId}"
    const val STOCK = "stock/{symbol}"

    fun transaction(
        type: String,
        id: Long = 0,
        symbol: String = "",
        quantity: String = "",
        price: String = "",
        cash: String = "",
    ) = "transaction?type=$type&id=$id&symbol=$symbol&quantity=$quantity&price=$price&cash=$cash"
    fun execute(planId: Long) = "execute/$planId"
    fun stock(symbol: String) = "stock/${symbol.trim().uppercase()}"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)
private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Default.Home),
    BottomDestination(Routes.INDEX_PLAN, "Index Plan", Icons.Default.PieChart),
    BottomDestination(Routes.PORTFOLIO, "Portfolio", Icons.Default.AccountBalanceWallet),
    BottomDestination(Routes.RESEARCH, "Research", Icons.Default.Search),
    BottomDestination(Routes.MORE, "More", Icons.Default.MoreHoriz),
)

@Composable
fun PsxWealthApp(
    viewModel: AppViewModel = hiltViewModel(),
    securityViewModel: SecurityViewModel = hiltViewModel(),
) {
    val security by securityViewModel.state.collectAsStateWithLifecycle()
    SecurityGate(security, securityViewModel) { PsxWealthContent(viewModel, securityViewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PsxWealthContent(viewModel: AppViewModel, securityViewModel: SecurityViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.portfolios.isEmpty()) { if (state.portfolios.isEmpty()) showCreate = true }
    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PsxWealthLogo()
                        Column {
                            Text("PSX Wealth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                bottomDestinations.firstOrNull { route.startsWith(it.route) }?.label ?: "Local portfolio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(24.dp))
                    else IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh cached market data") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = { navController.navigate(item.route) { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAddDeposit = { navController.navigate(Routes.transaction("CASH_DEPOSIT")) },
                        onAddBuy = { navController.navigate(Routes.transaction("BUY")) },
                        onAddDividend = { navController.navigate(Routes.transaction("DIVIDEND")) },
                        onRebalance = { navController.navigate(Routes.INDEX_PLAN) },
                        onCreatePortfolio = { showCreate = true },
                    )
                }
                composable(Routes.PORTFOLIO) {
                    PortfolioScreen(
                        onAddTransaction = { navController.navigate(Routes.transaction(it.name)) },
                        onEditTransaction = { type, id -> navController.navigate(Routes.transaction(type.name, id)) },
                        onManualPrice = { navController.navigate(Routes.MANUAL_PRICE) },
                        onTargets = { navController.navigate(Routes.TARGETS) },
                        onOpenStock = { navController.navigate(Routes.stock(it)) },
                    )
                }
                composable(Routes.RESEARCH) {
                    ResearchScreen(onOpenStock = { navController.navigate(Routes.stock(it)) })
                }
                composable(Routes.INDEX_PLAN) {
                    IndexPlanScreen(
                        onRecordFunds = { amount ->
                            navController.navigate(Routes.transaction("CASH_DEPOSIT", cash = amount))
                        },
                        onRecordTrade = { type, symbol, quantity, price ->
                            navController.navigate(
                                Routes.transaction(type.name, symbol = symbol, quantity = quantity, price = price),
                            )
                        },
                        onAdvancedRebalance = { navController.navigate(Routes.REBALANCE) },
                    )
                }
                composable(Routes.REBALANCE) {
                    RebalanceScreen(
                        onConfigureTargets = { navController.navigate(Routes.TARGETS) },
                        onExecutePlan = { navController.navigate(Routes.execute(it)) },
                    )
                }
                composable(Routes.MORE) {
                    MoreScreen(
                        appState = state,
                        onSelectPortfolio = viewModel::selectPortfolio,
                        onCreatePortfolio = { showCreate = true },
                        onArchivePortfolio = viewModel::archivePortfolio,
                        securityViewModel = securityViewModel,
                    )
                }
                composable(
                    Routes.TRANSACTION,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType; defaultValue = "BUY" },
                        navArgument("id") { type = NavType.StringType; defaultValue = "0" },
                        navArgument("symbol") { type = NavType.StringType; defaultValue = "" },
                        navArgument("quantity") { type = NavType.StringType; defaultValue = "" },
                        navArgument("price") { type = NavType.StringType; defaultValue = "" },
                        navArgument("cash") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { TransactionEditorScreen(onFinished = { navController.popBackStack() }) }
                composable(Routes.MANUAL_PRICE) { ManualPriceScreen(onFinished = { navController.popBackStack() }) }
                composable(Routes.TARGETS) { TargetScreen(onFinished = { navController.popBackStack() }) }
                composable(
                    Routes.EXECUTE_PLAN,
                    arguments = listOf(navArgument("planId") { type = NavType.StringType }),
                ) { ExecutePlanScreen(onFinished = { navController.popBackStack() }) }
                composable(
                    Routes.STOCK,
                    arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
                ) { StockResearchScreen() }
            }
        }
    }

    if (showCreate) CreatePortfolioDialog(
        canDismiss = state.portfolios.isNotEmpty(),
        onDismiss = { showCreate = false },
        onCreate = { name, benchmark -> viewModel.createPortfolio(name, benchmark); showCreate = false },
    )
}

@Composable
private fun CreatePortfolioDialog(
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("Main Portfolio") }
    var benchmark by remember { mutableStateOf("KMI30") }
    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        title = { Text("Create local portfolio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Portfolio name") }, modifier = Modifier.fillMaxWidth())
                Text("Index to follow")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("KMI30", "KSE100", "KMIALLSHR").forEach { code ->
                        FilterChip(
                            selected = benchmark == code,
                            onClick = { benchmark = code },
                            label = { Text(code) },
                        )
                    }
                }
                Text("You can compare or switch the saved index later from Index Plan.")
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, benchmark) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { if (canDismiss) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
