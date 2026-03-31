package ch.threema.app.preference.usecases

import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.listeners.DistributionListListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ListenerProvider
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

class RemoveAllPrivateMarksUseCaseTest {

    @Test
    fun `remove private marks`() {
        val privateContactReceiver = mockk<ContactMessageReceiver> {
            every { uniqueIdString } returns "ABCD1234"
            every { contact } returns mockk {
                every { identity } returns "ABCD1234"
            }
        }
        val nonPrivateContactReceiver = mockk<ContactMessageReceiver> {
            every { uniqueIdString } returns "EFGH5678"
        }
        val privateGroupReceiver = mockk<GroupMessageReceiver> {
            every { uniqueIdString } returns "group1"
            every { group } returns mockk {
                every { creatorIdentity } returns "ABCD1234"
                every { apiGroupId } returns GroupId(123L)
            }
        }
        val nonPrivateGroupReceiver = mockk<GroupMessageReceiver> {
            every { uniqueIdString } returns "group2"
        }
        val distributionListMock = mockk<DistributionListModel>()
        val privateDistributionListReceiver = mockk<DistributionListMessageReceiver> {
            every { uniqueIdString } returns "list1"
            every { distributionList } returns distributionListMock
        }
        val nonPrivateDistributionListReceiver = mockk<DistributionListMessageReceiver> {
            every { uniqueIdString } returns "list2"
        }
        val conversationCategoryServiceMock = mockk<ConversationCategoryService> {
            every { removePrivateMark(privateContactReceiver) } returns true
            every { removePrivateMark(nonPrivateContactReceiver) } returns false
            every { removePrivateMark(privateGroupReceiver) } returns true
            every { removePrivateMark(nonPrivateGroupReceiver) } returns false
            every { removePrivateMark(privateDistributionListReceiver) } returns true
            every { removePrivateMark(nonPrivateDistributionListReceiver) } returns false
        }
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val widgetUpdaterMock = mockk<WidgetUpdater>(relaxed = true)
        val conversationListenerMock = mockk<ConversationListener>(relaxed = true)
        val contactListenerMock = mockk<ContactListener>(relaxed = true)
        val groupListenerMock = mockk<GroupListener>(relaxed = true)
        val distributionListListenerMock = mockk<DistributionListListener>(relaxed = true)
        val conversationListenersMock = mockk<ListenerManager.TypedListenerManager<ConversationListener>> {
            every { handle(any()) } answers {
                firstArg<ListenerManager.HandleListener<ConversationListener>>().handle(conversationListenerMock)
            }
        }
        val contactListenersMock = mockk<ListenerManager.TypedListenerManager<ContactListener>> {
            every { handle(any()) } answers {
                firstArg<ListenerManager.HandleListener<ContactListener>>().handle(contactListenerMock)
            }
        }
        val groupListenersMock = mockk<ListenerManager.TypedListenerManager<GroupListener>> {
            every { handle(any()) } answers {
                firstArg<ListenerManager.HandleListener<GroupListener>>().handle(groupListenerMock)
            }
        }
        val distributionListListenersMock = mockk<ListenerManager.TypedListenerManager<DistributionListListener>> {
            every { handle(any()) } answers {
                firstArg<ListenerManager.HandleListener<DistributionListListener>>().handle(distributionListListenerMock)
            }
        }
        val listenerProviderMock = mockk<ListenerProvider> {
            every { conversationListeners } returns conversationListenersMock
            every { contactListeners } returns contactListenersMock
            every { groupListeners } returns groupListenersMock
            every { distributionListListeners } returns distributionListListenersMock
        }
        val useCase = RemoveAllPrivateMarksUseCase(
            conversationService = mockk {
                every { getAll(false) } returns listOf(
                    ConversationModel(nonPrivateContactReceiver),
                    ConversationModel(privateGroupReceiver),
                    ConversationModel(nonPrivateGroupReceiver),
                    ConversationModel(privateDistributionListReceiver),
                    ConversationModel(nonPrivateDistributionListReceiver),
                )
                every { archived } returns listOf(
                    ConversationModel(privateContactReceiver),
                )
            },
            conversationCategoryService = conversationCategoryServiceMock,
            preferenceService = preferenceServiceMock,
            widgetUpdater = widgetUpdaterMock,
            listenerProvider = listenerProviderMock,
        )

        useCase.call()

        verify(exactly = 1) { preferenceServiceMock.setArePrivateChatsHidden(false) }
        verify(exactly = 1) { widgetUpdaterMock.updateWidgets() }
        verify(exactly = 1) { conversationListenerMock.onModifiedAll() }
        verify(exactly = 1) { contactListenerMock.onModified("ABCD1234") }
        verify(exactly = 1) { groupListenerMock.onUpdate(GroupIdentity("ABCD1234", 123L)) }
        verify(exactly = 1) { distributionListListenerMock.onModify(distributionListMock) }
    }

    @Test
    fun `nothing happens when there are no private conversations`() {
        val nonPrivateContactReceiver = mockk<ContactMessageReceiver>()
        val nonPrivateGroupReceiver = mockk<GroupMessageReceiver>()
        val nonPrivateDistributionListReceiver = mockk<DistributionListMessageReceiver>()
        val conversationCategoryServiceMock = mockk<ConversationCategoryService> {
            every { removePrivateMark(nonPrivateContactReceiver) } returns false
            every { removePrivateMark(nonPrivateGroupReceiver) } returns false
            every { removePrivateMark(nonPrivateDistributionListReceiver) } returns false
        }
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val widgetUpdaterMock = mockk<WidgetUpdater>(relaxed = true)
        val useCase = RemoveAllPrivateMarksUseCase(
            conversationService = mockk {
                every { getAll(false) } returns listOf(
                    ConversationModel(nonPrivateContactReceiver),
                    ConversationModel(nonPrivateGroupReceiver),
                    ConversationModel(nonPrivateDistributionListReceiver),
                )
                every { archived } returns emptyList()
            },
            conversationCategoryService = conversationCategoryServiceMock,
            preferenceService = preferenceServiceMock,
            widgetUpdater = widgetUpdaterMock,
            listenerProvider = mockk(),
        )

        useCase.call()

        verify(exactly = 0) { preferenceServiceMock.setArePrivateChatsHidden(false) }
        verify(exactly = 0) { widgetUpdaterMock.updateWidgets() }
    }
}
