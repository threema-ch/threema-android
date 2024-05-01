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

package ch.threema.domain.taskmanager

import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.MessageTooLongException
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface

fun AbstractMessage.toCspMessage(
    identityStore: IdentityStoreInterface,
    contactStore: ContactStore,
    nonceFactory: NonceFactory,
    nonce: ByteArray,
): CspMessage {
    // Add missing attributes, if necessary
    if (fromIdentity == null) {
        fromIdentity = identityStore.identity
    }

    // Make box
    val messageCoder = MessageCoder(contactStore, identityStore)
    val messageBox = messageCoder.encode(this, nonce, nonceFactory)

    // For the sake of efficiency: simply deduct overhead size
    val overhead = (ProtocolDefines.OVERHEAD_MSG_HDR
            + ProtocolDefines.OVERHEAD_NACL_BOX
            + ProtocolDefines.OVERHEAD_PKT_HDR)
    if (messageBox.box != null && messageBox.box.size > ProtocolDefines.MAX_PKT_LEN - overhead) {
        throw MessageTooLongException()
    }

    return messageBox.creatCspMessage()
}
