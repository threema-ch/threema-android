/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.ThreemaKDF;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.Contact;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;
import ove.crypto.digest.Blake2b;

/**
 * ECDH key exchange and ratcheting session for forward security
 */
public class DHSession {
	private final static Logger logger = LoggingUtil.getThreemaLogger("DHSession");

	protected static final String KE_SALT_2DH_PREFIX = "ke-2dh-";
	protected static final String KE_SALT_4DH_PREFIX = "ke-4dh-";

	public static final String KDF_PERSONAL = "3ma-e2e";

	public static final Version SUPPORTED_VERSION_MIN = Version.V1_0;
	public static final Version SUPPORTED_VERSION_MAX = Version.V1_1;
	public static final VersionRange SUPPORTED_VERSION_RANGE = VersionRange.newBuilder()
		.setMin(SUPPORTED_VERSION_MIN.getNumber())
		.setMax(SUPPORTED_VERSION_MAX.getNumber())
		.build();

	public enum State {
		/**
		 * Locally initiated, out 2DH, in none
		 */
		L20,

		/**
		 * Remotely or locally initiated, out 4DH, in 4DH
		 */
		RL44,

		/**
		 * Remotely initiated, in 2DH, out none
		 */
		R20,

		/**
		 * Remotely initiated, in 2DH, out 4DH
		 */
		R24,
	}

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
	 * Negotiated version. Available once an Accept was sent or received.
	 */
	@Nullable private Version negotiatedVersion;

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

