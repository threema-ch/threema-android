package ch.threema.app.processors.reflectedoutgoingmessage.groupcall

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.reflectedoutgoingmessage.ReflectedOutgoingGroupMessageTask
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

internal class ReflectedOutgoingGroupCallStartTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupCallStartMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupCallStartMessage.fromReflected(
        message = outgoingMessage,
        ownIdentity = serviceManager.identityStore.getIdentityString()!!,
    ),
    type = CspE2eMessageType.GROUP_CALL_START,
    serviceManager = serviceManager,
) {
    private val groupCallManager = serviceManager.groupCallManager

    override fun processOutgoingMessage() {
        groupCallManager.handleControlMessage(message)
    }
}
