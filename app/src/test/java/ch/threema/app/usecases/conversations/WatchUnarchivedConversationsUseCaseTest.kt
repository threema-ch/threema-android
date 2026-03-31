package ch.threema.app.usecases.conversations

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.app.test.unconfinedTestDispatcherProvider
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchUnarchivedConversationsUseCaseTest {

    @Test
    fun `emits correct values`() = runTest {
        // arrange
        val contactConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
        val groupConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
        val distributionListConversation = TestData.createDistributionListConversationModel(distributionListId = 1L)
        val initialConversations = listOf(contactConversation, groupConversation, distributionListConversation)
        val conversationService: ConversationService = mockk {
            every { getAll(any()) } returns initialConversations
        }
        val useCase = WatchUnarchivedConversationsUseCase(
            conversationService = conversationService,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act/assert
        useCase.call().test {
            // Expect the current items
            expectItem(initialConversations)

            // Adding a new conversation
            val newConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
            every { conversationService.getAll(any()) } returns initialConversations + newConversation
            ListenerManager.conversationListeners.handle { it.onNew(newConversation) }
            expectItem(initialConversations + newConversation)

            // Modifying an existing conversation
            val modifiedConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
            every { conversationService.getAll(any()) } returns listOf(
                contactConversation,
                modifiedConversation,
                distributionListConversation,
            )
            ListenerManager.conversationListeners.handle { it.onModified(modifiedConversation) }
            expectItem(listOf(contactConversation, modifiedConversation, distributionListConversation))

            // Modifying all existing conversations
            every { conversationService.getAll(any()) } returns initialConversations
            ListenerManager.conversationListeners.handle { it.onModifiedAll() }
            expectItem(initialConversations)

            // Removing an existing conversation
            every { conversationService.getAll(any()) } returns listOf(groupConversation, distributionListConversation)
            ListenerManager.conversationListeners.handle { it.onRemoved(contactConversation) }
            expectItem(listOf(groupConversation, distributionListConversation))
        }

        // Verify that the global listener was cleaned up
        assertEquals(0, ListenerManager.conversationListeners.size())
    }
}
