/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.app.voip.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData;

public class SdpUtil {
	private static final Pattern SDP_LOOPBACK_RE =
		Pattern.compile(" (127\\.0\\.0\\.1|::1) \\d+ typ host");
	private static final Pattern SDP_IPv6_RE =
		Pattern.compile("candidate:.*:.*");
	private static final Pattern SDP_RELAY_CANDIDATE_RE =
		Pattern.compile("candidate:" +
			"([^ ]+) " + // foundation
			"([^ ]+) " + // componentid
			"([^ ]+) " + // transport
			"([^ ]+) " + // priority
			"([^ ]+) " + // ip
			"([^ ]+) " + // port
			"typ relay " +
			"raddr ([^ ]+)"); // relatedAddress

	/**
	 * Return the appropriate SessionDescription.Type for the specified type string.
	 *
	 * If the type is unknown, null is returned.
	 */
	@Nullable
	public static SessionDescription.Type getSdpType(@Nullable String type) {
		if (type == null) {
			return null;
		}
		switch (type) {
			case "offer":
				return SessionDescription.Type.OFFER;
			case "answer":
				return SessionDescription.Type.ANSWER;
			case "pranswer":
				return SessionDescription.Type.PRANSWER;
			default:
				return null;
		}
	}

	/**
	 * Create a `SessionDescription` from an `OfferData` instance.
	 */
	@Nullable
	public static SessionDescription getOfferSessionDescription(@NonNull VoipCallOfferData.OfferData data) {
		final SessionDescription.Type type = getSdpType(data.getSdpType());
		if (type == null) {
			return null;
		}
		return new SessionDescription(type, data.getSdp());
	}

	/**
	 * Create a `SessionDescription` from an `AnswerData` instance.
	 */
	@Nullable
	public static SessionDescription getAnswerSessionDescription(@NonNull VoipCallAnswerData.AnswerData data) {
		final SessionDescription.Type type = getSdpType(data.getSdpType());
		if (type == null) {
			return null;
		}
		return new SessionDescription(type, data.getSdp());
	}

	@NonNull
	public static IceCandidate[] getIceCandidates(@NonNull VoipICECandidatesData.Candidate[] candidates) {
		final List<IceCandidate> iceCandidateList = new LinkedList<>();
		for (VoipICECandidatesData.Candidate candidate : candidates) {
			if (candidate != null) {
				iceCandidateList.add(new IceCandidate(
						candidate.getSdpMid(),
						candidate.getSdpMLineIndex(),
						candidate.getCandidate()
				));
			}
		}
		return iceCandidateList.toArray(new IceCandidate[iceCandidateList.size()]);
	}

	/**
	 * Parse an SDP description and return whether this is a loopback candidate (e.g. 127.0.0.1 on
	 * IPv4 or ::1 on IPv6).
	 */
	public static boolean isLoopbackCandidate(String sdpDescription) {
		return SDP_LOOPBACK_RE.matcher(sdpDescription).find();
	}

	/**
	 * Parse an SDP description and return whether this is an IPv6 candidate.
	 */
	public static boolean isIpv6Candidate(String sdpDescription) {
		return SDP_IPv6_RE.matcher(sdpDescription).find();
	}

	/**
	 * Parse an SDP description and return the related address.
	 */
	@Nullable public static String getRelatedAddress(@NonNull final String sdpDescription) {
		final Matcher matcher = SDP_RELAY_CANDIDATE_RE.matcher(sdpDescription);
		if (matcher.find()) {
			// Return the related address
			return matcher.group(7);
		}
		return null;
	}
}
