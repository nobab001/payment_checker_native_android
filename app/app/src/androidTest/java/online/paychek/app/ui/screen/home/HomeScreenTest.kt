package online.paychek.app.ui.screen.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for HomeScreen. */
class HomeScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            HomeScreen(onNavigate = {})
        }
    }

    @Test
    fun homeScreen_placeholderText_exists() {
        composeTestRule.onNodeWithText("🏠 Paychek Home — Coming Soon").assertExists()
    }
}
