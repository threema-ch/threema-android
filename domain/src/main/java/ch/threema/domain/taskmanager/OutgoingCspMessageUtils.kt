package ch.threema.domain.taskmanager

import ch.threema.base.crypto.Nonce
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.MessageTooLongException
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStore

@JvmName("toCspMessageJava")
fun AbstractMessage.toCspMessage(
    identityStore: IdentityStore,
    contactStore: ContactStore,
    nonce: Nonce,
): CspMessage {
    // Add missing attributes, if necessary
    if (fromIdentity == null) {
        fromIdentity = identityStore.getIdentity()
    }

    // Make box
    val messageCoder = MessageCoder(contactStore, identityStore)
    val messageBox = messageCoder.encode(this, nonce.bytes)

    // For the sake of efficiency: simply deduct overhead size
    val overhead = (
        ProtocolDefines.OVERHEAD_MSG_HDR +
            ProtocolDefines.OVERHEAD_NACL_BOX +
            ProtocolDefines.OVERHEAD_PKT_HDR
        )
    if (messageBox.box != null && messageBox.box.size > ProtocolDefines.MAX_PKT_LEN - overhead) {
        throw MessageTooLongException()
    }

    return messageBox.creatCspMessage()
}
