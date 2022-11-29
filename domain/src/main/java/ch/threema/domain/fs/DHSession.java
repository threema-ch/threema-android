/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;

import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.ThreemaKDF;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.Contact;
import ch.threema.domain.stores.IdentityStoreInterface;
import ove.crypto.digest.Blake2b;

/**
 * ECDH key exchange and ratcheting session for forward security
 */
public class DHSession {
	protected static final String KE_SALT_2DH_PREFIX = "ke-2dh-";
	protected static final String KE_SALT_4DH_PREFIX = "ke-4dh-";

	public static final String KDF_PERSONAL = "3ma-e2e";

	/**
	 * 16 byte session ID, used for detecting when the other party has lost
	 * the session information, has restored an old backup etc.
	 */
	@NonNull private final DHSessionId id;

	/**
	 * My ("Alice") Threema identity.
	 */
	@NonNull private final String myIdentity;

	/**
	 * Peer ("Bob") Threema identity.
	 */
	@NonNull private final String peerIdentity;

	/**
	 * ECDH private key used on this side of the session.
	 * Discarded as soon as an Accept packet has been received from the peer.
	 */
	@Nullable private byte[] myEphemeralPrivateKey;

	/**
	 * ECDH public key used on this side of the session.
	 */
	@NonNull private final byte[] myEphemeralPublicKey;

	@Nullable protected KDFRatchet myRatchet2DH;
	@Nullable protected KDFRatchet myRatchet4DH;
	@Nullable protected KDFRatchet peerRatchet2DH;
	@Nullable protected KDFRatchet peerRatchet4DH;

	/**
	 * Create a new DHSession as an initiator, using a new random session ID and
	 * a new random private key.
	 */
	public DHSession(@NonNull Contact contact, @NonNull IdentityStoreInterface identityStoreInterface) {
		this.id = new DHSessionId();
		this.myIdentity = identityStoreInterface.getIdentity();
		this.peerIdentity = contact.getIdentity();

		this.myEphemeralPublicKey = new byte[NaCl.PUBLICKEYBYTES];
		this.myEphemeralPrivateKey = new byte[NaCl.SECRETKEYBYTES];
		NaCl.genkeypair(this.myEphemeralPublicKey, this.myEphemeralPrivateKey);

		byte[] dhStaticStatic = identityStoreInterface.calcSharedSecret(contact.getPublicKey());
		byte[] dhStaticEphemeral = new NaCl(myEphemeralPrivateKey, contact.getPublicKey()).getPrecomputed();
		this.initKDF2DH(dhStaticStatic, dhStaticEphemeral, false);
	}

	/**
	 * Create a new DHSession as a responder.
	 */
	public DHSession(@NonNull DHSessionId id,
	                 byte[] peerEphemeralPublicKey,
	                 Contact contact,
	                 IdentityStoreInterface identityStoreInterface) {

		if (peerEphemeralPublicKey.length != NaCl.SECRETKEYBYTES) {
			throw new IllegalArgumentException("Invalid peer ephemeral public key length");
		}

		this.id = id;
		this.myIdentity = identityStoreInterface.getIdentity();
		this.peerIdentity = contact.getIdentity();

		this.myEphemeralPublicKey = completeKeyExchange(peerEphemeralPublicKey, contact, identityStoreInterface);
	}

	/**
	 * Create a DHSession with existing data, e.g. read from a persistent store.
	 */
	public DHSession(@NonNull DHSessionId id,
                     @NonNull String myIdentity,
                     @NonNull String peerIdentity,
                     @Nullable byte[] myEphemeralPrivateKey,
                     @NonNull byte[] myEphemeralPublicKey,
                     @Nullable KDFRatchet myRatchet2DH,
                     @Nullable KDFRatchet myRatchet4DH,
                     @Nullable KDFRatchet peerRatchet2DH,
                     @Nullable KDFRatchet peerRatchet4DH) {
		this.id = id;
		this.myIdentity = myIdentity;
		this.peerIdentity = peerIdentity;
		this.myEphemeralPrivateKey = myEphemeralPrivateKey;
		this.myEphemeralPublicKey = myEphemeralPublicKey;
		this.setMyRatchet2DH(myRatchet2DH);
		this.setMyRatchet4DH(myRatchet4DH);
		this.setPeerRatchet2DH(peerRatchet2DH);
		this.setPeerRatchet4DH(peerRatchet4DH);
	}

