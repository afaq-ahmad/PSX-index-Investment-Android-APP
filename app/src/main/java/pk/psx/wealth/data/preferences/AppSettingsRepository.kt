package pk.psx.wealth.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pk.psx.wealth.domain.MarketProviderConfiguration
import pk.psx.wealth.domain.QuoteProviderPreference
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
    val remoteMarketDataEnabled: Boolean = true,
    val psxProviderEnabled: Boolean = true,
    val scsQuoteFallbackEnabled: Boolean = true,
    val quoteProviderPreference: QuoteProviderPreference = QuoteProviderPreference.PSX_FIRST,
    val refreshPortfolioQuotes: Boolean = true,
    val refreshWatchlistQuotes: Boolean = true,
    val refreshKmi30: Boolean = true,
    val refreshKse100: Boolean = true,
    val refreshKmiAllShare: Boolean = true,
    val privacyScreen: Boolean = false,
    val pinVerifier: String? = null,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
)

@Singleton
class AppSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map(::decode)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { values ->
            val current = decode(values)
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
            values[Keys.remoteMarketDataEnabled] = next.remoteMarketDataEnabled
            values[Keys.psxProviderEnabled] = next.psxProviderEnabled
            values[Keys.scsQuoteFallbackEnabled] = next.scsQuoteFallbackEnabled
            values[Keys.quoteProviderPreference] = next.quoteProviderPreference.name
            values[Keys.refreshPortfolioQuotes] = next.refreshPortfolioQuotes
            values[Keys.refreshWatchlistQuotes] = next.refreshWatchlistQuotes
            values[Keys.refreshKmi30] = next.refreshKmi30
            values[Keys.refreshKse100] = next.refreshKse100
            values[Keys.refreshKmiAllShare] = next.refreshKmiAllShare
            values[Keys.privacyScreen] = next.privacyScreen
            next.pinVerifier?.let { values[Keys.pinVerifier] = it } ?: values.remove(Keys.pinVerifier)
            values[Keys.biometricEnabled] = next.biometricEnabled
            values[Keys.autoLockMinutes] = next.autoLockMinutes.coerceIn(1, 120)
        }
    }

    private fun decode(values: Preferences) = AppSettings(
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
        remoteMarketDataEnabled = values[Keys.remoteMarketDataEnabled] ?: true,
        psxProviderEnabled = values[Keys.psxProviderEnabled] ?: true,
        scsQuoteFallbackEnabled = values[Keys.scsQuoteFallbackEnabled] ?: true,
        quoteProviderPreference = values[Keys.quoteProviderPreference]?.enumOrDefault(QuoteProviderPreference.PSX_FIRST)
            ?: QuoteProviderPreference.PSX_FIRST,
        refreshPortfolioQuotes = values[Keys.refreshPortfolioQuotes] ?: true,
        refreshWatchlistQuotes = values[Keys.refreshWatchlistQuotes] ?: true,
        refreshKmi30 = values[Keys.refreshKmi30] ?: true,
        refreshKse100 = values[Keys.refreshKse100] ?: true,
        refreshKmiAllShare = values[Keys.refreshKmiAllShare] ?: true,
        privacyScreen = values[Keys.privacyScreen] ?: false,
        pinVerifier = values[Keys.pinVerifier],
        biometricEnabled = values[Keys.biometricEnabled] ?: false,
        autoLockMinutes = values[Keys.autoLockMinutes] ?: 5,
    )

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
        val remoteMarketDataEnabled = booleanPreferencesKey("remote_market_data_enabled")
        val psxProviderEnabled = booleanPreferencesKey("psx_provider_enabled")
        val scsQuoteFallbackEnabled = booleanPreferencesKey("scs_quote_fallback_enabled")
        val quoteProviderPreference = stringPreferencesKey("quote_provider_preference")
        val refreshPortfolioQuotes = booleanPreferencesKey("refresh_portfolio_quotes")
        val refreshWatchlistQuotes = booleanPreferencesKey("refresh_watchlist_quotes")
        val refreshKmi30 = booleanPreferencesKey("refresh_kmi30")
        val refreshKse100 = booleanPreferencesKey("refresh_kse100")
        val refreshKmiAllShare = booleanPreferencesKey("refresh_kmi_all_share")
        val privacyScreen = booleanPreferencesKey("privacy_screen")
        val pinVerifier = stringPreferencesKey("pin_verifier")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        val autoLockMinutes = intPreferencesKey("auto_lock_minutes")
    }
}

fun AppSettings.marketProviderConfiguration() = MarketProviderConfiguration(
    remoteMarketDataEnabled = remoteMarketDataEnabled,
    psxProviderEnabled = psxProviderEnabled,
    scsQuoteFallbackEnabled = scsQuoteFallbackEnabled,
    quoteProviderPreference = quoteProviderPreference,
)

private inline fun <reified T : Enum<T>> String.enumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
