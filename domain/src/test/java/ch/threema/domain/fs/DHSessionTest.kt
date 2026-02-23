package ch.threema.domain.fs

import ch.threema.domain.helpers.DummyUsers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DHSessionTest {

    // Alice is the initiator
    private val initiatorDHSession: DHSession by lazy {
        DHSession(
            DummyUsers.getContactForUser(DummyUsers.BOB),
            DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE),
        )
    }

    // Bob gets an init message from Alice with her ephemeral public key
    private val responderDHSession: DHSession by lazy {
        DHSession(
            initiatorDHSession.id,
            DHSession.getSupportedVersionRange(),
            initiatorDHSession.myEphemeralPublicKey,
            DummyUsers.getContactForUser(DummyUsers.ALICE),
            DummyUsers.getIdentityStoreForUser(DummyUsers.BOB),
        )
    }

    @Test
    fun test2DHKeyExchange() {
        // At this point, both parties should have the same 2DH chain keys
        assertNotNull(initiatorDHSession.getMyRatchet2DH())
        assertNotNull(responderDHSession.getPeerRatchet2DH())
        assertContentEquals(
            initiatorDHSession.getMyRatchet2DH()!!.currentEncryptionKey,
            responderDHSession.getPeerRatchet2DH()!!.currentEncryptionKey,
        )
    }

    @Test
    fun test4DHKeyExchange() {
        // Now Bob sends his ephemeral public key back to Alice
        initiatorDHSession.processAccept(
            DHSession.getSupportedVersionRange(),
            responderDHSession.myEphemeralPublicKey,
            DummyUsers.getContactForUser(DummyUsers.BOB),
            DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE),
        )

        // At this point, both parties should have the same 4DH chain keys
        assertNotNull(initiatorDHSession.getMyRatchet4DH())
        assertNotNull(initiatorDHSession.getPeerRatchet4DH())
        assertNotNull(responderDHSession.getMyRatchet4DH())
        assertNotNull(responderDHSession.getPeerRatchet4DH())
        assertContentEquals(
            initiatorDHSession.getMyRatchet4DH()!!.currentEncryptionKey,
            responderDHSession.getPeerRatchet4DH()!!.currentEncryptionKey,
        )
        assertContentEquals(
            initiatorDHSession.getPeerRatchet4DH()!!.currentEncryptionKey,
            responderDHSession.getMyRatchet4DH()!!.currentEncryptionKey,
        )

        // The keys should be different for both directions
        assertFalse(
            initiatorDHSession.getMyRatchet4DH()!!.currentEncryptionKey
                .contentEquals(responderDHSession.getMyRatchet4DH()!!.currentEncryptionKey),
        )

        // Ensure that the private keys have been discarded
        assertNull(initiatorDHSession.myEphemeralPrivateKey)
        assertNull(responderDHSession.myEphemeralPrivateKey)
    }

    @Test
    fun testKDFRotation() {
        test4DHKeyExchange()

        assertNotNull(initiatorDHSession.getMyRatchet4DH())
        assertNotNull(initiatorDHSession.getPeerRatchet4DH())
        assertNotNull(responderDHSession.getMyRatchet4DH())
        assertNotNull(responderDHSession.getPeerRatchet4DH())

        // Turn the 4DH ratchet a couple of times on both sides and ensure the keys match
        for (i in 0..2) {
            initiatorDHSession.getMyRatchet4DH()!!.turn()
            responderDHSession.getPeerRatchet4DH()!!.turn()

            assertContentEquals(
                initiatorDHSession.getMyRatchet4DH()!!.currentEncryptionKey,
                responderDHSession.getPeerRatchet4DH()!!.currentEncryptionKey,
            )

            initiatorDHSession.getPeerRatchet4DH()!!.turn()
            responderDHSession.getMyRatchet4DH()!!.turn()

            assertContentEquals(
                initiatorDHSession.getPeerRatchet4DH()!!.currentEncryptionKey,
                responderDHSession.getMyRatchet4DH()!!.currentEncryptionKey,
            )
        }

        // Turn the 4DH ratchet several times on one side and verify that the other side can catch up
        val myTurns = 3
        for (i in 0..<myTurns) {
            initiatorDHSession.getMyRatchet4DH()!!.turn()
        }
        val responderTurns = responderDHSession.getPeerRatchet4DH()!!.turnUntil(initiatorDHSession.getMyRatchet4DH()!!.counter)
        assertEquals(myTurns.toLong(), responderTurns.toLong())
        assertContentEquals(
            initiatorDHSession.getMyRatchet4DH()!!.currentEncryptionKey,
            responderDHSession.getPeerRatchet4DH()!!.currentEncryptionKey,
        )
    }
}
