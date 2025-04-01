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

package ch.threema.domain.helpers

import ch.threema.base.utils.Utils
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import java.util.LinkedList
import java.util.Queue

/**
 * This task codec is used only for tests. It creates server acknowledgements and automatically
 * decrypts outgoing fs messages. Note that this therefore also changes the state of the recipient's
 * forward security message processor.
 */
open class DecryptTaskCodec(
    private val contactStore: ContactStore,
    private val identityStores: Map<String, IdentityStoreInterface>,
    private val forwardSecurityMessageProcessors: Map<String, ForwardSecurityMessageProcessor>,
) : ServerAckTaskCodec() {
    val outboundAbstractMessages: Queue<AbstractMessage> = LinkedList()

    override suspend fun handleOutgoingMessageBox(messageBox: MessageBox) {
        // This creates the server ack
        super.handleOutgoingMessageBox(messageBox)

        // Decode and decapsulate message to add it to the outgoing abstract message list
        val abstractMessage = decodeMessage(messageBox)
        val decapsulatedMessage = decapsulateMessage(abstractMessage)

        if (decapsulatedMessage != null) {
            // Note that in case of a forward security message, the decapsulated message is
            // null and therefore not added here. However, the message is still processed by
            // the forward security message processor.
            outboundAbstractMessages.add(decapsulatedMessage)
        }
    }

    private fun decodeMessage(messageBox: MessageBox): AbstractMessage {
        val identityStore = identityStores[messageBox.toIdentity]
            ?: throw AssertionError("No identity store provided for identity ${messageBox.toIdentity}")
        return MessageCoder(contactStore, identityStore).decode(messageBox)
    }

    private suspend fun decapsulateMessage(message: AbstractMessage): AbstractMessage? {
        val sender = contactStore.getContactForIdentityIncludingCache(message.fromIdentity)!!

        val forwardSecurityMessageProcessor = forwardSecurityMessageProcessors[message.toIdentity]
            ?: throw AssertionError("No forward security message processor provided for identity ${message.toIdentity}")
        if (message is ForwardSecurityEnvelopeMessage) {
            when (val data = message.data) {
                is ForwardSecurityDataInit -> forwardSecurityMessageProcessor.processInit(
                    sender,
                    data,
                    this
                )

                is ForwardSecurityDataAccept -> forwardSecurityMessageProcessor.processAccept(
                    sender,
                    data,
                    this
                )

                is ForwardSecurityDataReject -> forwardSecurityMessageProcessor.processReject(
                    sender,
                    data,
                    this
                )

                is ForwardSecurityDataTerminate -> forwardSecurityMessageProcessor.processTerminate(
                    sender,
                    data
                )

                is ForwardSecurityDataMessage -> return forwardSecurityMessageProcessor.processMessage(
                    sender,
                    message,
                    this
                ).message
            }
            return null
        } else {
            throw AssertionError(
                "Expected forward secure message but got a message of type ${
                    Utils.byteToHex(
                        message.type.toByte(),
                        true,
                        true
                    )
                }"
            )
        }
    }
}
