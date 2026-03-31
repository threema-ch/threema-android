package ch.threema.app.fragments.conversations

import androidx.annotation.StringRes
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.storage.models.ConversationModel

sealed interface ConversationsViewEvent {
    data class OpenConversationActionDialog(val conversationModel: ConversationModel) : ConversationsViewEvent
    data class ConversationArchived(val conversationModel: ConversationModel) : ConversationsViewEvent

    /**
     *  @param targetValueIsMarkedAsPrivate Whether the intent is to mark the conversation as private, or un-mark it
     */
    data class LockingMechanismRequiredToUpdatePrivateConversationMark(
        val conversationModel: ConversationModel,
        val targetValueIsMarkedAsPrivate: Boolean,
    ) : ConversationsViewEvent

    data class ConfirmationRequiredToMarkConversationAsPrivate(val conversationModel: ConversationModel) : ConversationsViewEvent
    data object ConversationMarkAsPrivateSuccess : ConversationsViewEvent

    data class UnlockRequiredToUnmarkConversationAsPrivate(val conversationModel: ConversationModel) : ConversationsViewEvent
    data object ConversationUnmarkAsPrivateSuccess : ConversationsViewEvent

    data object LockingMechanismRequiredToHidePrivateConversations : ConversationsViewEvent
    data object UnlockRequiredToShowPrivateConversations : ConversationsViewEvent

    data class ConfirmationRequiredToEmptyConversation(val conversationModel: ConversationModel) : ConversationsViewEvent

    data class ConfirmationRequiredToDeleteContactConversation(val conversationModel: ConversationModel) : ConversationsViewEvent
    data class ConfirmationRequiredToDeleteDistributionListConversation(val conversationModel: ConversationModel) : ConversationsViewEvent

    data object OnSystemLockWasRemoved : ConversationsViewEvent

    data class OnSupportContactAvailable(val receiverIdentifier: ContactReceiverIdentifier) : ConversationsViewEvent
    data class OnSupportContactUnavailable(@StringRes val message: Int) : ConversationsViewEvent

    data object UpdateWidgets : ConversationsViewEvent

    data object InternalError : ConversationsViewEvent
}
