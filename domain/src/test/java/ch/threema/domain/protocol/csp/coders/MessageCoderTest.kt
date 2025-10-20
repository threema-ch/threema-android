/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.coders

import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceScope
import ch.threema.domain.helpers.InMemoryContactStore
import ch.threema.domain.helpers.InMemoryIdentityStore
import ch.threema.domain.models.Contact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData.OfferData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.testhelpers.TestHelpers.noopContactStore
import ch.threema.domain.testhelpers.TestHelpers.noopIdentityStore
import ch.threema.domain.testhelpers.TestHelpers.noopNonceFactory
import ch.threema.domain.testhelpers.TestHelpers.setMessageDefaultSenderAndReceiver
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageCoderTest {
    private val encoder: MessageCoder
    private val decoder: MessageCoder

    init {

        val myPublicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        val myPrivateKey = ByteArray(NaCl.SECRET_KEY_BYTES)
        val peerPublicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        val peerPrivateKey = ByteArray(NaCl.SECRET_KEY_BYTES)

        NaCl.generateKeypairInPlace(myPublicKey, myPrivateKey)
        NaCl.generateKeypairInPlace(peerPublicKey, peerPrivateKey)

        val myIdentityStore: IdentityStore = InMemoryIdentityStore(
            "01234567",
            null,
            myPrivateKey,
            "Me",
        )

        val peerIdentityStore: IdentityStore = InMemoryIdentityStore(
            "0ABCDEFG",
            null,
            peerPrivateKey,
            "Peer",
        )

        val myContactStore: ContactStore = InMemoryContactStore()
        myContactStore.addContact(Contact("0ABCDEFG", peerPublicKey, VerificationLevel.UNVERIFIED))

        val peerContactStore: ContactStore = InMemoryContactStore()
        peerContactStore.addContact(Contact("01234567", myPublicKey, VerificationLevel.UNVERIFIED))

        encoder = MessageCoder(
            myContactStore,
            myIdentityStore,
        )

        decoder = MessageCoder(
            peerContactStore,
            peerIdentityStore,
        )
    }

    private fun box(msg: AbstractMessage): MessageBox {
        val messageCoder = MessageCoder(
            noopContactStore,
            noopIdentityStore,
        )
        val nonceFactory = noopNonceFactory
        val nonce: ByteArray = nonceFactory.next(NonceScope.CSP).bytes
        return messageCoder.encode(msg, nonce)
    }

    @Test
    fun testVoipFlagsOffer() {
        val voipCallOfferMessage = VoipCallOfferMessage()
        setMessageDefaultSenderAndReceiver(voipCallOfferMessage)
        val voipCallOfferData = VoipCallOfferData()
        val offerData = OfferData()
            .setSdp("testsdp")
            .setSdpType("offer")
        voipCallOfferData.setOfferData(offerData)
        voipCallOfferMessage.data = voipCallOfferData
        val messageBox = this.box(voipCallOfferMessage)
        // Flags: Voip + Push
        assertEquals((0x20 or 0x01).toLong(), messageBox.flags.toLong())
    }

    @Test
    fun testVoipFlagsAnswer() {
        val voipCallAnswerMessage = VoipCallAnswerMessage()
        val voipCallAnswerData = VoipCallAnswerData()
            .setAction(VoipCallAnswerData.Action.REJECT)
            .setAnswerData(null)
            .setRejectReason(VoipCallAnswerData.RejectReason.BUSY)
        voipCallAnswerMessage.data = voipCallAnswerData
        setMessageDefaultSenderAndReceiver(voipCallAnswerMessage)
        val messageBox = this.box(voipCallAnswerMessage)
        // Flags: Voip + Push
        assertEquals((0x20 or 0x01).toLong(), messageBox.flags.toLong())
    }

    @Test
    fun testVoipFlagsCandidates() {
        val voipICECandidatesMessage = VoipICECandidatesMessage()
        val voipICECandidatesData = VoipICECandidatesData()
            .setCandidates(
                arrayOf(
                    VoipICECandidatesData.Candidate("testcandidate1", "testmid1", 42, "testufrag1"),
                    VoipICECandidatesData.Candidate("testcandidate2", "testmid2", 23, "testufrag2"),
                ),
            )
        voipICECandidatesMessage.data = voipICECandidatesData
        setMessageDefaultSenderAndReceiver(voipICECandidatesMessage)
        val messageBox = this.box(voipICECandidatesMessage)
        // Flags: Voip + Push
        assertEquals((0x20 or 0x01).toLong(), messageBox.flags.toLong())
    }

    @Test
    fun testVoipFlagsHangup() {
        val voipCallHangupMessage = VoipCallHangupMessage()
        voipCallHangupMessage.data = VoipCallHangupData()
        setMessageDefaultSenderAndReceiver(voipCallHangupMessage)
        val messageBox = this.box(voipCallHangupMessage)
        // Flags: Push only
        assertEquals(0x01, messageBox.flags.toLong())
    }

    @Test
    fun testDeserializeTextMessage() {
        val textMessage = TextMessage()

        setAndAssertText(textMessage, "Hello")
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, ".")
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, String(Character.toChars(0x1F4A1)))
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, "")

        assertFailsWith<BadMessageException> { encodeAndDecode(textMessage) }
        setAndAssertText(textMessage, "a")
        val body: ByteArray = encode(textMessage).box
        assertFailsWith<BadMessageException> { // Invalid offset
            TextMessage.fromByteArray(body, body.size, body.size)
        }

        assertFailsWith<BadMessageException> { // Invalid length
            TextMessage.fromByteArray(body, 1, body.size)
        }
    }

    private fun setAndAssertText(message: TextMessage, text: String) {
        message.text = text
        assertEquals(text, message.text)
    }

    @Test
    fun testDeserializeGroupTextMessage() {
        val textMessage = GroupTextMessage()
        textMessage.groupCreator = "01234567"
        textMessage.apiGroupId = GroupId()

        setAndAssertText(textMessage, "Hello")
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, ".")
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, String(Character.toChars(0x1F4A1)))
        assertEqualMessage(textMessage, encodeAndDecode(textMessage))

        setAndAssertText(textMessage, "")
        assertFailsWith<BadMessageException> { encodeAndDecode(textMessage) }

        setAndAssertText(textMessage, "a")
        val body = encode(textMessage).box
        assertFailsWith<BadMessageException> { // Invalid offset
            GroupTextMessage.fromByteArray(body, body.size, body.size)
        }

        assertFailsWith<BadMessageException> { // Invalid length
            GroupTextMessage.fromByteArray(body, 1, body.size)
        }
    }

    private fun setAndAssertText(message: GroupTextMessage, text: String) {
        message.text = text
        assertEquals(text, message.text)
    }

    private fun assertEqualMessage(expected: AbstractMessage, actual: AbstractMessage) {
        assertContentEquals(expected.body, actual.body)
    }

    private fun encode(abstractMessage: AbstractMessage): MessageBox {
        abstractMessage.toIdentity = "0ABCDEFG"
        abstractMessage.fromIdentity = "01234567"
        return encoder.encode(abstractMessage, ByteArray(NaCl.NONCE_BYTES))
    }

    private fun encodeAndDecode(abstractMessage: AbstractMessage): AbstractMessage =
        decoder.decode(encode(abstractMessage))
}
