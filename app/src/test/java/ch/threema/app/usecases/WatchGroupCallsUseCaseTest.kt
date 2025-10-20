/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.usecases

import app.cash.turbine.test
import ch.threema.app.compose.conversation.models.GroupCallUiModel
import ch.threema.app.test.unconfinedTestDispatcherProvider
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.common.emptyByteArray
import ch.threema.common.now
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest

class WatchGroupCallsUseCaseTest {

    @BeforeTest
    fun before() {
        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.isGroupCallsEnabled() } returns true
    }

    @Test
    fun `emits correct ui models`() = runTest {
        // arrange
        val runningCallsFlow: MutableSharedFlow<Map<CallId, GroupCallDescription>> = MutableSharedFlow()
        val groupCallManager: GroupCallManager = mockk<GroupCallManager> {
            every { isJoinedCall(callId = any<CallId>()) } returns false
            every { watchRunningCalls() } returns runningCallsFlow
        }
        val useCase = WatchGroupCallsUseCase(
            groupCallManager = groupCallManager,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // No running calls in the beginning
            runningCallsFlow.emit(emptyMap())
            expectItem(emptySet())

            // Group call 1 gets started
            runningCallsFlow.emit(
                mapOf(groupCallDescription1.callId to groupCallDescription1),
            )
            expectItem(setOf(groupCallUiModel1))

            // Group call 2 also gets started
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription1.callId to groupCallDescription1,
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(setOf(groupCallUiModel1, groupCallUiModel2))

            // Group call 1 ends
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(setOf(groupCallUiModel2))

            // Group call feature gets disabled while group call 2 is still running
            every { ConfigUtils.isGroupCallsEnabled() } returns false

            // Group call 2 receives updated
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(emptySet())

            // Group call feature gets enabled again while group call 2 is still running
            every { ConfigUtils.isGroupCallsEnabled() } returns true

            // Group call 2 receives updated
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(setOf(groupCallUiModel2))

            // Group call 2 receives redundant updated (no new ui model will be emitted)
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )

            // User joins group call 2
            every { groupCallManager.isJoinedCall(groupCallDescription2.callId) } returns true
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(setOf(groupCallUiModel2.copy(isJoined = true)))

            // User leaves group call 2
            every { groupCallManager.isJoinedCall(groupCallDescription2.callId) } returns false
            runningCallsFlow.emit(
                mapOf(
                    groupCallDescription2.callId to groupCallDescription2,
                ),
            )
            expectItem(setOf(groupCallUiModel2.copy(isJoined = false)))

            // Group call 2 ends
            runningCallsFlow.emit(emptyMap())
            expectItem(emptySet())
        }
    }

    private companion object {

        val groupCallDescription1 = createGroupCallDescription(localGroupIdValue = 1)
        val groupCallUiModel1 = GroupCallUiModel(
            id = groupCallDescription1.callId,
            groupId = groupCallDescription1.groupId,
            startedAt = groupCallDescription1.startedAt.toLong(),
            processedAt = groupCallDescription1.processedAt.toLong(),
            isJoined = false,
        )
        val groupCallDescription2 = createGroupCallDescription(localGroupIdValue = 2)
        val groupCallUiModel2 = GroupCallUiModel(
            id = groupCallDescription2.callId,
            groupId = groupCallDescription2.groupId,
            startedAt = groupCallDescription2.startedAt.toLong(),
            processedAt = groupCallDescription2.processedAt.toLong(),
            isJoined = false,
        )

        private fun createGroupCallDescription(
            localGroupIdValue: Int,
            callId: CallId = CallId(
                bytes = nonSecureRandomArray(
                    length = 32,
                ),
            ),
        ) = GroupCallDescription(
            protocolVersion = 1u,
            groupId = LocalGroupId(
                id = localGroupIdValue,
            ),
            sfuBaseUrl = "",
            callId = callId,
            gck = emptyByteArray(),
            startedAt = now().time.toULong(),
            processedAt = now().time.toULong(),
        )
    }

    @AfterTest
    fun teardown() {
        unmockkStatic(ConfigUtils::class)
    }
}
