package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import ch.threema.domain.models.IdentityState
import ch.threema.storage.models.ConversationModel

const val INACTIVE_CONTACT_ALPHA = 0.6f

@Immutable
data class ConversationNameStyle(
    val strikethrough: Boolean = false,
    val dimAlpha: Boolean = false,
) {
    companion object {
        @Stable
        fun inactiveContact() = ConversationNameStyle(
            strikethrough = false,
            dimAlpha = true,
        )

        @Stable
        fun invalidContact() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        @Stable
        fun groupNotAMemberOf() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        fun forConversationModel(conversationModel: ConversationModel): ConversationNameStyle =
            if (conversationModel.isContactConversation) {
                conversationModel.contact?.let { contact ->
                    when (contact.state) {
                        IdentityState.INACTIVE -> inactiveContact()
                        IdentityState.INVALID -> invalidContact()
                        else -> null
                    }
                } ?: ConversationNameStyle()
            } else {
                if (conversationModel.groupModel?.isMember() == false) {
                    groupNotAMemberOf()
                } else {
                    ConversationNameStyle()
                }
            }
    }
}
