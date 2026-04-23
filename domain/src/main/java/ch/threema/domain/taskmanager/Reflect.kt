package ch.threema.domain.taskmanager

import ch.threema.base.crypto.Nonce
import ch.threema.base.utils.generateRandomProtobufPadding
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.GroupIdentity
import ch.threema.protobuf.common.groupIdentity
import ch.threema.protobuf.d2d.ContactSyncKt
import ch.threema.protobuf.d2d.ConversationId
import ch.threema.protobuf.d2d.Envelope
import ch.threema.protobuf.d2d.GroupSync
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.IncomingMessageUpdate
import ch.threema.protobuf.d2d.OutgoingMessage
import ch.threema.protobuf.d2d.OutgoingMessageUpdate
import ch.threema.protobuf.d2d.ProtocolVersion
import ch.threema.protobuf.d2d.SettingsSyncKt
import ch.threema.protobuf.d2d.UserProfileSyncKt
import ch.threema.protobuf.d2d.contactSync
import ch.threema.protobuf.d2d.conversationId
import ch.threema.protobuf.d2d.groupSync
import ch.threema.protobuf.d2d.settingsSync
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.d2d.sync.Settings
import ch.threema.protobuf.d2d.sync.UserProfile
import ch.threema.protobuf.d2d.userProfileSync
import com.google.protobuf.kotlin.toByteString

class ReflectIdManager {
    private var counter: UInt = 0u

    fun next() = counter++
}

