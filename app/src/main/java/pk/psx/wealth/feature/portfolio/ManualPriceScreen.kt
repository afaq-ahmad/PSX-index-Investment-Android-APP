package pk.psx.wealth.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.time.LocalDate

@Composable
fun ManualPriceScreen(
    onFinished: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Record manual market price", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Manual prices are labelled and replace neither your transaction history nor historical market data.")
        OutlinedTextField(symbol, { symbol = it.uppercase() }, label = { Text("PSX symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            price,
            { price = it },
            label = { Text("Closing price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(date, { date = it }, label = { Text("Price date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { viewModel.saveManualPrice(symbol, price, date, onFinished) },
            enabled = symbol.isNotBlank() && price.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save manual price") }
    }
}
