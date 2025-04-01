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

package ch.threema.app.processors.incomingcspmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactReactionMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupReactionMessageTask
import ch.threema.app.processors.incomingcspmessage.calls.IncomingCallAnswerTask
import ch.threema.app.processors.incomingcspmessage.calls.IncomingCallHangupTask
import ch.threema.app.processors.incomingcspmessage.calls.IncomingCallIceCandidateTask
import ch.threema.app.processors.incomingcspmessage.calls.IncomingCallOfferTask
import ch.threema.app.processors.incomingcspmessage.calls.IncomingCallRingingTask
import ch.threema.app.processors.incomingcspmessage.contactcontrol.IncomingContactRequestProfilePictureTask
import ch.threema.app.processors.incomingcspmessage.contactcontrol.IncomingDeleteProfilePictureTask
import ch.threema.app.processors.incomingcspmessage.contactcontrol.IncomingSetProfilePictureTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactConversationMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactDeleteMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactEditMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactFileMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactLocationMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactPollSetupTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingContactPollVoteTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupConversationMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupDeleteMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupEditMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupFileMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupLocationMessageTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupPollSetupTask
import ch.threema.app.processors.incomingcspmessage.conversation.IncomingGroupPollVoteTask
import ch.threema.app.processors.incomingcspmessage.fs.IncomingEmptyTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupCallControlTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupDeleteProfilePictureTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupJoinRequestTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupJoinResponseMessage
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupLeaveTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupNameTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupSetProfilePictureTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupSetupTask
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupSyncRequestTask
import ch.threema.app.processors.incomingcspmessage.statusupdates.IncomingDeliveryReceiptTask
import ch.threema.app.processors.incomingcspmessage.statusupdates.IncomingGroupDeliveryReceiptTask
import ch.threema.app.processors.incomingcspmessage.statusupdates.IncomingTypingIndicatorTask
import ch.threema.app.tasks.ActiveComposableTask
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EmptyMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import okhttp3.internal.toHexString

abstract class IncomingCspMessageSubTask<T : AbstractMessage?>(
    protected val message: T,
    protected val triggerSource: TriggerSource,
    protected val serviceManager: ServiceManager,
) : ActiveComposableTask<ReceiveStepsResult> {
    init {
        // Check that the trigger source is not local as an incoming message can only be triggered
        // by remote or by sync.
        if (triggerSource == TriggerSource.LOCAL) {
            throw IllegalStateException("An incoming csp message can never be locally created")
        }
    }

    final override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        // Check that the message and the trigger source is a valid combination
        if (message != null && !message.reflectIncoming() && triggerSource == TriggerSource.SYNC) {
            throw IllegalStateException("An incoming message of type ${message.type.toHexString()} has been received as reflected that should not have been reflected")
        }

        // Choose the right message steps depending on the trigger source
        return when (triggerSource) {
            TriggerSource.REMOTE -> executeMessageStepsFromRemote(handle)
            TriggerSource.SYNC -> executeMessageStepsFromSync()
            TriggerSource.LOCAL -> throw IllegalStateException("Cannot process an incoming message from local")
        }
    }

    /**
     * Execute the message receive steps for the given message type. Note that this must only be
     * called when the message is coming from the chat server (from remote) and not when it has been
     * reflected (from sync).
     */
    protected abstract suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult

    /**
     * Execute the message receive steps for the given message type. Note that this must be called
     * when the message has been received from sync (reflected).
     */
    protected abstract suspend fun executeMessageStepsFromSync(): ReceiveStepsResult
}

enum class ReceiveStepsResult {
    SUCCESS,
    DISCARD,
}