fun getEncryptedIncomingMessageEnvelope(
    message: AbstractMessage,
    nonce: ByteArray,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult? {
    val messageBody = message.body ?: return null
    val envelope = buildEnvelopeFor(mediatorDeviceId) {
        setIncomingMessage(
            IncomingMessage.newBuilder()
                .setSenderIdentity(message.fromIdentity)
                .setMessageId(message.messageId.messageIdLong)
                .setCreatedAt(message.date.time)
                .setTypeValue(message.type)
                .setBody(messageBody.toByteString())
                .setNonce(nonce.toByteString()),
        )
    }
    return multiDeviceKeys.encryptEnvelope(envelope)
}

fun getEncryptedOutgoingMessageEnvelope(
    message: AbstractMessage,
    nonces: Collection<Nonce>,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult? {
    val messageBody = message.body ?: return null
    val envelope = buildEnvelopeFor(mediatorDeviceId) {
        setOutgoingMessage(
            OutgoingMessage.newBuilder()
                .setConversation(getConversation(message))
                .setMessageId(message.messageId.messageIdLong)
                .setCreatedAt(message.date.time)
                .setTypeValue(message.type)
                .setBody(messageBody.toByteString())
                .addAllNonces(nonces.map { it.bytes.toByteString() }),
        )
    }
    return multiDeviceKeys.encryptEnvelope(envelope)
}

fun getEncryptedOutgoingMessageUpdateSentEnvelope(
    message: AbstractMessage,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(mediatorDeviceId) {
        setOutgoingMessageUpdate(
            OutgoingMessageUpdate.newBuilder()
                .addUpdates(
                    OutgoingMessageUpdate.Update.newBuilder()
                        .setConversation(getConversation(message))
                        .setMessageId(message.messageId.messageIdLong)
                        .setSent(OutgoingMessageUpdate.Sent.newBuilder()),
                ),
        )
    }
    return multiDeviceKeys.encryptEnvelope(envelope)
}

fun getEncryptedIncomingContactMessageUpdateReadEnvelope(
    messageIds: Set<MessageId>,
    timestamp: Long,
    senderIdentity: IdentityString,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    return getEncryptedIncomingMessageUpdateReadEnvelope(
        messageIds,
        timestamp,
        conversationId { contact = senderIdentity },
        mediatorDeviceId,
        multiDeviceKeys,
    )
}

fun getEncryptedIncomingGroupMessageUpdateReadEnvelope(
    messageIds: Set<MessageId>,
    timestamp: Long,
    creatorIdentity: IdentityString,
    groupId: GroupId,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    return getEncryptedIncomingMessageUpdateReadEnvelope(
        messageIds,
        timestamp,
        conversationId {
            group = groupIdentity {
                this.creatorIdentity = creatorIdentity
                this.groupId = groupId.toLong()
            }
        },
        mediatorDeviceId,
        multiDeviceKeys,
    )
}

private fun getEncryptedIncomingMessageUpdateReadEnvelope(
    messageIds: Set<MessageId>,
    timestamp: Long,
    conversation: ConversationId,
    mediatorDeviceId: DeviceId,
    multiDeviceKeys: MultiDeviceKeys,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(mediatorDeviceId) {
        setIncomingMessageUpdate(
            IncomingMessageUpdate.newBuilder()
                .addAllUpdates(
                    messageIds.map {
                        IncomingMessageUpdate.Update.newBuilder()
                            .setMessageId(it.messageIdLong)
                            .setConversation(conversation)
                            .setRead(
                                IncomingMessageUpdate.Read.newBuilder().setAt(timestamp).build(),
                            )
                            .build()
                    },
                ),
        )
    }
    return multiDeviceKeys.encryptEnvelope(envelope)
}

fun getEncryptedContactSyncCreate(
    contact: Contact,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setContactSync(
            contactSync {
                create = ContactSyncKt.create {
                    this.contact = contact
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedContactSyncUpdate(
    contact: Contact,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setContactSync(
            contactSync {
                update = ContactSyncKt.update {
                    this.contact = contact
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedUserProfileSyncUpdate(
    userProfile: UserProfile,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope: Envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setUserProfileSync(
            userProfileSync {
                update = UserProfileSyncKt.update {
                    this.userProfile = userProfile
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedSettingsSyncUpdate(
    settings: Settings,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        settingsSync = settingsSync {
            this.update = SettingsSyncKt.update {
                this.settings = settings
            }
        }
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedGroupSyncCreate(
    group: Group,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setGroupSync(
            groupSync {
                create = ch.threema.protobuf.d2d.GroupSyncKt.create {
                    this.group = group
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedGroupSyncUpdate(
    group: Group,
    memberStateChanges: Map<String, GroupSync.Update.MemberStateChange>,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setGroupSync(
            groupSync {
                update = ch.threema.protobuf.d2d.GroupSyncKt.update {
                    this.group = group
                    this.memberStateChanges.putAll(memberStateChanges)
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

fun getEncryptedGroupSyncDelete(
    groupIdentity: GroupIdentity,
    multiDeviceProperties: MultiDeviceProperties,
): MultiDeviceKeys.EncryptedEnvelopeResult {
    val envelope = buildEnvelopeFor(multiDeviceProperties.mediatorDeviceId) {
        setGroupSync(
            groupSync {
                delete = ch.threema.protobuf.d2d.GroupSyncKt.delete {
                    this.groupIdentity = groupIdentity
                }
            },
        )
    }
    return multiDeviceProperties.keys.encryptEnvelope(envelope)
}

private fun buildEnvelopeFor(
    mediatorDeviceId: DeviceId,
    buildBody: Envelope.Builder.() -> Unit,
): Envelope =
    Envelope.newBuilder()
        .setPadding(generateRandomProtobufPadding())
        .setDeviceId(mediatorDeviceId.id.toLong())
        .setProtocolVersion(ProtocolVersion.V0_3_VALUE)
        .apply { buildBody() }
        .build()

private fun getConversation(message: AbstractMessage): ConversationId {
    val conversationId = ConversationId.newBuilder()
    when (message) {
        is AbstractGroupMessage ->
            conversationId.setGroup(
                GroupIdentity.newBuilder()
                    .setCreatorIdentity(message.groupCreator)
                    .setGroupId(message.apiGroupId.toLong()),
            )

        else -> conversationId.contact = message.toIdentity
    }
    return conversationId.build()
}
