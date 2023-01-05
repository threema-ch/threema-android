/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;

@AnyThread
public class VoipStatus {
	private final static String OUTGOING = "outgoing";
	private final static String DURATION = "duration";
	private final static String REASON = "reason";
	private final static String PEER_IDENTITY = "identity";

	public static MsgpackObjectBuilder convertOnRinging(String peerIdentity) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		return builder;
	}

	public static MsgpackObjectBuilder convertOnStarted(String peerIdentity, boolean outgoing) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		builder.put(OUTGOING, outgoing);
		return builder;
	}

	public static MsgpackObjectBuilder convertOnFinished(String peerIdentity, boolean outgoing, int durationSeconds) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		builder.put(OUTGOING, outgoing);
		builder.put(DURATION, durationSeconds);
		return builder;
	}

	public static MsgpackObjectBuilder convertOnRejected(String peerIdentity, boolean outgoing, byte reason) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		builder.put(OUTGOING, outgoing);
		builder.put(REASON, VoipStatus.convertRejectReason(reason));
		return builder;
	}

	private static String convertRejectReason(byte reason) {
		switch (reason) {
			case VoipCallAnswerData.RejectReason.BUSY:
				// The called party is busy (another call is active)
				return "busy";
			case VoipCallAnswerData.RejectReason.TIMEOUT:
				// The called party did not accept the call during a certain time
				return "timeout";
			case VoipCallAnswerData.RejectReason.REJECTED:
				// The called party explicitly rejected the call
				return "rejected";
			case VoipCallAnswerData.RejectReason.DISABLED:
				// The called party disabled calls or did not grant microphone permissions
				return "disabled";
			case VoipCallAnswerData.RejectReason.UNKNOWN:
			default:
				return "unknown";
		}
	}

	public static MsgpackObjectBuilder convertOnMissed(String peerIdentity) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		return builder;
	}

	public static MsgpackObjectBuilder convertOnAborted(String peerIdentity) {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(PEER_IDENTITY, peerIdentity);
		return builder;
	}
}
