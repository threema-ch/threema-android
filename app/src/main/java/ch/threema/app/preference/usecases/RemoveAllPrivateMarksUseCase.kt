package ch.threema.app.preference.usecases

import ch.threema.app.listeners.ConversationListener
import ch.threema.app.managers.ListenerProvider
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.models.GroupIdentity

class RemoveAllPrivateMarksUseCase(
    private val conversationService: ConversationService,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
    private val widgetUpdater: WidgetUpdater,
    private val listenerProvider: ListenerProvider,
) {
    fun call() {
        val messageReceivers = conversationService.getAll(false)
            .plus(conversationService.archived)
            .map { conversation -> conversation.messageReceiver }
        var hadPrivateConversations = false
        messageReceivers.forEach { messageReceiver ->
            if (conversationCategoryService.removePrivateMark(messageReceiver)) {
                fireReceiverUpdate(messageReceiver)
                hadPrivateConversations = true
            }
        }
        if (hadPrivateConversations) {
            preferenceService.setArePrivateChatsHidden(false)
            widgetUpdater.updateWidgets()
            listenerProvider.conversationListeners.handle(ConversationListener::onModifiedAll)
        }
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is ContactMessageReceiver -> {
                listenerProvider.contactListeners.handle { listener ->
                    listener.onModified(receiver.contact.identity)
                }
            }
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                listenerProvider.groupListeners.handle { listener -> listener.onUpdate(groupIdentity) }
            }
            is DistributionListMessageReceiver -> {
                listenerProvider.distributionListListeners.handle { listener ->
                    listener.onModify(receiver.distributionList)
                }
            }
        }
    }
}
