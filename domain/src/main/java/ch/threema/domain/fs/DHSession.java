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
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.fs.Encapsulated;
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
	public static final Version SUPPORTED_VERSION_MAX = Version.V1_2;
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

	public static class DHVersions {
		/** Version for local/outgoing 4DH messages */
		@NonNull public final Version local;
		/** Version for remote/incoming 4DH messages */
		@NonNull public final Version remote;

		/**
		 * Restore the 4DH versions from a database.
		 */
		@Nullable
		public static DHVersions restored(@Nullable Version local, @Nullable Version remote) {
			if (local != null && remote != null) {
				return new DHVersions(local, remote);
			} else {
				return null;
			}
		}

		/**
		 * Bootstrap the initial 4DH versions from the negotiated version of the Init/Accept flow.
		 */
		@NonNull public static DHVersions negotiated(@NonNull Version version) {
			return new DHVersions(version, version);
		}

		/**
		 * 4DH versions to be updated from older versions after successful processing of an
		 * encapsulated message.
		 */
		@NonNull public static DHVersions updated(@NonNull Version local, @NonNull Version remote) {
			return new DHVersions(local, remote);
		}

		private DHVersions(@NonNull Version local, @NonNull Version remote) {
			this.local = local;
			this.remote = remote;
		}

		@Override
		public String toString() {
			return String.format("(local=%s, remote=%s)", local, remote);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			DHVersions that = (DHVersions) o;
			return local == that.local && remote == that.remote;
		}

		@Override
		public int hashCode() {
			return Objects.hash(local, remote);
		}
	}

	public static class ProcessedVersions {
		/** The effective offered version of the associated message. */
		public final int offeredVersion;
		/** The effective applied version of the associated message. */
		@NonNull public final Version appliedVersion;
		/** The resulting versions to be committed when the message has been processed. */
		@Nullable public final DHVersions pending4DHVersions;

		public ProcessedVersions(int offeredVersion, @NonNull Version appliedVersion, @Nullable DHVersions pending4DHVersions) {
			this.offeredVersion = offeredVersion;
			this.appliedVersion = appliedVersion;
			this.pending4DHVersions = pending4DHVersions;
		}
	}

	public static class UpdatedVersionsSnapshot {
		/** Versions before the update. */
		@NonNull public final DHVersions before;
		/** Versions after the update. */
		@NonNull public final DHVersions after;

		private UpdatedVersionsSnapshot(@NonNull DHVersions before, @NonNull DHVersions after) {
			this.before = before;
			this.after = after;
		}

		@Override
		public String toString() {
			return String.format("%s -> %s", before, after);
		}
	}

	/**
	 * Indicates that an encapsulated message cannot be processed. The FS session should be
	 * terminated and the associated message should be `Reject`ed.
	 */
	public static class RejectMessageError extends Exception {
		public RejectMessageError(@NonNull String reason) {
			super(reason);
		}
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
	 * ECDH private key used on this side of the session.
	 * Discarded as soon as an Accept packet has been received from the peer.
	 */
	@Nullable private byte[] myEphemeralPrivateKey;

	/**
	 * ECDH public key used on this side of the session.
	 */
	@NonNull private final byte[] myEphemeralPublicKey;

	/**
	 * Version used for local (outgoing) / remote (incoming) 4DH messages.
	 * `null` in case the 4DH message version has not been negotiated yet.
	 */
	@Nullable private DHVersions current4DHVersions;

	/**
	 * Timestamp of the last sent message in this session. This is used for the periodic empty
	 * message to ensure session freshness.
	 */
	private long lastOutgoingMessageTimestamp;

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

		this.myEphemeralPublicKey = completeKeyExchange(peerEphemeralPublicKey, contact, identityStoreInterface);
		this.current4DHVersions = DHVersions.negotiated(negotiatedVersion);
	}

	/**
	 * Create a DHSession with existing data, e.g. read from a persistent store. Note that an
	 * IllegalDHSessionStateException may be thrown. In this case we should terminate and delete the
	 * session and show a status message to the user.
	 */
	public DHSession(@NonNull DHSessionId id,
                     @NonNull String myIdentity,
                     @NonNull String peerIdentity,
                     @Nullable byte[] myEphemeralPrivateKey,
                     @NonNull byte[] myEphemeralPublicKey,
					 @Nullable DHVersions current4DHVersions,
					 long lastOutgoingMessageTimestamp,
                     @Nullable KDFRatchet myRatchet2DH,
                     @Nullable KDFRatchet myRatchet4DH,
                     @Nullable KDFRatchet peerRatchet2DH,
                     @Nullable KDFRatchet peerRatchet4DH) {
		this.id = id;
		this.myIdentity = myIdentity;
		this.peerIdentity = peerIdentity;
		this.myEphemeralPrivateKey = myEphemeralPrivateKey;
		this.myEphemeralPublicKey = myEphemeralPublicKey;
		this.current4DHVersions = current4DHVersions;
		this.lastOutgoingMessageTimestamp = lastOutgoingMessageTimestamp;
		setMyRatchet2DH(myRatchet2DH);
		setMyRatchet4DH(myRatchet4DH);
		setPeerRatchet2DH(peerRatchet2DH);
		setPeerRatchet4DH(peerRatchet4DH);

		// The database may restore 4DH versions when there are none because the DB migration adds
		// a `DEFAULT` clause. We need to override it to `null` in L20 and R20 state.
		// Note that this call may produce an IllegalDHSessionStateException. In this case we should
		// terminate and delete the session and show a status message to the user.
		State state = getState();
		if (state == State.L20 || state == State.R20) {
			this.current4DHVersions = null;
		}
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

	/**
	 * Warning: Only exported for storing the session, don't use it anywhere else!
	 */
	@Nullable
	public DHVersions getCurrent4DHVersions() {
		return current4DHVersions;
	}

	public long getLastOutgoingMessageTimestamp() {
		return lastOutgoingMessageTimestamp;
	}

	public void setLastOutgoingMessageTimestamp(long timestamp) {
		lastOutgoingMessageTimestamp = timestamp;
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
			throw new IllegalDHSessionStateException(String.format("Illegal DH session state:" +
					"myRatchet2DH=%s, myRatchet4DH=%s, peerRatchet2DH=%s, peerRatchet4DH=%s",
				myRatchet2DH != null,
				myRatchet4DH != null,
				peerRatchet2DH != null,
				peerRatchet4DH != null
			));
		}
	}

	/**
	 * The current negotiated major version plus the maximum supported minor version to offer in
	 * local/outgoing messages.
	 */
	@NonNull
	public Version getOutgoingOfferedVersion() {
		@NonNull State state = getState();
		switch (state) {
			case L20:
			case R20:
				// There should be no 4DH versions in this state
				if (current4DHVersions != null) {
					logger.error("getOutgoingOfferedVersion: Unexpected current4DHVersions in L20 state");
				}

				// TODO(ANDR-2452): We don't save the local/remote `Init` version range at the moment and simply
				// assume it to be 1.0 if not provided. This is a horrible hack and prevents us from
				// bumping the minimum version.
				return Version.V1_0;

			// R24, L44 or R44
			default:
				// We expect 4DH versions to be available in these states
				if (current4DHVersions == null) {
					logger.error("getOutgoingOfferedVersion: Missing current4DHVersions in state " + state);
					return Version.V1_0;
				}

				// Note: It does not matter whether we pick the local or the remote version to determine
				// the maximum supported minor version as both should always use the same major version.
				return getSupportedVersionWithin(current4DHVersions.local.getNumber());
		}
	}

	/**
	 * The current negotiated major and minor version to apply on local/outgoing messages.
	 */
	@NonNull
	public Version getOutgoingAppliedVersion() {
		@NonNull State state = getState();
		switch (state) {
			case L20:
			case R20:
				// There should be no 4DH versions in this state
				if (current4DHVersions != null) {
					logger.error("getOutgoingAppliedVersion: Unexpected current4DHVersions in L20 state");
				}

				// TODO(ANDR-2452): We don't save the local/remote `Init` version range at the moment and simply
				// assume it to be 1.0 if not provided. This is a horrible hack and prevents us from
				// bumping the minimum version.
				return Version.V1_0;

			// R24, L44 or R44
			default:
				// We expect 4DH versions to be available in these states
				if (current4DHVersions == null) {
					logger.error("getOutgoingAppliedVersion: Missing current4DHVersions in state " + state);
					return Version.V1_0;
				}
				return current4DHVersions.local;
		}
	}

	/**
	 * The current negotiated major and minor version that is expected to be the bottom line for
	 * remote/incoming messages.
	 *
	 * IMPORTANT: This is always the bottom line version for use in case a message without FS has
	 * been received. To validate an encapsulated message's versions, use
	 * processIncomingMessageVersion instead.
	 */
	@NonNull
	public Version getMinimumIncomingAppliedVersion() {
		@NonNull State state = getState();
		switch (state) {
			case L20:
			case R20:
				// There should be no 4DH versions in this state
				if (current4DHVersions != null) {
					logger.error("getMinimumIncomingAppliedVersion: Unexpected current4DHVersions in L20 state");
				}

				// TODO(ANDR-2452): We don't save the local/remote `Init` version range at the moment and simply
				// assume it to be 1.0 if not provided. This is a horrible hack and prevents us from
				// bumping the minimum version.
				return Version.V1_0;

			case R24:
				// Special case for this state where we can receive 2DH or 4DH messages, so the
				// bottom line is what has been offered in the remote's `Init`.

				// TODO(ANDR-2452): We don't save the remote `Init` version range at the moment and simply
				// assume it to be 1.0 if not provided. This is a horrible hack and prevents us from
				// bumping the minimum version.
				return Version.V1_0;

			// L44 or R44
			default:
				// We expect 4DH versions to be available in these states
				if (current4DHVersions == null) {
					logger.error("getMinimumIncomingAppliedVersion: Missing current4DHVersions in state " + state);
					return Version.V1_0;
				}
				return current4DHVersions.remote;
		}
	}

	/**
	 * Process the provided versions of an incoming message.
	 * Returns the processed versions to be committed once the message has been processed.
	 */
	@NonNull
	public ProcessedVersions processIncomingMessageVersion(ForwardSecurityDataMessage message) throws RejectMessageError {
		// Determine offered and applied version from the message
		int offeredVersion = message.getOfferedVersion();
		int rawAppliedVersion = message.getAppliedVersion();
		if (offeredVersion == Version.UNSPECIFIED_VALUE) {
			offeredVersion = Version.V1_0_VALUE;
		}
		if (rawAppliedVersion == Version.UNSPECIFIED_VALUE) {
			rawAppliedVersion = offeredVersion;
		}

		// TODO(ANDR-2452): Clamp hack. Clamping 2DH messages to 1.0 works around an issue where 2DH
		// messages would be claim to apply 1.1 from older beta versions. This is a horrible hack
		// and prevents us from bumping the minimum version.
		if (message.getType() == Encapsulated.DHType.TWODH) {
			offeredVersion = Version.V1_0_VALUE;
			rawAppliedVersion = Version.V1_0_VALUE;
		}

		// The applied version cannot be greater than offered version
		if (rawAppliedVersion > offeredVersion) {
			throw new RejectMessageError("Invalid FS versions in message: offered=" + offeredVersion + ", applied=" + rawAppliedVersion);
		}

		// Handle according to the DH type
		@NonNull State state = getState();
		@Nullable Version appliedVersion;
		@Nullable DHVersions pending4DHVersions;
		if (message.getType() == Encapsulated.DHType.TWODH) {
			// A 2DH message is only valid in R20 and R24 state
			if (state != State.R20 && state != State.R24) {
				throw new RejectMessageError("Unexpected 2DH message in state " + state);
			}

			// TODO(ANDR-2452): We don't save the remote `Init` version range at the moment and simply
			// assume it to be 1.0. This is a horrible hack and prevents us from bumping the minimum
			// version.
			@NonNull Version initVersionMin = Version.V1_0;

			// For 2DH messages, the versions must match exactly the minimum version that were
			// offered in the remote `Init`.
			if (offeredVersion != initVersionMin.getNumber()) {
				throw new RejectMessageError("Invalid offered FS version in 2DH message: offered=" + offeredVersion + ", init-version-min=" + initVersionMin);
			}
			if (rawAppliedVersion != initVersionMin.getNumber()) {
				throw new RejectMessageError("Invalid applied FS version in 2DH message: applied=" + rawAppliedVersion + ", init-version-min=" + initVersionMin);
			}

			// There are no versions to be committed
			appliedVersion = initVersionMin;
			pending4DHVersions = null;
		} else {
			// A 4DH message is only valid in R24, L44 or R44 state
			if (state != State.R24 && state != State.RL44) {
				throw new RejectMessageError("Unexpected 4DH message in state " + state);
			}
			if (current4DHVersions == null) {
				logger.error("Expected local/remote 4DH versions to exist, id={}, state={}", getId(), state);
				throw new RejectMessageError("Internal FS state mismatch");
			}

			// Major versions must match, the minor version must be ≥ the respective version
			if ((offeredVersion & 0xff00) != (current4DHVersions.local.getNumber() & 0xff00)
				|| (offeredVersion & 0x00ff) < (current4DHVersions.local.getNumber() & 0x00ff)) {
				throw new RejectMessageError("Invalid offered FS version in message: offered=" + offeredVersion + ", local-4dhv=" + current4DHVersions.local);
			}
			if ((rawAppliedVersion & 0xff00) != (current4DHVersions.remote.getNumber() & 0xff00)
				|| (rawAppliedVersion & 0x00ff) < (current4DHVersions.remote.getNumber() & 0x00ff)) {
				throw new RejectMessageError("Invalid applied FS version in message: applied=" + rawAppliedVersion + ", remote-4dhv=" + current4DHVersions.remote);
			}

			// The offered version is allowed to be greater than what we support, so calculate the
			// maximum commonly supported offered version.
			//
			// Note: There should be no gaps, so the resulting version should exist.
			int rawNewLocalVersion = Math.min(offeredVersion, getSupportedVersionWithin(offeredVersion).getNumber());
			@Nullable Version newLocalVersion = Version.forNumber(rawNewLocalVersion);
			if (newLocalVersion == null) {
				throw new RejectMessageError("Unknown maximum commonly supported offered FS version in message: offered=" + offeredVersion + ", supported=" + getSupportedVersionWithin(offeredVersion) + ", unsupported-common=" + rawNewLocalVersion);
			}

			// The applied version is not allowed to be greater than what we support (as it depends
			// on what we have offered in a previous message).
			appliedVersion = Version.forNumber(rawAppliedVersion);
			if (appliedVersion == null || rawAppliedVersion > getSupportedVersionWithin(rawAppliedVersion).getNumber()) {
				throw new RejectMessageError("Unsupported applied FS version in message: applied=" + rawAppliedVersion + ", supported=" + getSupportedVersionWithin(rawAppliedVersion));
			}

			// Determine versions to be committed as the new bottom line for incoming and outgoing
			// FS encapsulated messages.
			pending4DHVersions = DHVersions.updated(newLocalVersion, appliedVersion);
		}

		return new ProcessedVersions(offeredVersion, appliedVersion, pending4DHVersions);
	}

	/**
	 * Update the versions with the processed versions returned from `processIncomingMessageVersion`.
	 * Returns the updated versions snapshot containing before and after versions, if any have been updated.
	 */
	@Nullable
	public UpdatedVersionsSnapshot commitVersions(@NonNull ProcessedVersions processedVersions) {
		if (processedVersions.pending4DHVersions == null) {
			return null;
		}
		if (current4DHVersions == null) {
			logger.error("Expected local/remote 4DH versions to exist, id={}, state={}", getId(), getState());
			return null;
		}

		// Check if we need to update the versions
		boolean needsUpdate = false;
		if (processedVersions.pending4DHVersions.local != current4DHVersions.local) {
			logger.info("Updated local/outgoing message version ({} -> {}, id={})",
				current4DHVersions.local,
				processedVersions.pending4DHVersions.local,
				getId()
			);
			needsUpdate = true;
		}
		if (processedVersions.pending4DHVersions.remote != current4DHVersions.remote) {
			logger.info("Updated remote/incoming message version ({} -> {}, id={})",
				current4DHVersions.remote,
				processedVersions.pending4DHVersions.remote,
				getId()
			);
			needsUpdate = true;
		}

		// Update versions, if necessary
		if (needsUpdate) {
			@NonNull UpdatedVersionsSnapshot versionsSnapshot = new UpdatedVersionsSnapshot(
				this.current4DHVersions, processedVersions.pending4DHVersions);
			this.current4DHVersions = processedVersions.pending4DHVersions;
			return versionsSnapshot;
		} else {
			return null;
		}
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
		// myPrivateKey is not needed anymore at this point
		Arrays.fill(this.myEphemeralPrivateKey, (byte) 0);
		this.myEphemeralPrivateKey = null;
		this.current4DHVersions = DHVersions.negotiated(negotiatedVersion);

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

	/**
	 * Get a supported version within the provided major version range.
	 */
	@NonNull
	private static Version getSupportedVersionWithin(int majorVersion) {
		switch ((majorVersion & 0xff00)) {
			case Version.V1_0_VALUE:
				return Version.V1_2;
			default:
				throw new IllegalStateException("Unknown major version: " + majorVersion);
		}
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

		// Ensure the version range is supported
		if (remoteVersion.getMin() > localVersion.getMax() || localVersion.getMin() > remoteVersion.getMax()) {
			throw new BadMessageException("Unsupported minimum FS version: local-min=" + localVersion.getMin() + ", remote-min=" + remoteVersion.getMin());
		}

		// Take the minimum of the maximum supported versions
		final Version negotiatedVersion = Version.forNumber(Math.min(localVersion.getMax(), remoteVersion.getMax()));
		if (negotiatedVersion == null || negotiatedVersion == Version.UNSPECIFIED) {
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

	public static class IllegalDHSessionStateException extends RuntimeException {
		public IllegalDHSessionStateException(String msg) {
			super(msg);
		}
	}

	@Override
	public String toString() {
		return String.format(
			"(id=%s, 4dh-versions=%s)",
			getId(),
			current4DHVersions
		);
	}
}
