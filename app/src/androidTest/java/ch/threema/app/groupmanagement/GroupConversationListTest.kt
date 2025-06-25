/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
