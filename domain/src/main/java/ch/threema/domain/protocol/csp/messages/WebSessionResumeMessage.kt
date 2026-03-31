package ch.threema.domain.protocol.csp.messages

import ch.threema.common.emptyByteArray
import ch.threema.domain.protocol.csp.ProtocolDefines

class WebSessionResumeMessage(private val data: Map<String, String>) : AbstractMessage() {
    override fun getType() = ProtocolDefines.MSGTYPE_WEB_SESSION_RESUME

    override fun getMinimumRequiredForwardSecurityVersion() = null

    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = true

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = true

    override fun reflectIncoming() = false

    override fun reflectOutgoing() = false

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate(): Boolean = false

    override fun getBody() = emptyByteArray()

    fun getData(): Map<String, String> = data
}
