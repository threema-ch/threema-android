package ch.threema.domain.protocol.csp.messages.voip

import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.protobuf.csp.e2e.fs.Version

abstract class VoipMessage : AbstractMessage() {
    override fun flagSendPush(): Boolean = true

    // Should be set for all VoIP messages except for the hangup message
    override fun flagShortLivedServerQueuing(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version? = Version.V1_1
}
