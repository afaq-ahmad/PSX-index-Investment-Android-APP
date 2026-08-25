package pk.psx.wealth.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("local_settings")

enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class NumberFormatPreference { STANDARD, LAKH_CRORE }
enum class RebalanceModePreference { CASH_ONLY, FULL }

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val numberFormat: NumberFormatPreference = NumberFormatPreference.LAKH_CRORE,
    val defaultPortfolioId: Long? = null,
    val defaultBenchmark: String = "KMI30",
    val defaultCashReserve: Double = 0.0,
    val defaultMinimumTrade: Double = 0.0,
    val defaultRebalanceMode: RebalanceModePreference = RebalanceModePreference.CASH_ONLY,
    val refreshOnOpen: Boolean = false,
    val dailyRefresh: Boolean = false,
    val wifiOnly: Boolean = false,
    val privacyScreen: Boolean = false,
    val pinVerifier: String? = null,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
)

@Singleton
class AppSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { values ->
        AppSettings(
            theme = values[Keys.theme]?.enumOrDefault(ThemePreference.SYSTEM) ?: ThemePreference.SYSTEM,
            numberFormat = values[Keys.numberFormat]?.enumOrDefault(NumberFormatPreference.LAKH_CRORE)
                ?: NumberFormatPreference.LAKH_CRORE,
            defaultPortfolioId = values[Keys.defaultPortfolioId],
            defaultBenchmark = values[Keys.defaultBenchmark] ?: "KMI30",
            defaultCashReserve = values[Keys.defaultCashReserve] ?: 0.0,
            defaultMinimumTrade = values[Keys.defaultMinimumTrade] ?: 0.0,
            defaultRebalanceMode = values[Keys.rebalanceMode]?.enumOrDefault(RebalanceModePreference.CASH_ONLY)
                ?: RebalanceModePreference.CASH_ONLY,
            refreshOnOpen = values[Keys.refreshOnOpen] ?: false,
            dailyRefresh = values[Keys.dailyRefresh] ?: false,
            wifiOnly = values[Keys.wifiOnly] ?: false,
            privacyScreen = values[Keys.privacyScreen] ?: false,
            pinVerifier = values[Keys.pinVerifier],
            biometricEnabled = values[Keys.biometricEnabled] ?: false,
            autoLockMinutes = values[Keys.autoLockMinutes] ?: 5,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { values ->
            val current = AppSettings(
                theme = values[Keys.theme]?.enumOrDefault(ThemePreference.SYSTEM) ?: ThemePreference.SYSTEM,
                numberFormat = values[Keys.numberFormat]?.enumOrDefault(NumberFormatPreference.LAKH_CRORE)
                    ?: NumberFormatPreference.LAKH_CRORE,
                defaultPortfolioId = values[Keys.defaultPortfolioId],
                defaultBenchmark = values[Keys.defaultBenchmark] ?: "KMI30",
                defaultCashReserve = values[Keys.defaultCashReserve] ?: 0.0,
                defaultMinimumTrade = values[Keys.defaultMinimumTrade] ?: 0.0,
                defaultRebalanceMode = values[Keys.rebalanceMode]?.enumOrDefault(RebalanceModePreference.CASH_ONLY)
                    ?: RebalanceModePreference.CASH_ONLY,
                refreshOnOpen = values[Keys.refreshOnOpen] ?: false,
                dailyRefresh = values[Keys.dailyRefresh] ?: false,
                wifiOnly = values[Keys.wifiOnly] ?: false,
                privacyScreen = values[Keys.privacyScreen] ?: false,
                pinVerifier = values[Keys.pinVerifier],
                biometricEnabled = values[Keys.biometricEnabled] ?: false,
                autoLockMinutes = values[Keys.autoLockMinutes] ?: 5,
            )
            val next = transform(current)
            values[Keys.theme] = next.theme.name
            values[Keys.numberFormat] = next.numberFormat.name
            next.defaultPortfolioId?.let { values[Keys.defaultPortfolioId] = it } ?: values.remove(Keys.defaultPortfolioId)
            values[Keys.defaultBenchmark] = next.defaultBenchmark
            values[Keys.defaultCashReserve] = next.defaultCashReserve
            values[Keys.defaultMinimumTrade] = next.defaultMinimumTrade
            values[Keys.rebalanceMode] = next.defaultRebalanceMode.name
            values[Keys.refreshOnOpen] = next.refreshOnOpen
            values[Keys.dailyRefresh] = next.dailyRefresh
            values[Keys.wifiOnly] = next.wifiOnly
            values[Keys.privacyScreen] = next.privacyScreen
            next.pinVerifier?.let { values[Keys.pinVerifier] = it } ?: values.remove(Keys.pinVerifier)
            values[Keys.biometricEnabled] = next.biometricEnabled
            values[Keys.autoLockMinutes] = next.autoLockMinutes.coerceIn(1, 120)
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val numberFormat = stringPreferencesKey("number_format")
        val defaultPortfolioId = longPreferencesKey("default_portfolio_id")
        val defaultBenchmark = stringPreferencesKey("default_benchmark")
        val defaultCashReserve = doublePreferencesKey("default_cash_reserve")
        val defaultMinimumTrade = doublePreferencesKey("default_minimum_trade")
        val rebalanceMode = stringPreferencesKey("rebalance_mode")
        val refreshOnOpen = booleanPreferencesKey("refresh_on_open")
        val dailyRefresh = booleanPreferencesKey("daily_refresh")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val privacyScreen = booleanPreferencesKey("privacy_screen")
        val pinVerifier = stringPreferencesKey("pin_verifier")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        val autoLockMinutes = intPreferencesKey("auto_lock_minutes")
    }
}

private inline fun <reified T : Enum<T>> String.enumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
