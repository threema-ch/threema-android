package ch.threema.storage.models

import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.utils.ConversationUtil.getContactConversationUid
import ch.threema.app.utils.ConversationUtil.getDistributionListConversationUid
import ch.threema.app.utils.ConversationUtil.getGroupConversationUid
import ch.threema.data.models.GroupModel
import ch.threema.domain.types.ConversationUID
import ch.threema.storage.models.group.GroupModelOld
import java.util.Date

class ConversationModel(
    @JvmField var messageReceiver: MessageReceiver<*>,
) {
    val isContactConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_CONTACT

    val isGroupConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_GROUP

    val isDistributionListConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_DISTRIBUTION_LIST

    val contact: ContactModel?
        get() = when {
            isContactConversation -> (messageReceiver as ContactMessageReceiver).contact
            else -> null
        }

    val group: GroupModelOld?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).group
            else -> null
        }

    val groupModel: GroupModel?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).groupModel
            else -> null
        }

    val distributionList: DistributionListModel?
        get() = when {
            isDistributionListConversation -> (messageReceiver as DistributionListMessageReceiver).distributionList
            else -> null
        }

    @JvmField
    var messageCount: Long = 0L

    @JvmField
    var latestMessage: AbstractMessageModel? = null

    var unreadCount: Long = 0L
        set(value) {
            field = value
            if (value == 0L) {
                isUnreadTagged = false
            }
        }

    @JvmField
    var isUnreadTagged: Boolean = false

    @JvmField
    var isArchived: Boolean = false

    val uid: ConversationUID
        get() = when {
            isContactConversation -> getContactConversationUid(contact!!.identity)
            isGroupConversation -> getGroupConversationUid(group!!.id.toLong())
            isDistributionListConversation -> getDistributionListConversationUid(distributionList!!.id)
            else -> error("Can not determine uid of conversation model for receiver od type ${messageReceiver.type}")
        }

    // Only used by the web-client
    var position: Int = -1

    @JvmField
    var lastUpdate: Date? = null

    @JvmField
    var isTyping: Boolean = false

    @JvmField
    var isPinTagged: Boolean = false

    /**
     * @return Return the date used for sorting. Corresponds to [lastUpdate] if set.
     */
    val sortDate: Date
        get() = when {
            lastUpdate != null -> lastUpdate!!
            else -> Date(0)
        }

    fun hasUnreadMessage(): Boolean = unreadCount > 0

    val receiverModel: ReceiverModel
        get() = contact ?: group ?: distributionList ?: throw IllegalStateException("ConversationModel is missing a ReceiverModel")

    override fun toString(): String = receiverModel.identifier.toString()
}
