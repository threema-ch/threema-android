/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.fs;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.protocol.csp.messages.BadMessageException;

public class DHSessionTest {
    private DHSession initiatorDHSession;
    private DHSession responderDHSession;

    public void createSessions() throws BadMessageException {
        // Alice is the initiator
        this.initiatorDHSession = new DHSession(
            DummyUsers.getContactForUser(DummyUsers.BOB),
            DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
        );

        // Bob gets an init message from Alice with her ephemeral public key
        this.responderDHSession = new DHSession(
            this.initiatorDHSession.getId(),
            DHSession.SUPPORTED_VERSION_RANGE,
            this.initiatorDHSession.getMyEphemeralPublicKey(),
            DummyUsers.getContactForUser(DummyUsers.ALICE),
            DummyUsers.getIdentityStoreForUser(DummyUsers.BOB)
        );
    }

    @Test
    public void test2DHKeyExchange() throws BadMessageException {
        createSessions();

        // At this point, both parties should have the same 2DH chain keys
        Assert.assertNotNull(this.initiatorDHSession.getMyRatchet2DH());
        Assert.assertNotNull(this.responderDHSession.getPeerRatchet2DH());
        Assert.assertArrayEquals(
            this.initiatorDHSession.getMyRatchet2DH().getCurrentEncryptionKey(),
            this.responderDHSession.getPeerRatchet2DH().getCurrentEncryptionKey()
        );
    }

    @Test
    public void test4DHKeyExchange() throws DHSession.MissingEphemeralPrivateKeyException, BadMessageException {
        createSessions();

        // Now Bob sends his ephemeral public key back to Alice
        this.initiatorDHSession.processAccept(
            DHSession.SUPPORTED_VERSION_RANGE,
            this.responderDHSession.getMyEphemeralPublicKey(),
            DummyUsers.getContactForUser(DummyUsers.BOB),
            DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE)
        );

        // At this point, both parties should have the same 4DH chain keys
        Assert.assertNotNull(this.initiatorDHSession.getMyRatchet4DH());
        Assert.assertNotNull(this.initiatorDHSession.getPeerRatchet4DH());
        Assert.assertNotNull(this.responderDHSession.getMyRatchet4DH());
        Assert.assertNotNull(this.responderDHSession.getPeerRatchet4DH());
        Assert.assertArrayEquals(
            this.initiatorDHSession.getMyRatchet4DH().getCurrentEncryptionKey(),
            this.responderDHSession.getPeerRatchet4DH().getCurrentEncryptionKey()
        );
        Assert.assertArrayEquals(
            this.initiatorDHSession.getPeerRatchet4DH().getCurrentEncryptionKey(),
            this.responderDHSession.getMyRatchet4DH().getCurrentEncryptionKey()
        );

        // The keys should be different for both directions
        Assert.assertNotEquals(
            this.initiatorDHSession.getMyRatchet4DH().getCurrentEncryptionKey(),
            this.responderDHSession.getMyRatchet4DH().getCurrentEncryptionKey()
        );

        // Ensure that the private keys have been discarded
        Assert.assertNull(this.initiatorDHSession.getMyEphemeralPrivateKey());
        Assert.assertNull(this.responderDHSession.getMyEphemeralPrivateKey());
    }

    @Test
    public void testKDFRotation() throws KDFRatchet.RatchetRotationException, DHSession.MissingEphemeralPrivateKeyException, BadMessageException {
        test4DHKeyExchange();

        Assert.assertNotNull(this.initiatorDHSession.getMyRatchet4DH());
        Assert.assertNotNull(this.initiatorDHSession.getPeerRatchet4DH());
        Assert.assertNotNull(this.responderDHSession.getMyRatchet4DH());
        Assert.assertNotNull(this.responderDHSession.getPeerRatchet4DH());

        // Turn the 4DH ratchet a couple of times on both sides and ensure the keys match
        for (int i = 0; i < 3; i++) {
            this.initiatorDHSession.getMyRatchet4DH().turn();
            this.responderDHSession.getPeerRatchet4DH().turn();

            Assert.assertArrayEquals(
                this.initiatorDHSession.getMyRatchet4DH().getCurrentEncryptionKey(),
                this.responderDHSession.getPeerRatchet4DH().getCurrentEncryptionKey()
            );

            this.initiatorDHSession.getPeerRatchet4DH().turn();
            this.responderDHSession.getMyRatchet4DH().turn();

            Assert.assertArrayEquals(
                this.initiatorDHSession.getPeerRatchet4DH().getCurrentEncryptionKey(),
                this.responderDHSession.getMyRatchet4DH().getCurrentEncryptionKey()
            );
        }

        // Turn the 4DH ratchet several times on one side and verify that the other side can catch up
        int myTurns = 3;
        for (int i = 0; i < myTurns; i++) {
            this.initiatorDHSession.getMyRatchet4DH().turn();
        }
        int responderTurns = this.responderDHSession.getPeerRatchet4DH().turnUntil(this.initiatorDHSession.getMyRatchet4DH().getCounter());
        Assert.assertEquals(myTurns, responderTurns);
        Assert.assertArrayEquals(
            this.initiatorDHSession.getMyRatchet4DH().getCurrentEncryptionKey(),
            this.responderDHSession.getPeerRatchet4DH().getCurrentEncryptionKey()
        );
    }
}
