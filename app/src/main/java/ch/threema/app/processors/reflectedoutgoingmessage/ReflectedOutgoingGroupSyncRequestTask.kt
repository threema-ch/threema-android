package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

private val logger = getThreemaLogger("ReflectedOutgoingGroupSyncRequestTask")

internal class ReflectedOutgoingGroupSyncRequestTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupSyncRequestMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupSyncRequestMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.GROUP_SYNC_REQUEST,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        logger.info("Discarding reflected outgoing group sync request message")
    }
}
