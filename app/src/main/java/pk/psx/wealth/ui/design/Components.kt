package pk.psx.wealth.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.DecimalFormat
import pk.psx.wealth.data.preferences.NumberFormatPreference

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

val LocalNumberFormat = staticCompositionLocalOf { NumberFormatPreference.STANDARD }

@Composable
fun pkr(value: BigDecimal?): String = value?.let {
    if (LocalNumberFormat.current == NumberFormatPreference.LAKH_CRORE) when {
        it.abs() >= BigDecimal("10000000") -> "Rs ${DecimalFormat("0.##").format(it.divide(BigDecimal("10000000")))} Cr"
        it.abs() >= BigDecimal("100000") -> "Rs ${DecimalFormat("0.##").format(it.divide(BigDecimal("100000")))} L"
        else -> "Rs ${DecimalFormat("#,##0.##").format(it)}"
    } else "Rs ${DecimalFormat("#,##0.##").format(it)}"
} ?: "—"
fun quantity(value: BigDecimal?): String = value?.stripTrailingZeros()?.toPlainString() ?: "—"
fun percentFraction(value: BigDecimal?): String = value?.multiply(BigDecimal(100))
    ?.let { "${DecimalFormat("0.00").format(it)}%" } ?: "—"
fun percentValue(value: BigDecimal?): String = value?.let { "${DecimalFormat("0.00").format(it)}%" } ?: "—"

@Composable
fun profitColor(value: BigDecimal?): Color = when (value?.signum()) {
    1 -> Color(0xFF18794E)
    -1 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}
