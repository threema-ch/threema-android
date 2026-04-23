package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyByteArray
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.protobuf.d2d.OutgoingMessage

private val logger = getThreemaLogger("ReflectedOutgoingPlaceholderTask")

/**
 * This task is used for messages that do not require any steps to be executed. We use this generic placeholder task to prevent us from having to
 * parse the unused messages. Therefore we use the [PlaceholderMessage].
 */
internal class ReflectedOutgoingPlaceholderTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
    private val logMessage: String? = null,
) : ReflectedOutgoingContactMessageTask<AbstractMessage>(
    outgoingMessage = outgoingMessage,
    message = PlaceholderMessage,
    type = outgoingMessage.type,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        logMessage?.let(logger::warn)
    }
}

/**
 * This placeholder message is used instead of parsing the reflected outgoing message that won't be used anyways.
 */
private object PlaceholderMessage : AbstractMessage() {
    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = false

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = false

    override fun reflectIncoming() = false

    override fun reflectOutgoing() = false

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate() = false

    override fun getType() = -1

    override fun getMinimumRequiredForwardSecurityVersion() = null

    override fun getBody() = emptyByteArray()
}
