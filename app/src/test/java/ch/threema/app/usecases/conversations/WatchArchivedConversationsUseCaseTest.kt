package ch.threema.app.usecases.conversations

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.app.test.unconfinedTestDispatcherProvider
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchArchivedConversationsUseCaseTest {

    // TODO(ANDR-4175): Test the skip of onNew for a non-archived conversation
    @Test
    fun `emits correct values and skips unnecessary changes`() = runTest {
        // arrange
        val conversationService = mockk<ConversationService>()
        val useCase = WatchArchivedConversationsUseCase(
            conversationService = conversationService,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        val archivedContactConversation = TestData.createContactConversationModel(
            identity = TestData.Identities.OTHER_1,
            isArchived = true,
        )
        val archivedGroupConversation = TestData.createGroupConversationModel(
            groupDatabaseId = 1L,
            isArchived = true,
        )
        val archivedDistributionListConversation = TestData.createDistributionListConversationModel(
            distributionListId = 1L,
            isArchived = true,
        )

        every { conversationService.archived } returns listOf(
            archivedContactConversation,
            archivedGroupConversation,
            archivedDistributionListConversation,
        )

        // act / assert
        useCase.call().test {
            // Expect the current items
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation))

            // Adding a new archived conversation
            val newArchivedConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1, isArchived = true)
            every { conversationService.archived } returns listOf(
                archivedContactConversation,
                archivedGroupConversation,
                archivedDistributionListConversation,
                newArchivedConversation,
            )
            ListenerManager.conversationListeners.handle { it.onNew(newArchivedConversation) }
            expectItem(
                listOf(
                    archivedContactConversation,
                    archivedGroupConversation,
                    archivedDistributionListConversation,
                    newArchivedConversation,
                ),
            )

            // Updating a non-archived conversation
            val updatedNonArchivedConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
            ListenerManager.conversationListeners.handle {
                it.onModified(updatedNonArchivedConversation)
            }
            expectNoEvents()

            // Updating an archived conversation
            val updatedArchivedConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L, isArchived = true)
            ListenerManager.conversationListeners.handle {
                it.onModified(updatedArchivedConversation)
            }
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation, newArchivedConversation))

            // Modified all
            ListenerManager.conversationListeners.handle { it.onModifiedAll() }
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation, newArchivedConversation))

            // Remove a non-archived conversation
            val removedNonArchivedConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
            ListenerManager.conversationListeners.handle { it.onRemoved(removedNonArchivedConversation) }
            expectNoEvents()

            // Remove an archived conversation
            val removedArchivedConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1, isArchived = true)
            every { conversationService.archived } returns listOf(
                archivedContactConversation,
                archivedGroupConversation,
                archivedDistributionListConversation,
            )
            ListenerManager.conversationListeners.handle { it.onRemoved(removedArchivedConversation) }
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation))
        }

        verify(exactly = 5) { conversationService.archived }

        // Verify that the global listener was cleaned up
        assertEquals(0, ListenerManager.conversationListeners.size())
    }
}
