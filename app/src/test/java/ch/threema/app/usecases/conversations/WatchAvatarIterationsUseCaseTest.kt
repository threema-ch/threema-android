package ch.threema.app.usecases.conversations

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchAvatarIterationsUseCaseTest {

    @Test
    fun `should emit current value`() = runTest {
        // arrange
        val groupModelRepositoryMock = mockk<GroupModelRepository>()
        val contactConversation = TestData.createContactConversationModel(
            identity = TestData.Identities.OTHER_1,
        )
        val groupConversation = TestData.createGroupConversationModel(
            groupDatabaseId = 1L,
            groupModelRepositoryMock = groupModelRepositoryMock,
        )
        val conversationService = mockk<ConversationService> {
            every { getAll(false) } returns listOf(contactConversation, groupConversation)
        }
        val useCase = WatchAvatarIterationsUseCase(
            conversationService = conversationService,
            groupModelRepository = groupModelRepositoryMock,
        )

        // act / assert
        useCase.call().test {
            // Expect the initial items
            expectItem(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to AvatarIteration.initial,
                    groupConversation.receiverModel.identifier to AvatarIteration.initial,
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should increment contact receiver iteration`() = runTest {
        // arrange
        val groupModelRepositoryMock = mockk<GroupModelRepository>()
        val contactConversation = TestData.createContactConversationModel(
            identity = TestData.Identities.OTHER_1,
        )
        val groupConversation = TestData.createGroupConversationModel(
            groupDatabaseId = 1L,
            groupModelRepositoryMock = groupModelRepositoryMock,
        )
        val conversationService = mockk<ConversationService> {
            every { getAll(false) } returns listOf(contactConversation, groupConversation)
        }
        val useCase = WatchAvatarIterationsUseCase(
            conversationService = conversationService,
            groupModelRepository = groupModelRepositoryMock,
        )

        // act / assert
        useCase.call().test {
            // Expect the initial items
            expectItem(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to AvatarIteration.initial,
                    groupConversation.receiverModel.identifier to AvatarIteration.initial,
                ),
            )

            // Avatar of an existing contact changed
            ListenerManager.contactListeners.handle {
                it.onAvatarChanged(TestData.Identities.OTHER_1.value)
            }
            val nextIterationsMap1 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 1,
                    groupConversation.receiverModel.identifier to 0,
                ),
                actual = nextIterationsMap1,
            )

            // Adding a new contact and changing its avatar
            val newContactConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_2)
            every { conversationService.getAll(false) } returns listOf(
                contactConversation,
                newContactConversation,
                groupConversation,
            )
            ListenerManager.contactListeners.handle {
                it.onAvatarChanged(TestData.Identities.OTHER_2.value)
            }
            val nextIterationsMap2 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 1,
                    newContactConversation.receiverModel.identifier to 1,
                    groupConversation.receiverModel.identifier to 0,
                ),
                actual = nextIterationsMap2,
            )

            // A contact sync was completed
            ListenerManager.synchronizeContactsListeners.handle {
                it.onFinished(null)
            }
            val nextIterationsMap3 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 2,
                    newContactConversation.receiverModel.identifier to 2,
                    groupConversation.receiverModel.identifier to 0,
                ),
                actual = nextIterationsMap3,
            )

            // The user changed the setting to show default avatars in color
            ListenerManager.contactSettingsListeners.handle {
                it.onIsDefaultContactPictureColoredChanged(isColored = false)
            }
            val nextIterationsMap4 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 3,
                    newContactConversation.receiverModel.identifier to 3,
                    groupConversation.receiverModel.identifier to 0,
                ),
                actual = nextIterationsMap4,
            )

            // The user changed the setting to show contact defined avatars
            ListenerManager.contactSettingsListeners.handle {
                it.onShowContactDefinedAvatarsChanged(shouldShow = false)
            }
            val nextIterationsMap5 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 4,
                    newContactConversation.receiverModel.identifier to 4,
                    groupConversation.receiverModel.identifier to 0,
                ),
                actual = nextIterationsMap5,
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should increment group receiver iteration`() = runTest {
        // arrange
        val groupModelRepositoryMock = mockk<GroupModelRepository>()
        val contactConversation = TestData.createContactConversationModel(
            identity = TestData.Identities.OTHER_1,
        )
        val groupConversation = TestData.createGroupConversationModel(
            groupDatabaseId = 1L,
            groupModelRepositoryMock = groupModelRepositoryMock,
        )
        val conversationServiceMock = mockk<ConversationService> {
            every { getAll(false) } returns listOf(contactConversation, groupConversation)
        }
        val useCase = WatchAvatarIterationsUseCase(
            conversationService = conversationServiceMock,
            groupModelRepository = groupModelRepositoryMock,
        )

        // act / assert
        useCase.call().test {
            // Expect the initial items
            expectItem(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to AvatarIteration.initial,
                    groupConversation.receiverModel.identifier to AvatarIteration.initial,
                ),
            )

            // Group photo of an existing group was changed
            ListenerManager.groupListeners.handle {
                it.onUpdatePhoto(groupConversation.groupModel!!.groupIdentity)
            }
            val nextIterationsMap1 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 0,
                    groupConversation.receiverModel.identifier to 1,
                ),
                actual = nextIterationsMap1,
            )

            // Group photo of a new group was changed
            val newGroupConversation = TestData.createGroupConversationModel(
                groupDatabaseId = 2L,
                groupModelRepositoryMock = groupModelRepositoryMock,
            )
            every { conversationServiceMock.getAll(false) } returns listOf(
                contactConversation,
                groupConversation,
                newGroupConversation,
            )
            ListenerManager.groupListeners.handle {
                it.onUpdatePhoto(newGroupConversation.groupModel!!.groupIdentity)
            }
            val nextIterationsMap2 = awaitItem()
                .mapValues { mapEntry ->
                    // unbox AvatarIteration value class for asserting
                    mapEntry.value.value
                }
            assertEquals(
                expected = mapOf(
                    contactConversation.receiverModel.identifier to 0,
                    groupConversation.receiverModel.identifier to 1,
                    newGroupConversation.receiverModel.identifier to 1,
                ),
                actual = nextIterationsMap2,
            )

            // A group photo listener event with an unknown group identity
            val unknownGroupIdentity = GroupIdentity(creatorIdentity = "00000000", groupId = GroupId().toLong())
            every { groupModelRepositoryMock.getByGroupIdentity(unknownGroupIdentity) } returns null
            ListenerManager.groupListeners.handle {
                it.onUpdatePhoto(unknownGroupIdentity)
            }
            expectNoEvents()

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit fallback value on internal error`() = runTest {
        // arrange
        val conversationService = mockk<ConversationService> {
            every { getAll(false) } throws IllegalStateException("Test")
        }
        val useCase = WatchAvatarIterationsUseCase(
            conversationService = conversationService,
            groupModelRepository = mockk<GroupModelRepository>(),
        )

        // act / assert
        useCase.call().test {
            // Expect the empty fallback map
            expectItem(
                expected = emptyMap(),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }
}
