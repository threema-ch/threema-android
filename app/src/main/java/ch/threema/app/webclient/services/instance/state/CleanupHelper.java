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

package ch.threema.app.webclient.services.instance.state;

import org.saltyrtc.client.signaling.state.SignalingState;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Helper to clean up data related to a webclient session.
 */
@WorkerThread
class CleanupHelper {
	/**
	 * Dispose the session context.
	 */
	static void cleanupSessionContext(@NonNull final Logger logger, @NonNull final SessionContext ctx) {
		logger.debug("CleanupHelper: Release session resources...");
		ctx.releaseResources();
	}

	/**
	 * Dispose the session connection context and discard any further events.
	 */
	static void cleanupSessionConnectionContext(
		@NonNull final Logger logger,
		@Nullable final SessionConnectionContext cctx
	) {
		if (cctx == null) {
			return;
		}

		// Mark the session connection closed to ignore all further events
		cctx.closed.set(true);

		// Tear down both the peer-to-peer connection and the SaltyRTC connection
		// Warning: Keep it in this order, otherwise, you'll see deadlocks!
		CleanupHelper.cleanupSaltyRTC(logger, cctx);
		CleanupHelper.cleanupPeerConnection(logger, cctx);
	}

	private static void cleanupPeerConnection(
		@NonNull final Logger logger,
		@NonNull final SessionConnectionContext cctx
	) {
		if (cctx.pc == null) {
			return;
		}

		// Dispose peer connection
		// Note: This will eventually dispose the data channel once the close event fires on the channel.
		logger.debug("CleanupHelper: Disposing peer connection wrapper");
		cctx.pc.dispose();
		cctx.pc = null;
	}

	/**
	 * Clear all SaltyRTC event listeners.
	 */
	private static void cleanupSaltyRTC(@NonNull final Logger logger, @NonNull final SessionConnectionContext cctx) {
		// Clear all SaltyRTC event listeners
		cctx.salty.events.clearAll();

		// Make sure that SaltyRTC is disconnected
		if (cctx.salty.getSignalingState() != SignalingState.CLOSED &&
			cctx.salty.getSignalingState() != SignalingState.CLOSING) {
			logger.debug("CleanupHelper: Disconnecting SaltyRTC (signaling state was {})", cctx.salty.getSignalingState());
			cctx.salty.disconnect();
		}
	}
}
