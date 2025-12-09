/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.processors

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.getSubTaskFromMessage
import ch.threema.app.processors.reflectedd2dsync.ReflectedContactSyncTask
import ch.threema.app.processors.reflectedd2dsync.ReflectedGroupSyncTask
import ch.threema.app.processors.reflectedd2dsync.ReflectedSettingsSyncTask
import ch.threema.app.processors.reflectedd2dsync.ReflectedUserProfileSyncTask
import ch.threema.app.processors.reflectedmessageupdate.ReflectedIncomingMessageUpdateTask
import ch.threema.app.processors.reflectedmessageupdate.ReflectedOutgoingMessageUpdateTask
import ch.threema.app.processors.reflectedoutgoingmessage.getReflectedOutgoingMessageTask
import ch.threema.app.tasks.ActiveComposableTask
import ch.threema.app.utils.AppVersionProvider
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toHexString
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.AudioMessage
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.ImageMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.protocol.csp.messages.VideoMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.protobuf.Common.CspE2eMessageType
import ch.threema.protobuf.d2d.MdD2D.ContactSync
import ch.threema.protobuf.d2d.MdD2D.DistributionListSync
import ch.threema.protobuf.d2d.MdD2D.Envelope
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.CONTACT_SYNC
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.DISTRIBUTION_LIST_SYNC
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.GROUP_SYNC
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.INCOMING_MESSAGE
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.INCOMING_MESSAGE_UPDATE
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.MDM_PARAMETER_SYNC
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.OUTGOING_MESSAGE
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.OUTGOING_MESSAGE_UPDATE
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.SETTINGS_SYNC
import ch.threema.protobuf.d2d.MdD2D.Envelope.ContentCase.USER_PROFILE_SYNC
import ch.threema.protobuf.d2d.MdD2D.GroupSync
import ch.threema.protobuf.d2d.MdD2D.IncomingMessage
import ch.threema.protobuf.d2d.MdD2D.IncomingMessageUpdate
import ch.threema.protobuf.d2d.MdD2D.MdmParameterSync
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessageUpdate
import ch.threema.protobuf.d2d.MdD2D.SettingsSync
import ch.threema.protobuf.d2d.MdD2D.UserProfileSync

private val logger = getThreemaLogger("IncomingReflectedMessageTask")