		this.negotiatedVersion = null;
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
					 @NonNull VersionRange peerSupportedVersionRange,
	                 byte[] peerEphemeralPublicKey,
	                 Contact contact,
	                 IdentityStoreInterface identityStoreInterface) throws BadMessageException {
		final Version negotiatedVersion = DHSession.negotiateMajorAndMinorVersion(
			SUPPORTED_VERSION_RANGE, peerSupportedVersionRange);

		if (peerEphemeralPublicKey.length != NaCl.SECRETKEYBYTES) {
			throw new BadMessageException("Invalid peer ephemeral public key length");
		}

		this.id = id;
		this.myIdentity = identityStoreInterface.getIdentity();
		this.peerIdentity = contact.getIdentity();
		this.negotiatedVersion = negotiatedVersion;

		this.myEphemeralPublicKey = completeKeyExchange(peerEphemeralPublicKey, contact, identityStoreInterface);
	}

	/**
	 * Create a DHSession with existing data, e.g. read from a persistent store.
	 */
	public DHSession(@NonNull DHSessionId id,
                     @NonNull String myIdentity,
                     @NonNull String peerIdentity,
                     @Nullable final Version negotiatedVersion,
                     @Nullable byte[] myEphemeralPrivateKey,
                     @NonNull byte[] myEphemeralPublicKey,
                     @Nullable KDFRatchet myRatchet2DH,
                     @Nullable KDFRatchet myRatchet4DH,
                     @Nullable KDFRatchet peerRatchet2DH,
                     @Nullable KDFRatchet peerRatchet4DH) {
		this.id = id;
		this.myIdentity = myIdentity;
		this.peerIdentity = peerIdentity;
		this.negotiatedVersion = negotiatedVersion;
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
	 * The current negotiated major version plus the locally supported minor version.
	 *
	 * Note: This will always be greater or equal to the bidirectionally negotiated version.
	 */
	@NonNull
	public Version getAnnouncedVersion() {
		switch (getNegotiatedVersion()) {
			case V1_0:
			case V1_1:
				return Version.V1_1;
			default:
				throw new IllegalStateException("Unknown negotiated FS version: " + this.negotiatedVersion);
		}
	}

	/**
	 * The current negotiated version.
	 *
	 * This is the minimum of the version currently supported by local and the version announced
	 * by remote.
	 */
	@NonNull
	public Version getNegotiatedVersion() {
		// Negotiation did not complete yet, so we are constrained to the minimum announced
		// version.
		return Objects.requireNonNullElse(this.negotiatedVersion, SUPPORTED_VERSION_MIN);
	}

	/**
	 * Get the state of the DH session. Note that this state depends on the availability of the
	 * ratchets, which needs great care when adding and remove the ratchets.
	 *
	 * @return the current state of the DH session
	 */
	@NonNull
	public State getState() {
		if (myRatchet2DH == null
			&& myRatchet4DH != null
			&& peerRatchet2DH == null
			&& peerRatchet4DH != null) {
			return State.RL44;
		} else if (myRatchet2DH == null
			&& peerRatchet2DH != null
			&& myRatchet4DH != null
			&& peerRatchet4DH != null) {
			return State.R24;
		} else if (myRatchet2DH != null
			&& myRatchet4DH == null
			&& peerRatchet2DH == null
			&& peerRatchet4DH == null) {
			return State.L20;
		} else if (myRatchet2DH == null
			&& myRatchet4DH == null
			&& peerRatchet2DH != null
			&& peerRatchet4DH == null) {
			return State.R20;
		} else {
			throw new IllegalStateException(String.format("Illegal DH session state:" +
					"myRatchet2DH=%s, myRatchet4DH=%s, peerRatchet2DH=%s, peerRatchet4DH=%s",
				myRatchet2DH != null,
				myRatchet4DH != null,
				peerRatchet2DH != null,
				peerRatchet4DH != null
			));
		}
	}

	/**
	 * Validate the applied version of a message against the negotiated version.
	 * @returns The updated negotiated version to be committed or null in case validation failed.
	 */
	@Nullable
	public Version validateAppliedVersion(int appliedVersion) throws BadMessageException {
		final Version currentNegotiatedVersion = getNegotiatedVersion();

		// The major version must match, the minor version must be >= negotiated.
		if ((appliedVersion & 0xff00) != (currentNegotiatedVersion.getNumber() & 0xff00)
			|| (appliedVersion & 0x00ff) < (currentNegotiatedVersion.getNumber() & 0x00ff)) {
			return null;
		}

		// Take the minimum of the maximum supported versions
		final Version updatedNegotiatedVersion = Version.forNumber(
			Math.min(getAnnouncedVersion().getNumber(), appliedVersion));
		if (updatedNegotiatedVersion == null) {
			throw new BadMessageException("Unable to negotiate FS version: negotiated=" + currentNegotiatedVersion + ", applied=" + appliedVersion);
		}
		return updatedNegotiatedVersion;
	}

	/**
	 * Update the current negotiated version with the negotiated version return from validation.
	 */
	public void commitNegotiatedVersion(@NonNull Version updatedNegotiatedVersion) {
		if (negotiatedVersion == null || negotiatedVersion.getNumber() != updatedNegotiatedVersion.getNumber()) {
			logger.info("Updated negotiated version from {} to {} in session {}",
				negotiatedVersion,
				updatedNegotiatedVersion,
				getId()
			);
		}
		this.negotiatedVersion = updatedNegotiatedVersion;
	}

	/**
	 * Process a DH accept received from the peer.
	 */
	public void processAccept(
		@NonNull VersionRange peerSupportedVersionRange,
		byte[] peerEphemeralPublicKey,
		@NonNull Contact contact,
		@NonNull IdentityStoreInterface identityStoreInterface
	) throws DHSession.MissingEphemeralPrivateKeyException, BadMessageException {
		// Note: This mitigates accepting twice because we remove the ephemeral private key after the first accept.
		if (myEphemeralPrivateKey == null) {
			throw new DHSession.MissingEphemeralPrivateKeyException("Missing ephemeral private key");
		}

		// Determine negotiated version
		final Version negotiatedVersion = DHSession.negotiateMajorAndMinorVersion(
			SUPPORTED_VERSION_RANGE, peerSupportedVersionRange);

		// Derive 4DH root key
		byte[] dhStaticStatic = identityStoreInterface.calcSharedSecret(contact.getPublicKey());
		byte[] dhStaticEphemeral = new NaCl(myEphemeralPrivateKey, contact.getPublicKey()).getPrecomputed();
		byte[] dhEphemeralStatic = identityStoreInterface.calcSharedSecret(peerEphemeralPublicKey);
		byte[] dhEphemeralEphemeral = new NaCl(myEphemeralPrivateKey, peerEphemeralPublicKey).getPrecomputed();
		this.initKDF4DH(dhStaticStatic, dhStaticEphemeral, dhEphemeralStatic, dhEphemeralEphemeral);

		// Validation complete, update state
		this.negotiatedVersion = negotiatedVersion;
		// myPrivateKey is not needed anymore at this point
		Arrays.fill(this.myEphemeralPrivateKey, (byte) 0);
		this.myEphemeralPrivateKey = null;

		// My 2DH ratchet is not needed anymore at this point, but the peer 2DH ratchet is still
		// needed until we receive the first 4DH message, as there may be some 2DH messages still
		// in flight. Note that this is also needed to be able to correctly determine the current
		// session state.
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

	private static @NonNull Version negotiateMajorAndMinorVersion(
		@NonNull VersionRange localVersion,
		@NonNull VersionRange remoteVersion
	) throws BadMessageException {
		// Older clients may not provide a version range. Map min and max to V1.0 in that case.
		if (remoteVersion.getMin() == 0 && remoteVersion.getMax() == 0) {
			remoteVersion = VersionRange.newBuilder()
				.setMin(Version.V1_0_VALUE)
				.setMax(Version.V1_0_VALUE)
				.build();
		}

		// Validate version range
		if (remoteVersion.getMax() < remoteVersion.getMin()) {
			throw new BadMessageException("Invalid FS version range: min=" + remoteVersion.getMin() + ", max=" + remoteVersion.getMax());
		}

		// Ensure the minimum version is supported
		if (remoteVersion.getMin() < localVersion.getMin()) {
			throw new BadMessageException("Unsupported minimum FS version: local-min=" + localVersion.getMin() + ", remote-min=" + remoteVersion.getMin());
		}

		// Take the minimum of the maximum supported versions
		final Version negotiatedVersion = Version.forNumber(Math.min(localVersion.getMax(), remoteVersion.getMax()));
		if (negotiatedVersion == null) {
			throw new BadMessageException("Unable to negotiate FS version: local-max=" + localVersion.getMax() + ", remote-max=" + remoteVersion.getMax());
		}
		return negotiatedVersion;
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

	@NonNull
	public String toDebugString() {
		return String.format(
			"(id=%s, negotiatedVersion=%s, announcedVersion=%s)",
			getId(),
			getNegotiatedVersion(),
			getAnnouncedVersion()
		);
	}
}
