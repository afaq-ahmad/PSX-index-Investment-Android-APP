package pk.psx.wealth

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MvpFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstUseCreatesPortfolioAndOpensDepositLedgerForm() {
        compose.onNodeWithText("Create local portfolio").assertExists()
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Portfolio").performClick()
        compose.onNodeWithText("Deposit").performClick()
        compose.onNodeWithText("Add ledger entry").assertExists()
        compose.onNodeWithText("Cash amount").assertExists()
    }
}
