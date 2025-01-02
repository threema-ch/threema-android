/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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
import ch.threema.app.processors.reflectedmessageupdate.ReflectedIncomingMessageUpdateTask
import ch.threema.app.processors.reflectedmessageupdate.ReflectedOutgoingMessageUpdateTask
import ch.threema.app.processors.reflectedoutgoingmessage.getReflectedOutgoingMessageTask
import ch.threema.app.tasks.ActiveComposableTask
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.toHexString
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.protobuf.Common
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

private val logger = LoggingUtil.getThreemaLogger("IncomingReflectedMessageTask")

class IncomingReflectedMessageTask(
    private val message: InboundD2mMessage.Reflected,
    private val serviceManager: ServiceManager,
) : ActiveComposableTask<Unit> {
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    override suspend fun run(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        val (nonce, envelope) = multiDeviceProperties.keys.decryptEnvelope(message.envelope)

        logger.debug("Process reflected message: {} at {}", envelope, message.timestamp)

        suspend {
            if (!nonceFactory.exists(NonceScope.D2D, nonce)) {
                processEnvelope(envelope, handle)
            } else {
                logger.warn("Skipped processing of reflected message {} as its nonce has already been used", message.reflectedId)
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

            else -> logger.error("Reflected message with unknown content type {} received", envelope.contentCase)
        }
    }

    private fun processOutgoingMessage(outgoingMessage: OutgoingMessage) {
        outgoingMessage.getReflectedOutgoingMessageTask(serviceManager)
            .executeReflectedOutgoingMessageSteps()
    }

    private fun processOutgoingMessageUpdate(outgoingMessageUpdate: OutgoingMessageUpdate) {
        ReflectedOutgoingMessageUpdateTask(outgoingMessageUpdate, message.timestamp, serviceManager).run()
    }

    private suspend fun processIncomingMessage(
        incomingMessage: IncomingMessage,
        handle: ActiveTaskCodec,
    ) {
        when (incomingMessage.type) {
            Common.CspE2eMessageType.TEXT -> TextMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.FILE -> FileMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.GROUP_FILE -> GroupFileMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.DELIVERY_RECEIPT -> DeliveryReceiptMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.GROUP_DELIVERY_RECEIPT -> GroupDeliveryReceiptMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.GROUP_TEXT -> GroupTextMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.POLL_SETUP -> PollSetupMessage.fromReflected(incomingMessage, incomingMessage.senderIdentity)
            Common.CspE2eMessageType.POLL_VOTE -> PollVoteMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.GROUP_POLL_SETUP -> GroupPollSetupMessage.fromReflected(incomingMessage, incomingMessage.senderIdentity)
            Common.CspE2eMessageType.GROUP_POLL_VOTE -> GroupPollVoteMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CALL_OFFER -> VoipCallOfferMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CALL_ICE_CANDIDATE -> VoipICECandidatesMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CALL_RINGING -> VoipCallRingingMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CALL_ANSWER -> VoipCallAnswerMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CALL_HANGUP -> VoipCallHangupMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.GROUP_CALL_START -> GroupCallStartMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CONTACT_REQUEST_PROFILE_PICTURE -> ContactRequestProfilePictureMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CONTACT_SET_PROFILE_PICTURE -> SetProfilePictureMessage.fromReflected(incomingMessage)
            Common.CspE2eMessageType.CONTACT_DELETE_PROFILE_PICTURE -> DeleteProfilePictureMessage.fromReflected(incomingMessage)

            else -> {
                logger.warn("Reflected incoming message of type {} is not yet supported", incomingMessage.type)
                null
            }
        }?.let { message: AbstractMessage ->
            if (message.protectAgainstReplay()) {
                val nonce = Nonce(incomingMessage.nonce.toByteArray())
                if (nonceFactory.exists(NonceScope.CSP, nonce)) {
                    logger.info("Skip adding preexisting CSP nonce {}", nonce.bytes.toHexString())
                } else if (!nonceFactory.store(NonceScope.CSP, nonce)) {
                    logger.warn("CSP nonce {} of outgoing message could not be stored", nonce.bytes.toHexString())
                }
            } else {
                logger.debug("Do not store nonces for message of type {}", incomingMessage.type)
            }
            getSubTaskFromMessage(message, TriggerSource.SYNC, serviceManager).run(handle)
        }
    }

    private fun processIncomingMessageUpdate(incomingMessageUpdate: IncomingMessageUpdate) {
        ReflectedIncomingMessageUpdateTask(incomingMessageUpdate, serviceManager).run()
    }

    private fun processUserProfileSync(userProfileSync: UserProfileSync) {
        // TODO(ANDR-2840)
        logger.warn("User profile sync is not yet supported")
    }

    private fun processContactSync(contactSync: ContactSync) {
        ReflectedContactSyncTask(
            contactSync,
            serviceManager.modelRepositories.contacts,
            serviceManager,
        ).run()
    }

    private fun processGroupSync(groupSync: GroupSync) {
        // TODO(ANDR-2741)
        logger.warn("Group sync is not yet supported")
    }

    private fun processDistributionListSync(distributionListSync: DistributionListSync) {
        // TODO(ANDR-2718)
        logger.warn("Distribution sync is not yet supported")
    }

    private fun processSettingsSync(settingsSync: SettingsSync) {
        // TODO(ANDR-2839)
        logger.warn("Settings sync is not yet supported")
    }

    private fun processMdmParameterSync(mdmParameterSync: MdmParameterSync) {
        // TODO(ANDR-2670)
        logger.warn("Mdm parameter sync is not yet supported")
    }

}
