package ch.threema.app.groupmanagement

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.withId
import ch.threema.app.R
import ch.threema.app.home.HomeActivity
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import kotlin.test.BeforeTest

/**
 * This class provides a utility method to verify that the correct group names are displayed.
 */
abstract class GroupConversationListTest<T : AbstractGroupMessage> : GroupControlTest<T>() {

    @BeforeTest
    override fun setup() {
        super.setup()
        startScenario()
    }

    private fun startScenario() {
        Intents.init()

        launchActivity<HomeActivity>()

        do {
            var switchedToMessages = false
            try {
                onView(withId(R.id.messages)).perform(click())
                switchedToMessages = true
            } catch (_: NoMatchingViewException) {
                onView(withId(R.id.close_button)).perform(click())
            }
        } while (!switchedToMessages)

        Intents.release()
    }

    /**
     * Assert that in the given scenario the expected groups are listed.
     */
    protected fun assertGroupConversations(expectedGroups: List<TestGroup>) {
        Thread.sleep(1500)
        expectedGroups.forEach { testGroup ->
            composeTestRule.onNodeWithText(testGroup.groupName).assertIsDisplayed()
        }
    }
}