fun getSubTaskFromMessage(
    message: AbstractMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
): IncomingCspMessageSubTask<*> = when (message) {
    // Determine the message type and get its corresponding receive steps. Note that the order
    // of checking the types is important. For instance, an abstract group message must first be
    // checked for a group control message to prevent processing it as a group conversation
    // message.

    // Check if message is a status update
    is TypingIndicatorMessage -> IncomingTypingIndicatorTask(message, triggerSource, serviceManager)
    is DeliveryReceiptMessage -> IncomingDeliveryReceiptTask(message, triggerSource, serviceManager)
    is GroupDeliveryReceiptMessage -> IncomingGroupDeliveryReceiptTask(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a location message
    is LocationMessage -> IncomingContactLocationMessageTask(message, triggerSource, serviceManager)
    is GroupLocationMessage -> IncomingGroupLocationMessageTask(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a group control message
    is GroupSetupMessage -> IncomingGroupSetupTask(message, triggerSource, serviceManager)
    is GroupNameMessage -> IncomingGroupNameTask(message, triggerSource, serviceManager)
    is GroupSetProfilePictureMessage -> IncomingGroupSetProfilePictureTask(
        message,
        triggerSource,
        serviceManager
    )

    is GroupDeleteProfilePictureMessage -> IncomingGroupDeleteProfilePictureTask(
        message,
        triggerSource,
        serviceManager
    )

    is GroupLeaveMessage -> IncomingGroupLeaveTask(message, triggerSource, serviceManager)
    is GroupSyncRequestMessage -> IncomingGroupSyncRequestTask(
        message,
        triggerSource,
        serviceManager
    )

    is GroupCallControlMessage -> IncomingGroupCallControlTask(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a contact control message
    is SetProfilePictureMessage -> IncomingSetProfilePictureTask(
        message,
        triggerSource,
        serviceManager
    )

    is DeleteProfilePictureMessage -> IncomingDeleteProfilePictureTask(
        message,
        triggerSource,
        serviceManager
    )

    is ContactRequestProfilePictureMessage -> IncomingContactRequestProfilePictureTask(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a ballot message
    is PollSetupMessage -> IncomingContactPollSetupTask(message, triggerSource, serviceManager)
    is PollVoteMessage -> IncomingContactPollVoteTask(message, triggerSource, serviceManager)
    is GroupPollSetupMessage -> IncomingGroupPollSetupTask(message, triggerSource, serviceManager)
    is GroupPollVoteMessage -> IncomingGroupPollVoteTask(message, triggerSource, serviceManager)

    // Check if message is a group join message
    is GroupJoinRequestMessage -> IncomingGroupJoinRequestTask(
        message,
        triggerSource,
        serviceManager
    )

    is GroupJoinResponseMessage -> IncomingGroupJoinResponseMessage(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a call message
    is VoipCallOfferMessage -> IncomingCallOfferTask(message, triggerSource, serviceManager)
    is VoipCallAnswerMessage -> IncomingCallAnswerTask(message, triggerSource, serviceManager)
    is VoipICECandidatesMessage -> IncomingCallIceCandidateTask(
        message,
        triggerSource,
        serviceManager
    )

    is VoipCallRingingMessage -> IncomingCallRingingTask(message, triggerSource, serviceManager)
    is VoipCallHangupMessage -> IncomingCallHangupTask(message, triggerSource, serviceManager)

    // Check if message is an edit message
    is EditMessage -> IncomingContactEditMessageTask(message, triggerSource, serviceManager)
    is GroupEditMessage -> IncomingGroupEditMessageTask(message, triggerSource, serviceManager)

    // Check if message is a delete message
    is DeleteMessage -> IncomingContactDeleteMessageTask(message, triggerSource, serviceManager)
    is GroupDeleteMessage -> IncomingGroupDeleteMessageTask(message, triggerSource, serviceManager)

    // Check if message is a group reaction message
    is ReactionMessage -> IncomingContactReactionMessageTask(message, triggerSource, serviceManager)
    is GroupReactionMessage -> IncomingGroupReactionMessageTask(
        message,
        triggerSource,
        serviceManager
    )

    // Check if message is a file message
    is FileMessage -> IncomingContactFileMessageTask(message, triggerSource, serviceManager)
    is GroupFileMessage -> IncomingGroupFileMessageTask(message, triggerSource, serviceManager)

    // If it is a group message, process it as a group conversation message
    is AbstractGroupMessage -> IncomingGroupConversationMessageTask(
        message,
        triggerSource,
        serviceManager
    )

    // Process the empty message in its corresponding task
    is EmptyMessage -> IncomingEmptyTask(message, triggerSource, serviceManager)

    // Otherwise it must be a contact conversation message
    else -> IncomingContactConversationMessageTask(message, triggerSource, serviceManager)
}
