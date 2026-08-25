package pk.psx.wealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import pk.psx.wealth.ui.PsxWealthApp
import pk.psx.wealth.ui.theme.PsxTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PsxTheme { PsxWealthApp() } }
    }
}

