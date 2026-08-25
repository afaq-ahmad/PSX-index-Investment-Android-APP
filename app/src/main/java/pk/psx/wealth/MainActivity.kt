package pk.psx.wealth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import pk.psx.wealth.data.preferences.AppSettings
import pk.psx.wealth.data.preferences.AppSettingsRepository
import pk.psx.wealth.ui.PsxWealthApp
import pk.psx.wealth.ui.design.LocalNumberFormat
import pk.psx.wealth.ui.theme.PsxTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CompositionLocalProvider(LocalNumberFormat provides preferences.numberFormat) {
                PsxTheme(preferences.theme) { PsxWealthApp() }
            }
        }
    }
}