	@NonNull public DHSessionId getId() {
		return id;
	}

	@NonNull
	public String getMyIdentity() {
		return myIdentity;
	}

	@NonNull
	public String getPeerIdentity() {
		return peerIdentity;
	}

	@NonNull public byte[] getMyEphemeralPublicKey() {
		return myEphemeralPublicKey;
	}

	@Nullable
	public byte[] getMyEphemeralPrivateKey() {
		return myEphemeralPrivateKey;
	}

	@Nullable
	public KDFRatchet getMyRatchet2DH() {
		return myRatchet2DH;
	}

	@Nullable
	public KDFRatchet getMyRatchet4DH() {
		return myRatchet4DH;
	}

	@Nullable
	public KDFRatchet getPeerRatchet2DH() {
		return peerRatchet2DH;
	}

	@Nullable
	public KDFRatchet getPeerRatchet4DH() {
		return peerRatchet4DH;
	}

	/**
	 * Process a DH accept received from the peer.
	 */
	public void processAccept(byte[] peerEphemeralPublicKey, @NonNull Contact contact, @NonNull IdentityStoreInterface identityStoreInterface) throws DHSession.MissingEphemeralPrivateKeyException {
		if (myEphemeralPrivateKey == null) {
			throw new DHSession.MissingEphemeralPrivateKeyException("Missing ephemeral private key");
		}

		// Derive 4DH root key
		byte[] dhStaticStatic = identityStoreInterface.calcSharedSecret(contact.getPublicKey());
		byte[] dhStaticEphemeral = new NaCl(myEphemeralPrivateKey, contact.getPublicKey()).getPrecomputed();
		byte[] dhEphemeralStatic = identityStoreInterface.calcSharedSecret(peerEphemeralPublicKey);
		byte[] dhEphemeralEphemeral = new NaCl(myEphemeralPrivateKey, peerEphemeralPublicKey).getPrecomputed();
		this.initKDF4DH(dhStaticStatic, dhStaticEphemeral, dhEphemeralStatic, dhEphemeralEphemeral);

		// myPrivateKey is not needed anymore at this point
		Arrays.fill(this.myEphemeralPrivateKey, (byte) 0);
		this.myEphemeralPrivateKey = null;

		// My 2DH ratchet is not needed anymore at this point, but the peer 2DH ratchet is still
		// needed until we receive the first 4DH message, as there may be some 2DH messages still
		// in flight.
		this.setMyRatchet2DH(null);
	}

	/**
	 * Discard the 2DH peer ratchet associated with this session (because a 4DH message has been
	 * received).
	 */
	public void discardPeerRatchet2DH() {
		this.setPeerRatchet2DH(null);
	}

	protected void initKDF2DH(byte[] dhStaticStatic, byte[] dhStaticEphemeral, boolean peer) {
		// We can feed the combined 64 bytes directly into BLAKE2b
		ThreemaKDF kdf = new ThreemaKDF(KDF_PERSONAL);
		if (peer) {
			byte[] peerK0 = kdf.deriveKey(KE_SALT_2DH_PREFIX + peerIdentity, Utils.concatByteArrays(dhStaticStatic, dhStaticEphemeral));
			this.peerRatchet2DH = new KDFRatchet(1, peerK0);
		} else {
			byte[] myK0 = kdf.deriveKey(KE_SALT_2DH_PREFIX + myIdentity, Utils.concatByteArrays(dhStaticStatic, dhStaticEphemeral));
			this.myRatchet2DH = new KDFRatchet(1, myK0);
		}
	}

