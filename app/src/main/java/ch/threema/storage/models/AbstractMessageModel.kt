/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.storage.models

import ch.threema.app.utils.QuoteUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.storage.models.data.DisplayTag
import ch.threema.storage.models.data.LocationDataModel
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.data.MessageDataInterface
import ch.threema.storage.models.data.media.AudioDataModel
import ch.threema.storage.models.data.media.BallotDataModel
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.storage.models.data.media.ImageDataModel
import ch.threema.storage.models.data.media.VideoDataModel
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel
import ch.threema.storage.models.data.status.GroupCallStatusDataModel
import ch.threema.storage.models.data.status.GroupStatusDataModel
import ch.threema.storage.models.data.status.StatusDataModel
import ch.threema.storage.models.data.status.VoipStatusDataModel
import java.util.Date
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("AbstractMessageModel")

abstract class AbstractMessageModel
@JvmOverloads
internal constructor(
    open var isStatusMessage: Boolean = false,
) {
    /**
     * The message's id, unique per message type.
     */
    open var id: Int = 0

    /**
     * The message's uid, globally unique.
     */
    open var uid: String? = null

    /**
     * The chat protocol message id assigned by the sender.
     */
    open var apiMessageId: String? = null

    /**
     * The associated identity: Either the recipient
     * (for outgoing messages) or the sender (for incoming messages).
     */
    open var identity: String? = null

    /**
     * Whether the message is an outgoing one, i.e., sent by the user
     */
    open var isOutbox: Boolean = false
        set(value) {
            field = value
            if (value) {
                // Outgoing messages can't be unread
                isRead = true
            }
        }

    open var type: MessageType? = null

    open var correlationId: String? = null

    open var body: String? = null

    open var isRead: Boolean = false
        get() = field || isOutbox

    open var isSaved: Boolean = false

    open var state: MessageState? = null

    var rawPostedAt: Date? = null
        private set

    open var postedAt: Date?
        get() = rawPostedAt ?: createdAt
        set(value) {
            rawPostedAt = value
        }

    open var createdAt: Date? = null

    open var deliveredAt: Date? = null

    open var readAt: Date? = null

    open var modifiedAt: Date? = null

    open var editedAt: Date? = null

    open var deletedAt: Date? = null

    var caption: String? = null
        get() = when (type) {
            MessageType.FILE -> fileData.caption
            MessageType.LOCATION -> locationData.poi?.getCaptionOrNull()
            else -> field
        }

    var quotedMessageId: String? = null

    @MessageContentsType
    var messageContentsType: Int = 0

    var messageFlags: Int = 0

    var forwardSecurityMode: ForwardSecurityMode? = null

    @DisplayTag
    var displayTags: Int = 0

    var bodyAndQuotedMessageId: String?
        get() = body
            ?.let { body ->
                quotedMessageId?.let { quotedMessageId ->
                    QuoteUtil.quote(body, quotedMessageId)
                }
                    ?: body
            }
        set(value) {
            if (QuoteUtil.isQuoteV2(value)) {
                QuoteUtil.addBodyAndQuotedMessageId(this, value)
            } else {
                body = value
                quotedMessageId = null
            }
        }

    val isDeleted: Boolean
        get() = deletedAt != null

    protected var dataObject: MessageDataInterface? = null

    var locationData: LocationDataModel
        get() = getOrSetDataModel(MessageType.LOCATION, LocationDataModel::fromStringOrDefault)
            ?: emptyLocationDataModel
        set(value) {
            setDataModel(MessageType.LOCATION, value)
        }

    var videoData: VideoDataModel
        get() = getOrSetDataModel(MessageType.VIDEO, VideoDataModel::create)
            ?: emptyVideoDataModel
        set(value) {
            setDataModel(MessageType.VIDEO, value)
        }

    var audioData: AudioDataModel
        get() = getOrSetDataModel(MessageType.VOICEMESSAGE, AudioDataModel::create)
            ?: emptyAudioDataModel
        set(value) {
            setDataModel(MessageType.VOICEMESSAGE, value)
        }

    var voipStatusData: VoipStatusDataModel?
        get() = getOrSetDataModel(
            expectedType = MessageType.VOIP_STATUS,
            convert = {
                StatusDataModel.convert(this) as VoipStatusDataModel?
            },
        )
        set(value) {
            setDataModel(MessageType.VOIP_STATUS, value, StatusDataModel::convert)
        }

    var groupCallStatusData: GroupCallStatusDataModel?
        get() = getOrSetDataModel(
            expectedType = MessageType.GROUP_CALL_STATUS,
            convert = {
                StatusDataModel.convert(this) as GroupCallStatusDataModel?
            },
        )
        set(value) {
            setDataModel(MessageType.GROUP_CALL_STATUS, value, StatusDataModel::convert)
        }

    var forwardSecurityStatusData: ForwardSecurityStatusDataModel?
        get() = getOrSetDataModel(
            expectedType = MessageType.FORWARD_SECURITY_STATUS,
            convert = {
                StatusDataModel.convert(this) as ForwardSecurityStatusDataModel?
            },
        )
        set(value) {
            setDataModel(MessageType.FORWARD_SECURITY_STATUS, value, StatusDataModel::convert)
        }

    var groupStatusData: GroupStatusDataModel?
        get() = getOrSetDataModel(
            expectedType = MessageType.GROUP_STATUS,
            convert = {
                StatusDataModel.convert(this) as GroupStatusDataModel?
            },
        )
        set(value) {
            setDataModel(MessageType.GROUP_STATUS, value, StatusDataModel::convert)
        }

    var imageData: ImageDataModel
        get() = getOrSetDataModel(MessageType.IMAGE, ImageDataModel::create)
            ?: emptyImageDataModel
        set(value) {
            setDataModel(MessageType.IMAGE, value)
        }

    var ballotData: BallotDataModel
        get() = getOrSetDataModel(MessageType.BALLOT, BallotDataModel::create)
            ?: emptyBallotDataModel
        set(value) {
            setDataModel(MessageType.BALLOT, value)
        }

    var fileData: FileDataModel
        get() = getOrSetDataModel(MessageType.FILE, FileDataModel::create)
            ?: emptyFileDataModel
        set(value) {
            setDataModel(MessageType.FILE, value)
        }

    private inline fun <reified T : MessageDataInterface> getOrSetDataModel(
        expectedType: MessageType,
        convert: String.() -> T?,
    ): T? {
        if (dataObject == null || dataObject !is T) {
            if (checkBodyAndTypeForDataObjectAccess(expectedType)) {
                dataObject = body?.convert()
                if (dataObject !is T) {
                    logger.warn("Message data object is of wrong type for expected message type {}", expectedType)
                }
            }
        }
        return (dataObject as? T)
    }

    private fun checkBodyAndTypeForDataObjectAccess(expectedType: MessageType): Boolean {
        if (body == null) {
            logger.warn("Message body was null when trying to get data model from it")
            return false
        }
        if (type == null) {
            logger.warn("Message type was expected to be {} but was null when trying to get data model", expectedType)
            return true
        }
        if (type != expectedType) {
            logger.warn("Message type was expected to be {} but was {} when trying to get data model", expectedType, type)
            return false
        }
        return true
    }

    private fun <T : MessageDataInterface> setDataModel(
        type: MessageType,
        dataObject: T?,
        convertToString: T.() -> String = { this.toString() },
    ) {
        this.type = type
        this.body = dataObject?.convertToString()
        this.dataObject = dataObject
    }

    /**
     * Call this to update the body field with the data model stuff
     */
    fun writeDataModelToBody() {
        dataObject?.let { dataObject ->
            body = dataObject.toString()
        }
    }

    val isAvailable: Boolean
        get() = when (type) {
            MessageType.IMAGE -> isOutbox || imageData.isDownloaded
            MessageType.VIDEO -> isOutbox || videoData.isDownloaded
            MessageType.VOICEMESSAGE -> isOutbox || audioData.isDownloaded
            MessageType.FILE -> isOutbox || fileData.isDownloaded
            else -> true
        }

    val isStarred: Boolean
        get() = (displayTags and DisplayTag.DISPLAY_TAG_STARRED) == DisplayTag.DISPLAY_TAG_STARRED

    /**
     * This only makes sense (finds its use) when multi device is active.
     * The api call to download a blob without multi-device does not require a scope.
     *
     * @return The `BlobScope` to use when downloading the blob from the mirror server.
     * If the message if outgoing (`isOutbox()`) we use the local scope to download the
     * blob, as we know we received a reflected message. For every kind of incoming message we
     * need to use the public scope, as the blob might only be present on the usual blob
     * server (not mirror)
     */
    val blobScopeForDownload: BlobScope
        get() = if (isOutbox) BlobScope.Local else BlobScope.Public

    /**
     * This only makes sense (finds its use) when multi device is active.
     * The api call to mark a blob as done without multi-device does not require a scope.
     *
     * @return The `BlobScope` to use when marking the blob as "done" on the blob mirror server.
     * We only use the public scope here if the message is incoming and its not a group message. If
     * the message in outgoing, always use local scope (because we got the reflection only). If the
     * message is incoming but in a group, we use local scope (so that the blob is retained for other group members).
     */
    val blobScopeForMarkAsDone: BlobScope
        get() = if (!isOutbox && this !is GroupMessageModel) BlobScope.Public else BlobScope.Local

    companion object {
        /**
         * The message id, unique per type.
         */
        const val COLUMN_ID: String = "id"

        /**
         * The message uid, unique globally.
         */
        const val COLUMN_UID: String = "uid"

        /**
         * The chat protocol message id assigned by the sender.
         */
        const val COLUMN_API_MESSAGE_ID: String = "apiMessageId"

        /**
         * Identity of the conversation partner.
         */
        const val COLUMN_IDENTITY: String = "identity"

        /**
         * Message direction. true = outgoing, false = incoming.
         */
        const val COLUMN_OUTBOX: String = "outbox"

        /**
         * Message type.
         */
        const val COLUMN_TYPE: String = "type"

        /**
         * Correlation ID.
         */
        const val COLUMN_CORRELATION_ID: String = "correlationId"

        /**
         * Message body.
         */
        const val COLUMN_BODY: String = "body"

        /**
         * Message caption.
         */
        const val COLUMN_CAPTION: String = "caption"

        /**
         * Whether this message has been read by the receiver.
         */
        const val COLUMN_IS_READ: String = "isRead"

        /**
         * Whether this message has been saved to the internal database.
         */
        const val COLUMN_IS_SAVED: String = "isSaved"

        /**
         * The message state.
         */
        const val COLUMN_STATE: String = "state"

        /**
         * When the message was created.
         */
        const val COLUMN_CREATED_AT: String = "createdAtUtc"

        /**
         * When the message was accepted by the server.
         */
        const val COLUMN_POSTED_AT: String = "postedAtUtc"

        /**
         * When the message was last modified.
         */
        const val COLUMN_MODIFIED_AT: String = "modifiedAtUtc"

        /**
         * Whether this message is a status message.
         */
        const val COLUMN_IS_STATUS_MESSAGE: String = "isStatusMessage"

        /**
         * This was used to indicate whether the message was saved to the message queue. Note that this
         * column is not used anymore. We just keep it in the database to prevent performing a risky
         * database migration since the message tables potentially contain many rows.
         */
        const val COLUMN_IS_QUEUED: String = "isQueued"

        /**
         * API message id of quoted message, if any.
         */
        const val COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID: String = "quotedMessageId"

        /**
         * contents type of message - may be different from type
         */
        const val COLUMN_MESSAGE_CONTENTS_TYPE: String = "messageContentsType"

        /**
         * message flags that affect delivery receipt behavior etc. - carried over from AbstractMessage
         */
        const val COLUMN_MESSAGE_FLAGS: String = "messageFlags"

        /**
         * When the message was delivered.
         */
        const val COLUMN_DELIVERED_AT: String = "deliveredAtUtc"

        /**
         * When the message was read.
         */
        const val COLUMN_READ_AT: String = "readAtUtc"

        /**
         * The forward security mode in which the message was received/sent.
         */
        const val COLUMN_FORWARD_SECURITY_MODE: String = "forwardSecurityMode"

        /**
         * Display tags. Used e.g. for starred or pinned messages
         */
        const val COLUMN_DISPLAY_TAGS: String = "displayTags"

        /**
         * When the message was edited
         */
        const val COLUMN_EDITED_AT: String = "editedAtUtc"

        /**
         * When the message was deleted
         */
        const val COLUMN_DELETED_AT: String = "deletedAtUtc"

        // These models are used as workaround for the case where the data model is accessed
        // through a getter, but the stored body is missing or of the wrong type. We can't return null
        // so instead we return these dummy models that contain no data.
        private val emptyLocationDataModel by lazy(LazyThreadSafetyMode.NONE) {
            LocationDataModel.createEmpty()
        }
        private val emptyVideoDataModel by lazy(LazyThreadSafetyMode.NONE) {
            VideoDataModel.createEmpty()
        }
        private val emptyAudioDataModel by lazy(LazyThreadSafetyMode.NONE) {
            AudioDataModel.createEmpty()
        }
        private val emptyImageDataModel by lazy(LazyThreadSafetyMode.NONE) {
            ImageDataModel.createEmpty()
        }
        private val emptyBallotDataModel by lazy(LazyThreadSafetyMode.NONE) {
            BallotDataModel.createEmpty()
        }
        private val emptyFileDataModel by lazy(LazyThreadSafetyMode.NONE) {
            FileDataModel.createEmpty()
        }
    }
}
