package ch.threema.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import ch.threema.android.buildIntent
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.MessageService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.storage.models.ConversationModel
import java.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("WidgetViewsFactory")

class WidgetViewsFactory(private val context: Context) : RemoteViewsFactory, KoinComponent {

    private val conversationService: ConversationService? by injectNullableNonBinding()
    private val preferenceService: PreferenceService? by injectNullableNonBinding()
    private val lockAppService: LockAppService? by injectNullableNonBinding()
    private val contactService: ContactService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()
    private val distributionListService: DistributionListService? by injectNullableNonBinding()
    private val conversationCategoryService: ConversationCategoryService? by injectNullableNonBinding()
    private val messageService: MessageService? by injectNullableNonBinding()
    private val notificationPreferenceService: NotificationPreferenceService by inject()
    private val masterKeyManager: MasterKeyManager by inject()

    private var conversations: List<ConversationModel>? = null

    override fun onCreate() {}

    override fun onDataSetChanged() {
        logger.info("onDataSetChanged called")
        try {
            val conversationService = conversationService
            val preferenceService = preferenceService

            if (conversationService == null || preferenceService == null) {
                logger.info("Services unavailable, showing nothing")
                conversations = emptyList()
                return
            }

            if (masterKeyManager.isProtectedWithRemoteSecret() == true) {
                // While Remote Secret protection is active, the widget is fully disabled to avoid the risk of leaking chat content
                conversations = emptyList()
                return
            }

            conversations = conversationService.getAll(
                /* forceReloadFromDatabase = */
                false,
                /* filter = */
                object : ConversationService.Filter {
                    override fun onlyUnread() = true

                    override fun noHiddenChats() = preferenceService.arePrivateChatsHidden()
                },
            )
            logger.info("Conversations updated")
        } catch (e: Exception) {
            logger.error("Failed to get conversations for widget", e)
            conversations = emptyList()
        }
    }

    override fun getCount(): Int {
        val lockAppService = lockAppService ?: return 0
        return try {
            if (!lockAppService.isLocked() && notificationPreferenceService.isShowMessagePreview()) {
                conversations?.size ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            logger.error("Failed to get count for widget", e)
            0
        }
    }

    override fun getViewAt(position: Int): RemoteViews? {
        val lockAppService = lockAppService ?: return null
        val contactService = contactService ?: return null
        val groupService = groupService ?: return null
        val distributionListService = distributionListService ?: return null
        val conversationCategoryService = conversationCategoryService ?: return null
        val messageService = messageService ?: return null

        val conversation = conversations?.getOrNull(position) ?: return null

        var sender: String
        var message: String? = null
        var date: Instant? = null
        var count: Long? = null
        var profilePicture: Bitmap? = null
        val extras = Bundle()

        if (!lockAppService.isLocked() && notificationPreferenceService.isShowMessagePreview()) {
            sender = conversation.messageReceiver.getDisplayName(
                preferenceService?.getContactNameFormat() ?: ContactNameFormat.DEFAULT,
            )

            when {
                conversation.isContactConversation -> {
                    val contact = conversation.contact
                    val identity = contact?.identity
                    profilePicture = contactService.getAvatar(identity, false)
                    extras.putString(AppConstants.INTENT_DATA_CONTACT, identity)
                }
                conversation.isGroupConversation -> {
                    profilePicture = groupService.getAvatar(conversation.group, false)
                    extras.putLong(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, conversation.group!!.id.toLong())
                }
                conversation.isDistributionListConversation -> {
                    profilePicture = distributionListService.getAvatar(conversation.distributionList!!.id, false)
                    extras.putLong(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, conversation.distributionList!!.id)
                }
            }

            count = conversation.unreadCount

            if (conversationCategoryService.isPrivateChat(conversation.messageReceiver.getUniqueIdString())) {
                message = context.getString(R.string.private_chat_subject)
            } else {
                conversation.latestMessage?.let { latestMessage ->
                    message = messageService.getMessageString(latestMessage, 200).message
                    date = MessageUtil.getDisplayInstant(
                        /* postedAt = */
                        latestMessage.postedAt,
                        /* isOutbox = */
                        latestMessage.isOutbox,
                        /* modifiedAt = */
                        latestMessage.modifiedAt,
                    )
                }
            }
        } else {
            sender = context.getString(R.string.new_unprocessed_messages)
            message = context.getString(R.string.new_unprocessed_messages_description)
            conversation.latestMessage?.let { latestMessage ->
                date = MessageUtil.getDisplayInstant(
                    /* postedAt = */
                    latestMessage.postedAt,
                    /* isOutbox = */
                    latestMessage.isOutbox,
                    /* modifiedAt = */
                    latestMessage.modifiedAt,
                )
            }
        }

        return createItemView(
            sender = sender,
            message = message,
            date = date,
            count = count,
            profilePicture = profilePicture,
            extras = extras,
        )
    }

    private fun createItemView(
        sender: String,
        message: String?,
        date: Instant?,
        count: Long?,
        profilePicture: Bitmap?,
        extras: Bundle,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.item_widget)
            .apply {
                setTextViewText(R.id.sender_text, sender)
                setTextViewText(R.id.message_text, message ?: "")
                setTextViewText(
                    R.id.msg_date,
                    if (date != null) {
                        LocaleUtil.formatTimeStampString(context, date, false)
                    } else {
                        ""
                    },
                )
                setTextViewText(R.id.message_count, count?.toString() ?: "")
                if (profilePicture != null) {
                    setImageViewBitmap(R.id.avatar, profilePicture)
                } else {
                    setImageViewResource(R.id.avatar, R.drawable.ic_contact)
                }
                setOnClickFillInIntent(R.id.item_layout, buildIntent { putExtras(extras) })
            }

    override fun getLoadingView() = null

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = position.toLong()

    override fun hasStableIds() = true

    override fun onDestroy() {}
}
