package ch.threema.app.services.notification

import android.content.Context
import androidx.core.app.Person
import ch.threema.app.R
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.emojis.EmojiMarkupUtil
import ch.threema.app.services.notification.NotificationService.FetchCacheUri
import ch.threema.app.services.poll.PollService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.isNotNullOrBlank
import ch.threema.common.takeUnlessBlank
import ch.threema.common.toHMMSS
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.getDuration
import java.time.Instant
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("ConversationReactionNotification")

/**
 * @param emojiSequences A list of all emoji sequences for this message. This list can be empty, indicating that all reactions were withdrawn.
 */
abstract class ConversationReactionNotification(
    messageId: Int,
    messageUid: MessageUid,
    conversationHandle: NotificationConversationHandle,
    createdAt: Instant,
    thumbnailMimeType: String?,
    fetchThumbnailUri: FetchCacheUri?,
    senderPerson: Person?,
    val targetMessage: MessageModel,
    val emojiSequences: List<String>,
) : ConversationNotification(
    messageId = messageId,
    messageUid = messageUid,
    conversationHandle = conversationHandle,
    createdAt = createdAt,
    thumbnailMimeType = thumbnailMimeType,
    fetchThumbnailUri = fetchThumbnailUri,
    senderPerson = senderPerson,
),
    KoinComponent {

    private val pollService: PollService? by injectNullableNonBinding<PollService>()

    val anyEmojiReactionExists: Boolean = emojiSequences.isNotEmpty()

    override val shouldApplySenderPerson: Boolean
        get() = true

    /**
     * "Public" meaning that this returned content will be presented in the public version of the notification.
     */
    abstract fun resolveNotificationTitlePublic(context: Context): String?

    /**
     * "Private" meaning that this returned content will be presented in the private version of the notification.
     */
    abstract fun resolveNotificationTitlePrivate(context: Context): String?

    /**
     * "Public" meaning that this returned content will be presented in the public version of the notification.
     */
    abstract fun resolveNotificationContentPublic(context: Context): String?

    /**
     * "Private" meaning that this returned content will be presented in the private version of the notification.
     */
    abstract fun resolveNotificationContentPrivate(
        context: Context,
        contactModelRepository: ContactModelRepository,
        contactNameFormat: ContactNameFormat,
    ): String?

    /**
     * Builds a preview of the message that was reacted on based on the message type.
     *
     * Examples:
     * - `Just a normal text message body.`
     * - `File "message caption"` or `File "file name"`
     * - `Poll "poll name"`
     * - `Voice message (h:mm:SS)`
     */
    fun resolveNotificationContentPreviewPrivate(context: Context): String? =
        when (targetMessage.messageContentsType) {
            MessageContentsType.TEXT -> {
                val bodyText: CharSequence = targetMessage
                    .body
                    ?.let(EmojiMarkupUtil.getInstance()::addTextSpans)
                    ?: return null
                context.getString(R.string.emoji_reaction_notification_preview_text, bodyText)
            }
            MessageContentsType.IMAGE, MessageContentsType.GIF -> {
                if (targetMessage.caption.isNotNullOrBlank()) {
                    context.getString(R.string.emoji_reaction_notification_preview_image_caption, targetMessage.caption)
                } else {
                    context.getString(R.string.emoji_reaction_notification_preview_image)
                }
            }
            MessageContentsType.VIDEO -> {
                if (targetMessage.caption.isNotNullOrBlank()) {
                    context.getString(R.string.emoji_reaction_notification_preview_video_caption, targetMessage.caption)
                } else {
                    context.getString(R.string.emoji_reaction_notification_preview_video)
                }
            }
            MessageContentsType.AUDIO -> {
                if (targetMessage.caption.isNotNullOrBlank()) {
                    context.getString(R.string.emoji_reaction_notification_preview_audio_caption, targetMessage.caption)
                } else {
                    val durationString = targetMessage.fileData.getDuration().toHMMSS()
                    context.getString(R.string.emoji_reaction_notification_preview_audio, durationString)
                }
            }
            MessageContentsType.VOICE_MESSAGE -> {
                val durationString = targetMessage.fileData.getDuration().toHMMSS()
                context.getString(R.string.emoji_reaction_notification_preview_voice_message, durationString)
            }
            MessageContentsType.CONTACT -> {
                val contactName = targetMessage.caption
                if (contactName.isNotNullOrBlank()) {
                    context.getString(R.string.emoji_reaction_notification_preview_contact_named, contactName)
                } else {
                    context.getString(R.string.emoji_reaction_notification_preview_contact)
                }
            }
            MessageContentsType.FILE -> {
                context.getString(
                    R.string.emoji_reaction_notification_preview_file,
                    if (targetMessage.caption.isNotNullOrBlank()) {
                        targetMessage.caption
                    } else {
                        targetMessage.fileData.fileName
                    },
                )
            }
            MessageContentsType.POLL -> {
                val pollName = targetMessage.pollData
                    .let { pollDataModel -> pollService?.get(pollDataModel.pollId) }
                    ?.name
                    ?.takeUnlessBlank()
                    ?: return context.getString(R.string.emoji_reaction_notification_preview_poll)
                context.getString(R.string.emoji_reaction_notification_preview_poll_named, pollName)
            }
            MessageContentsType.LOCATION -> {
                targetMessage
                    .locationData
                    .poiNameOrNull
                    ?.let { poiName -> context.getString(R.string.emoji_reaction_notification_preview_location_named, poiName) }
                    ?: context.getString(R.string.emoji_reaction_notification_preview_location)
            }
            else -> null
        }

    companion object {
        const val MAX_VISIBLE_EMOJIS = 3
    }
}

