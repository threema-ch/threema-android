package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

private val logger = getThreemaLogger("ReflectedOutgoingGroupSyncRequestTask")

internal class ReflectedOutgoingGroupSyncRequestTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupSyncRequestMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupSyncRequestMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.GROUP_SYNC_REQUEST,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        logger.info("Discarding reflected outgoing group sync request message")
    }
}
