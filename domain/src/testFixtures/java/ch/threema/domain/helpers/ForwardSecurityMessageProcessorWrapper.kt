/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityEncryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.fs.PeerRatchetIdentifier
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityData
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.stores.DHSessionStoreException
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import kotlinx.coroutines.runBlocking

/**
 * This class can be used for testing the forward security message processor. It can be used to call
 * the suspend functions blocking and is therefore compatible with java code. Note that this must be
 * only used for testing as this does not make use of suspendable functions.
 */
class ForwardSecurityMessageProcessorWrapper(
    private val fsmp: ForwardSecurityMessageProcessor,
) {
    @Throws(ThreemaException::class)
    fun runFsEncapsulationSteps(
        contact: BasicContact,
        innerMessage: AbstractMessage,
        nonce: ByteArray,
        nonceFactory: NonceFactory,
        handle: ActiveTaskCodec,
    ): ForwardSecurityEncryptionResult {
        return fsmp.runFsEncapsulationSteps(contact, innerMessage, Nonce(nonce), nonceFactory, handle)
    }

    @Throws(
        DHSessionStoreException::class,
        ForwardSecurityData.InvalidEphemeralPublicKeyException::class
    )
    fun runFsRefreshSteps(contact: Contact, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.runFsRefreshSteps(contact, handle)
        }
    }

    fun commitSessionState(result: ForwardSecurityEncryptionResult) {
        fsmp.commitSessionState(result)
    }

    @Throws(DHSessionStoreException::class)
    fun commitPeerRatchet(peerRatchetIdentifier: PeerRatchetIdentifier, handle: ActiveTaskCodec) {
        fsmp.commitPeerRatchet(peerRatchetIdentifier, handle)
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    fun processInit(contact: Contact, init: ForwardSecurityDataInit, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.processInit(contact, init, handle)
        }
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    fun processAccept(contact: Contact, accept: ForwardSecurityDataAccept, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.processAccept(contact, accept, handle)
        }
    }

    @Throws(DHSessionStoreException::class)
    fun processReject(contact: Contact, reject: ForwardSecurityDataReject, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.processReject(contact, reject, handle)
        }
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    fun processMessage(contact: Contact, envelopeMessage: ForwardSecurityEnvelopeMessage, handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        return runBlocking {
            fsmp.processMessage(contact, envelopeMessage, handle)
        }
    }

    @Throws(DHSessionStoreException::class)
    fun processTerminate(contact: Contact, message: ForwardSecurityDataTerminate) {
        runBlocking {
            fsmp.processTerminate(contact, message)
        }
    }

    fun clearAndTerminateAllSessions(contact: Contact, cause: Cause, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.clearAndTerminateAllSessions(contact, cause, handle)
        }
    }

    @Throws(DHSessionStoreException::class)
    fun terminateAllInvalidSessions(contact: Contact, handle: ActiveTaskCodec) {
        runBlocking {
            fsmp.terminateAllInvalidSessions(contact, handle)
        }
    }
}