class IncomingReflectedMessageTask(
    private val message: InboundD2mMessage.Reflected,
    private val serviceManager: ServiceManager,
) : ActiveComposableTask<Unit> {
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val myIdentity by lazy { serviceManager.userService.identity!! }

    override suspend fun run(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        val (nonce, envelope) = multiDeviceProperties.keys.decryptEnvelope(message.envelope)

        logger.info("Process reflected message with content case {} and timestamp {}", envelope.contentCase, message.timestamp)

        logger.debug("Process reflected message: {}", envelope)

        suspend {
            if (!nonceFactory.exists(NonceScope.D2D, nonce)) {
                processEnvelope(envelope, handle)
            } else {
                logger.warn(
                    "Skipped processing of reflected message {} as its nonce has already been used",
                    message.reflectedId,
                )
            }
        }.catchAllExceptNetworkException {
            logger.error("Could not process reflected envelope", it)
            // TODO(ANDR-2706): Handle these exceptions
        }

        // Finally send a reflected ack and store the nonce
        handle.write(OutboundD2mMessage.ReflectedAck(message.reflectedId))
        // Note that the existence of the nonce is checked again before storing it to avoid logging
        // an exception
        // TODO(ANDR-2983): Only store the nonce if it has to be stored according to protocol
        if (!nonceFactory.exists(NonceScope.D2D, nonce)) {
            nonceFactory.store(NonceScope.D2D, nonce)
        }
    }

    private suspend fun processEnvelope(envelope: Envelope, handle: ActiveTaskCodec) {
        when (envelope.contentCase) {
            OUTGOING_MESSAGE -> {
                processOutgoingMessage(envelope.outgoingMessage)
            }

            OUTGOING_MESSAGE_UPDATE -> {
                processOutgoingMessageUpdate(envelope.outgoingMessageUpdate)
            }

            INCOMING_MESSAGE -> {
                processIncomingMessage(envelope.incomingMessage, handle)
            }

            INCOMING_MESSAGE_UPDATE -> {
                processIncomingMessageUpdate(envelope.incomingMessageUpdate)
            }

            USER_PROFILE_SYNC -> {
                processUserProfileSync(envelope.userProfileSync)
            }

            CONTACT_SYNC -> {
                processContactSync(envelope.contactSync)
            }

            GROUP_SYNC -> {
                processGroupSync(envelope.groupSync)
            }

            DISTRIBUTION_LIST_SYNC -> {
                processDistributionListSync(envelope.distributionListSync)
            }

            SETTINGS_SYNC -> {
                processSettingsSync(envelope.settingsSync)
            }

            MDM_PARAMETER_SYNC -> {
                processMdmParameterSync(envelope.mdmParameterSync)
            }

            else -> logger.error(
                "Reflected message with unknown content type {} received",
                envelope.contentCase,
            )
        }
    }

    private fun processOutgoingMessage(outgoingMessage: OutgoingMessage) {
        outgoingMessage.getReflectedOutgoingMessageTask(serviceManager)
            .executeReflectedOutgoingMessageSteps()
    }

    private fun processOutgoingMessageUpdate(outgoingMessageUpdate: OutgoingMessageUpdate) {
        ReflectedOutgoingMessageUpdateTask(
            outgoingMessageUpdate,
            message.timestamp,
            serviceManager,
        ).run()
    }

    private suspend fun processIncomingMessage(
        incomingMessage: IncomingMessage,
        handle: ActiveTaskCodec,
    ) {
        when (incomingMessage.type) {
            CspE2eMessageType.TEXT -> TextMessage.fromReflected(incomingMessage)
            CspE2eMessageType.FILE -> FileMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_FILE -> GroupFileMessage.fromReflected(incomingMessage)
            CspE2eMessageType.DELIVERY_RECEIPT -> DeliveryReceiptMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.GROUP_DELIVERY_RECEIPT -> GroupDeliveryReceiptMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.GROUP_TEXT -> GroupTextMessage.fromReflected(incomingMessage)
            CspE2eMessageType.POLL_SETUP -> PollSetupMessage.fromReflected(
                incomingMessage,
                incomingMessage.senderIdentity,
            )

            CspE2eMessageType.POLL_VOTE -> PollVoteMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_POLL_SETUP -> GroupPollSetupMessage.fromReflected(
                incomingMessage,
                incomingMessage.senderIdentity,
            )

            CspE2eMessageType.GROUP_POLL_VOTE -> GroupPollVoteMessage.fromReflected(incomingMessage)
            CspE2eMessageType.CALL_OFFER -> VoipCallOfferMessage.fromReflected(incomingMessage)
            CspE2eMessageType.CALL_ICE_CANDIDATE -> VoipICECandidatesMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.CALL_RINGING -> VoipCallRingingMessage.fromReflected(incomingMessage)
            CspE2eMessageType.CALL_ANSWER -> VoipCallAnswerMessage.fromReflected(incomingMessage)
            CspE2eMessageType.CALL_HANGUP -> VoipCallHangupMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_CALL_START -> GroupCallStartMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.CONTACT_REQUEST_PROFILE_PICTURE -> ContactRequestProfilePictureMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.CONTACT_SET_PROFILE_PICTURE -> SetProfilePictureMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.CONTACT_DELETE_PROFILE_PICTURE -> DeleteProfilePictureMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.LOCATION -> LocationMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_LOCATION -> GroupLocationMessage.fromReflected(incomingMessage)
            CspE2eMessageType.DELETE_MESSAGE -> DeleteMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_DELETE_MESSAGE -> GroupDeleteMessage.fromReflected(
                incomingMessage,
            )

            CspE2eMessageType.EDIT_MESSAGE -> EditMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_EDIT_MESSAGE -> GroupEditMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_SYNC_REQUEST -> GroupSyncRequestMessage.fromReflected(
                incomingMessage,
                myIdentity,
            )

            CspE2eMessageType.DEPRECATED_IMAGE -> ImageMessage.fromReflected(incomingMessage)
            CspE2eMessageType.DEPRECATED_AUDIO -> AudioMessage.fromReflected(incomingMessage)
            CspE2eMessageType.DEPRECATED_VIDEO -> VideoMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_IMAGE -> throw IllegalStateException("Deprecated group image messages are unsupported")
            CspE2eMessageType.GROUP_AUDIO -> throw IllegalStateException("Deprecated group audio messages are unsupported")
            CspE2eMessageType.GROUP_VIDEO -> throw IllegalStateException("Deprecated group video messages are unsupported")
            CspE2eMessageType.GROUP_JOIN_REQUEST -> throw IllegalStateException("Group join requests are unsupported")
            CspE2eMessageType.GROUP_JOIN_RESPONSE -> throw IllegalStateException("Group join responses are unsupported")
            CspE2eMessageType.REACTION -> ReactionMessage.fromReflected(incomingMessage)
            CspE2eMessageType.GROUP_REACTION -> GroupReactionMessage.fromReflected(incomingMessage)

            CspE2eMessageType.GROUP_SETUP -> logIgnoredMessageAndReturnNull("GROUP_SETUP")
            CspE2eMessageType.GROUP_NAME -> logIgnoredMessageAndReturnNull("GROUP_NAME")
            CspE2eMessageType.GROUP_LEAVE -> logIgnoredMessageAndReturnNull("GROUP_LEAVE")
            CspE2eMessageType.GROUP_SET_PROFILE_PICTURE -> logIgnoredMessageAndReturnNull("GROUP_SET_PROFILE_PICTURE")
            CspE2eMessageType.GROUP_DELETE_PROFILE_PICTURE -> logIgnoredMessageAndReturnNull("GROUP_DELETE_PROFILE_PICTURE")

            CspE2eMessageType.WEB_SESSION_RESUME -> throw IllegalStateException(
                "A web session resume message should never be received as reflected incoming message",
            )

            CspE2eMessageType.TYPING_INDICATOR -> TypingIndicatorMessage.fromReflected(incomingMessage)

            CspE2eMessageType.FORWARD_SECURITY_ENVELOPE -> throw IllegalStateException(
                "A forward security envelope message should never be received as reflected incoming message",
            )

            CspE2eMessageType.EMPTY -> throw IllegalStateException("An empty message should never be received as reflected incoming message")

            CspE2eMessageType.UNRECOGNIZED -> throw IllegalStateException("The reflected incoming message type is unrecognized")
            CspE2eMessageType._INVALID_TYPE -> throw IllegalStateException("The reflected incoming message type is invalid")

            null -> {
                logger.error("The reflected incoming message type is null")
                null
            }
        }?.let { message: AbstractMessage ->
            if (message.protectAgainstReplay()) {
                val nonce = Nonce(incomingMessage.nonce.toByteArray())
                if (nonceFactory.exists(NonceScope.CSP, nonce)) {
                    logger.info("Skip adding preexisting CSP nonce {}", nonce.bytes.toHexString())
                } else if (!nonceFactory.store(NonceScope.CSP, nonce)) {
                    logger.warn(
                        "CSP nonce {} of outgoing message could not be stored",
                        nonce.bytes.toHexString(),
                    )
                }
            } else {
                logger.debug("Do not store nonces for message of type {}", incomingMessage.type)
            }
            getSubTaskFromMessage(message, TriggerSource.SYNC, serviceManager).run(handle)
        }
    }

    private fun logIgnoredMessageAndReturnNull(messageTypeName: String): AbstractMessage? {
        logger.info("Ignoring incoming reflected message of type {}", messageTypeName)
        return null
    }

    private fun processIncomingMessageUpdate(incomingMessageUpdate: IncomingMessageUpdate) {
        ReflectedIncomingMessageUpdateTask(incomingMessageUpdate, serviceManager).run()
    }

    private fun processUserProfileSync(userProfileSync: UserProfileSync) {
        ReflectedUserProfileSyncTask(
            userProfileSync = userProfileSync,
            userService = serviceManager.userService,
            okHttpClient = serviceManager.okHttpClient,
            serverAddressProvider = serviceManager.serverAddressProviderService.serverAddressProvider,
            multiDevicePropertyProvider = multiDeviceManager.propertiesProvider,
            symmetricEncryptionService = serviceManager.symmetricEncryptionService,
            appVersion = AppVersionProvider.appVersion,
            preferenceService = serviceManager.preferenceService,
            profilePictureRecipientsService = serviceManager.profilePicRecipientsService,
        ).run()
    }

    private fun processContactSync(contactSync: ContactSync) {
        ReflectedContactSyncTask(
            contactSync,
            serviceManager.modelRepositories.contacts,
            serviceManager,
        ).run()
    }

    private fun processGroupSync(groupSync: GroupSync) {
        ReflectedGroupSyncTask(
            groupSync = groupSync,
            groupModelRepository = serviceManager.modelRepositories.groups,
            groupService = serviceManager.groupService,
            fileService = serviceManager.fileService,
            okHttpClient = serviceManager.okHttpClient,
            serverAddressProvider = serviceManager.serverAddressProviderService.serverAddressProvider,
            symmetricEncryptionService = serviceManager.symmetricEncryptionService,
            multiDeviceManager = multiDeviceManager,
            conversationService = serviceManager.conversationService,
            conversationTagService = serviceManager.conversationTagService,
            conversationCategoryService = serviceManager.conversationCategoryService,
            userService = serviceManager.userService,
        ).run()
    }

    private fun processDistributionListSync(distributionListSync: DistributionListSync) {
        // TODO(ANDR-2718)
        logger.warn("Distribution sync is not yet supported")
    }

    private fun processSettingsSync(settingsSync: SettingsSync) {
        ReflectedSettingsSyncTask(
            settingsSync = settingsSync,
            preferenceService = serviceManager.preferenceService,
            blockedIdentitiesService = serviceManager.blockedIdentitiesService,
            excludedSyncIdentitiesService = serviceManager.excludedSyncIdentitiesService,
        ).run()
    }

    private fun processMdmParameterSync(mdmParameterSync: MdmParameterSync) {
        // TODO(ANDR-2670)
        logger.warn("Mdm parameter sync is not yet supported")
    }
}