	protected void initKDF4DH(byte[] dhStaticStatic, byte[] dhStaticEphemeral, byte[] dhEphemeralStatic, byte[] dhEphemeralEphemeral) {
		// The combined 128 bytes need to be hashed with plain BLAKE2b (512 bit output) first
		Blake2b.Digest digest = Blake2b.Digest.newInstance();
		byte[] intermediateHash = digest.digest(Utils.concatByteArrays(dhStaticStatic, dhStaticEphemeral, dhEphemeralStatic, dhEphemeralEphemeral));

		ThreemaKDF kdf = new ThreemaKDF(KDF_PERSONAL);
		byte[] myK = kdf.deriveKey(KE_SALT_4DH_PREFIX + myIdentity, intermediateHash);
		byte[] peerK = kdf.deriveKey(KE_SALT_4DH_PREFIX + peerIdentity, intermediateHash);

		this.myRatchet4DH = new KDFRatchet(1, myK);
		this.peerRatchet4DH = new KDFRatchet(1, peerK);
	}

	protected void setMyRatchet2DH(@Nullable KDFRatchet myRatchet2DH) {
		this.myRatchet2DH = myRatchet2DH;
	}

	protected void setMyRatchet4DH(@Nullable KDFRatchet myRatchet4DH) {
		this.myRatchet4DH = myRatchet4DH;
	}

	protected void setPeerRatchet2DH(@Nullable KDFRatchet peerRatchet2DH) {
		this.peerRatchet2DH = peerRatchet2DH;
	}

	protected void setPeerRatchet4DH(@Nullable KDFRatchet peerRatchet4DH) {
		this.peerRatchet4DH = peerRatchet4DH;
	}

	private byte[] completeKeyExchange(byte[] peerEphemeralPublicKey, Contact contact, IdentityStoreInterface identityStoreInterface) {
		byte[] myEphemeralPublicKeyLocal = new byte[NaCl.PUBLICKEYBYTES];
		byte[] myEphemeralPrivateKeyLocal = new byte[NaCl.SECRETKEYBYTES];
		NaCl.genkeypair(myEphemeralPublicKeyLocal, myEphemeralPrivateKeyLocal);

		// Derive 2DH root key
		byte[] dhStaticStatic = identityStoreInterface.calcSharedSecret(contact.getPublicKey());
		byte[] dhStaticEphemeral = identityStoreInterface.calcSharedSecret(peerEphemeralPublicKey);
		this.initKDF2DH(dhStaticStatic, dhStaticEphemeral, true);

		// Derive 4DH root key
		byte[] dhEphemeralStatic = new NaCl(myEphemeralPrivateKeyLocal, contact.getPublicKey()).getPrecomputed();
		byte[] dhEphemeralEphemeral = new NaCl(myEphemeralPrivateKeyLocal, peerEphemeralPublicKey).getPrecomputed();
		this.initKDF4DH(dhStaticStatic, dhStaticEphemeral, dhEphemeralStatic, dhEphemeralEphemeral);

		// myPrivateKey is not needed anymore at this point
		Arrays.fill(myEphemeralPrivateKeyLocal, (byte) 0);

		return myEphemeralPublicKeyLocal;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DHSession dhSession = (DHSession) o;
		return getId().equals(dhSession.getId()) && myIdentity.equals(dhSession.myIdentity) && peerIdentity.equals(dhSession.peerIdentity) && Objects.equals(getMyRatchet2DH(), dhSession.getMyRatchet2DH()) && Objects.equals(getMyRatchet4DH(), dhSession.getMyRatchet4DH()) && Objects.equals(getPeerRatchet2DH(), dhSession.getPeerRatchet2DH()) && Objects.equals(getPeerRatchet4DH(), dhSession.getPeerRatchet4DH());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), myIdentity, peerIdentity, getMyRatchet2DH(), getMyRatchet4DH(), getPeerRatchet2DH(), getPeerRatchet4DH());
	}

	public static class MissingEphemeralPrivateKeyException extends ThreemaException {
		public MissingEphemeralPrivateKeyException(final String msg) {
			super(msg);
		}
	}
}