class ConversationContactReactionNotification(
    messageId: Int,
    messageUid: MessageUid,
    conversationHandle: NotificationConversationHandle,
    createdAt: Instant,
    senderPerson: Person?,
    thumbnailMimeType: String?,
    fetchThumbnailUri: FetchCacheUri?,
    targetMessage: ContactMessageModel,
    emojiSequences: List<String>,
) : ConversationReactionNotification(
    messageId = messageId,
    messageUid = messageUid,
    conversationHandle = conversationHandle,
    createdAt = createdAt,
    senderPerson = senderPerson,
    thumbnailMimeType = thumbnailMimeType,
    fetchThumbnailUri = fetchThumbnailUri,
    targetMessage = targetMessage,
    emojiSequences = emojiSequences,
) {

    override fun resolveNotificationTitlePublic(context: Context): String? {
        if (emojiSequences.isEmpty()) {
            return null
        }
        return context.getString(R.string.private_chat_subject)
    }

    override fun resolveNotificationTitlePrivate(context: Context): String? {
        if (emojiSequences.isEmpty()) {
            return null
        }
        return conversationHandle.name.get(context)
    }

    override fun resolveNotificationContentPublic(context: Context): String? {
        if (emojiSequences.isEmpty()) {
            return null
        }
        return context.resources.getQuantityString(
            /* id = */
            R.plurals.new_emoji_reactions,
            /* quantity = */
            emojiSequences.size,
            /* ...formatArgs = */
            emojiSequences.size,
        )
    }

    override fun resolveNotificationContentPrivate(
        context: Context,
        contactModelRepository: ContactModelRepository,
        contactNameFormat: ContactNameFormat,
    ): String? {
        if (emojiSequences.isEmpty()) {
            return null
        }
        val emojiSequence = emojiSequences
            .take(MAX_VISIBLE_EMOJIS)
            .joinToString(
                separator = "",
            )
        val preview = resolveNotificationContentPreviewPrivate(context)
            ?: ""
        return if (emojiSequences.size > MAX_VISIBLE_EMOJIS) {
            context.getString(
                /* resId = */
                R.string.emoji_reaction_notification_body_contact_more,
                /* ...formatArgs = */
                emojiSequence,
                emojiSequences.size - MAX_VISIBLE_EMOJIS,
                preview,
            )
        } else {
            context.getString(
                /* resId = */
                R.string.emoji_reaction_notification_body_contact,
                /* ...formatArgs = */
                emojiSequence,
                preview,
            )
        }
    }
}

