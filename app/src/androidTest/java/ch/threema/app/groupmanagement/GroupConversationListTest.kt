package ch.threema.app.groupmanagement

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import ch.threema.app.R
import ch.threema.app.adapters.MessageListAdapter
import ch.threema.app.home.HomeActivity
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import junit.framework.TestCase

/**
 * This class provides a utility method to verify that the correct group names are displayed.
 */
abstract class GroupConversationListTest<T : AbstractGroupMessage> : GroupControlTest<T>() {
    /**
     * Assert that in the given scenario the expected groups are listed.
     */
    protected fun assertGroupConversations(
        scenario: ActivityScenario<HomeActivity>,
        expectedGroups: List<TestGroup>,
        errorMessage: String = "",
    ) {
        Thread.sleep(500)

        scenario.onActivity { activity ->
            val adapter = activity.findViewById<RecyclerView>(R.id.list)?.adapter
            assertGroups(expectedGroups, adapter as MessageListAdapter, errorMessage)
        }
    }

    /**
     * Assert that the given recycler view shows the given
     */
    private fun assertGroups(
        testGroups: List<TestGroup>,
        adapter: MessageListAdapter,
        errorMessage: String,
    ) {
        val expectedGroupNames: Set<String> = testGroups.map { it.groupName }.toSet()

        val actualGroupNames = (0 until adapter.itemCount)
            .mapNotNull { adapter.getEntity(it) }
            .map { it.messageReceiver.displayName }
            .toSet()

        TestCase.assertEquals(errorMessage, expectedGroupNames, actualGroupNames)
    }
}
