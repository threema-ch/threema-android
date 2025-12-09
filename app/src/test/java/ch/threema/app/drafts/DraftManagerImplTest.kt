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

package ch.threema.app.drafts

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.test.testDispatcherProvider
import ch.threema.domain.models.MessageId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DraftManagerImplTest {

    @Test
    fun `drafts are restored from storage`() = runTest {
        val draftManager = DraftManagerImpl(
            preferenceService = mockk {
                every { messageDrafts } returns mapOf(
                    CONVERSATION_ID1 to "Hello",
                    CONVERSATION_ID2 to "World",
                )
                every { quoteDrafts } returns mapOf(
                    CONVERSATION_ID1 to MESSAGE_ID_STRING,
                )
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        draftManager.init()

        assertEquals(
            MessageDraft(
                text = "Hello",
                quotedMessageId = MESSAGE_ID,
            ),
            draftManager.get(CONVERSATION_ID1),
        )
        assertEquals(
            MessageDraft(
                text = "World",
                quotedMessageId = null,
            ),
            draftManager.get(CONVERSATION_ID2),
        )
        assertNull(draftManager.get(CONVERSATION_ID3))
    }

    @Test
    fun `drafts can be retrieved after being set`() = runTest {
        val draftManager = DraftManagerImpl(
            preferenceService = mockk {
                every { messageDrafts } returns emptyMap()
                every { quoteDrafts } returns emptyMap()
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()

        draftManager.set(CONVERSATION_ID1, text = "Hello", quotedMessageId = MESSAGE_ID)
        draftManager.set(CONVERSATION_ID2, text = "World")

        assertEquals(
            MessageDraft(
                text = "Hello",
                quotedMessageId = MESSAGE_ID,
            ),
            draftManager.get(CONVERSATION_ID1),
        )
        assertEquals(
            MessageDraft(
                text = "World",
                quotedMessageId = null,
            ),
            draftManager.get(CONVERSATION_ID2),
        )
        assertNull(draftManager.get(CONVERSATION_ID3))
    }

    @Test
    fun `drafts are persisted when set`() = runTest {
        val preferenceServiceMock = mockk<PreferenceService> {
            every { messageDrafts } returns emptyMap()
            every { quoteDrafts } returns emptyMap()
            every { messageDrafts = any() } just runs
            every { quoteDrafts = any() } just runs
        }
        val draftManager = DraftManagerImpl(
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()
        advanceUntilIdle()

        draftManager.set(CONVERSATION_ID1, text = "Hello", quotedMessageId = MESSAGE_ID)
        draftManager.set(CONVERSATION_ID2, text = "World")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferenceServiceMock.messageDrafts = mapOf(
                CONVERSATION_ID1 to "Hello",
                CONVERSATION_ID2 to "World",
            )
        }
        verify(exactly = 1) {
            preferenceServiceMock.quoteDrafts = mapOf(
                CONVERSATION_ID1 to MESSAGE_ID_STRING,
            )
        }
    }

    @Test
    fun `drafts can be replaced and removed`() = runTest {
        val preferenceServiceMock = mockk<PreferenceService> {
            every { messageDrafts } returns mapOf(
                CONVERSATION_ID1 to "Hello",
                CONVERSATION_ID2 to "World",
            )
            every { quoteDrafts } returns mapOf(
                CONVERSATION_ID1 to MESSAGE_ID_STRING,
            )
            every { messageDrafts = any() } just runs
            every { quoteDrafts = any() } just runs
        }
        val draftManager = DraftManagerImpl(
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()
        advanceUntilIdle()

        draftManager.set(CONVERSATION_ID1, text = "HELLO!!!")
        draftManager.remove(CONVERSATION_ID2)
        advanceUntilIdle()

        verify(exactly = 1) {
            preferenceServiceMock.messageDrafts = mapOf(
                CONVERSATION_ID1 to "HELLO!!!",
            )
        }
        verify(exactly = 1) {
            preferenceServiceMock.quoteDrafts = emptyMap()
        }
    }

    companion object {
        private const val CONVERSATION_ID1 = "conv1"
        private const val CONVERSATION_ID2 = "conv2"
        private const val CONVERSATION_ID3 = "conv3"

        private const val MESSAGE_ID_STRING = "00dead0000beef00"
        private val MESSAGE_ID = MessageId.fromString(MESSAGE_ID_STRING)
    }
}