class ConversationGroupReactionNotification(
    messageId: Int,
    messageUid: MessageUid,
    conversationHandle: NotificationConversationHandle,
    createdAt: Instant,
    thumbnailMimeType: String?,
    fetchThumbnailUri: FetchCacheUri?,
    targetMessage: GroupMessageModel,
    emojiSequences: List<String>,
    private val reactorIdentities: List<IdentityString>,
) : ConversationReactionNotification(
    messageId = messageId,
    messageUid = messageUid,
    conversationHandle = conversationHandle,
    createdAt = createdAt,
    senderPerson = null,
    thumbnailMimeType = thumbnailMimeType,
    fetchThumbnailUri = fetchThumbnailUri,
    targetMessage = targetMessage,
    emojiSequences = emojiSequences,
) {

    override fun resolveNotificationTitlePublic(context: Context): String? {
        if (!anyEmojiReactionExists) {
            return null
        }
        return context.getString(R.string.private_chat_subject)
    }

    override fun resolveNotificationTitlePrivate(context: Context): String? {
        if (!anyEmojiReactionExists) {
            return null
        }
        return conversationHandle.name.get(context)
    }

    override fun resolveNotificationContentPublic(context: Context): String? {
        if (!anyEmojiReactionExists) {
            return null
        }
        return context.resources.getQuantityString(
            /* id = */
            R.plurals.new_emoji_reactions,
            /* quantity = */
            emojiSequences.size,
            /* ...formatArgs = */
            emojiSequences.size,
        )
    }

    override fun resolveNotificationContentPrivate(
        context: Context,
        contactModelRepository: ContactModelRepository,
        contactNameFormat: ContactNameFormat,
    ): String? {
        if (!anyEmojiReactionExists) {
            return null
        }
        val preview = resolveNotificationContentPreviewPrivate(context)
            ?: ""
        val distinctReactorIdentities = reactorIdentities.distinct()
        return when (distinctReactorIdentities.size) {
            0 -> {
                logger.error("Missing a reactor identity for emoji reaction notification for message {}", messageUid)
                null
            }
            1 -> {
                val reactorName = contactModelRepository
                    .getByIdentity(distinctReactorIdentities[0])
                    ?.data
                    ?.getShortName()
                if (reactorName == null) {
                    return onMissingReactorContact(distinctReactorIdentities[0])
                }
                val emojiSequence = emojiSequences
                    .take(MAX_VISIBLE_EMOJIS)
                    .joinToString(
                        separator = "",
                    )
                if (emojiSequences.size > MAX_VISIBLE_EMOJIS) {
                    context.getString(
                        R.string.emoji_reaction_notification_body_group_one_person_more,
                        reactorName,
                        emojiSequence,
                        emojiSequences.size - MAX_VISIBLE_EMOJIS,
                        preview,
                    )
                } else {
                    context.getString(
                        R.string.emoji_reaction_notification_body_group_one_person,
                        reactorName,
                        emojiSequence,
                        preview,
                    )
                }
            }
            2 -> {
                val reactorIdentityFirst = distinctReactorIdentities[0]
                val reactorIdentitySecond = distinctReactorIdentities[1]
                val reactorNameFirst = contactModelRepository
                    .getByIdentity(reactorIdentityFirst)
                    ?.data
                    ?.getShortName()
                val reactorNameSecond = contactModelRepository
                    .getByIdentity(reactorIdentitySecond)
                    ?.data
                    ?.getShortName()
                if (reactorNameFirst == null && reactorNameSecond == null) {
                    logger.error("Missing reactor contacts {} and {}", reactorIdentityFirst, reactorIdentitySecond)
                    return null
                }
                context.getString(
                    /* resId = */
                    R.string.emoji_reaction_notification_body_group_two_persons,
                    /* ...formatArgs = */
                    reactorNameFirst ?: reactorIdentityFirst,
                    reactorNameSecond ?: reactorIdentitySecond,
                    preview,
                )
            }
            else -> {
                val reactorFirstName = contactModelRepository
                    .getByIdentity(distinctReactorIdentities[0])
                    ?.data
                    ?.getShortName()
                if (reactorFirstName == null) {
                    return onMissingReactorContact(distinctReactorIdentities[0])
                }
                context.getString(
                    /* resId = */
                    R.string.emoji_reaction_notification_body_group_more_persons,
                    /* ...formatArgs = */
                    reactorFirstName,
                    distinctReactorIdentities.size - 1,
                    preview,
                )
            }
        }
    }

    private fun onMissingReactorContact(identity: IdentityString): String? {
        logger.error("Cannot find reactor contact {}", identity)
        return null
    }
}
