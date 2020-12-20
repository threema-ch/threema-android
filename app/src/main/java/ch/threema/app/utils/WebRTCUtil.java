/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;

import androidx.annotation.NonNull;
import ch.threema.logging.WebRTCLoggable;

/**
 * This util handles WebRTC initialization.
 */
public class WebRTCUtil {
	private static final Logger logger = LoggerFactory.getLogger(WebRTCUtil.class);

	private static boolean initialized = false;

	/**
	 * If the WebRTC Android globals haven't been initialized yet, initialize them.
	 *
	 * @param appContext The Android context to use. Make sure to use the application context!
	 */
	public static void initializeAndroidGlobals(final Context appContext) {
		if (!initialized) {
			logger.debug("Initializing Android globals");

			// Enable this to allow WebRTC trace logging. Note: Since this logs a lot of data,
			// it should only be enabled temporarily.
			final boolean enableVerboseInternalTracing = false;

			// Initialize peer connection factory
			PeerConnectionFactory.initialize(
				PeerConnectionFactory.InitializationOptions.builder(appContext)
					.setEnableInternalTracer(enableVerboseInternalTracing)
					.setInjectableLogger(new WebRTCLoggable(), Logging.Severity.LS_INFO)
					.createInitializationOptions()
			);

			initialized = true;
		} else {
			logger.debug("Android globals already initialized");
		}
	}

	/**
	 * Convert an ICE candidate to a nice string representation.
	 * @param candidate The ICE candidate
	 */
	@NonNull
	public static String iceCandidateToString(@NonNull IceCandidate candidate) {
		final IceCandidateParser.CandidateData parsed = IceCandidateParser.parse(candidate.sdp);
		if (parsed != null) {
			final StringBuilder builder = new StringBuilder();
			builder
				.append("[")
				.append(parsed.candType)
				.append("] ")
				.append(parsed.transport);
			if (parsed.tcptype != null) {
				builder.append("/").append(parsed.tcptype);
			}
			builder
				.append(" ")
				.append(parsed.connectionAddress)
				.append(":")
				.append(parsed.port);
			if (parsed.relAddr != null && parsed.relPort != null) {
				builder.append(" via ").append(parsed.relAddr).append(":").append(parsed.relPort);
			}
			return builder.toString();
		} else {
			return candidate.sdp;
		}
	}
}
