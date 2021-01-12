/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.SaltyRTCBuilder;

import ch.threema.app.webclient.state.WebClientSessionState;

/**
 * There was an error during handling of the session.
 */
@WorkerThread
final class SessionStateError extends SessionState {
	SessionStateError(@NonNull SessionContext ctx) {
		super(WebClientSessionState.ERROR, ctx);
		logger.info("Initializing with no connection");
	}

	SessionStateError(@NonNull SessionContext ctx, @Nullable SessionConnectionContext cctx) {
		super(WebClientSessionState.ERROR, ctx);
		logger.info("Initializing with existing connection");

		// Tear down the existing connection
		logger.info("Cleanup");
		CleanupHelper.cleanupSessionConnectionContext(logger, cctx);
		CleanupHelper.cleanupSessionContext(logger, this.ctx);
	}

	@Override
	@NonNull SessionStateConnecting setConnecting(@NonNull final SaltyRTCBuilder builder, @Nullable final String affiliationId)
		throws InvalidStateTransition {
		logger.info("Connecting");
		return new SessionStateConnecting(this.ctx, builder, affiliationId);
	}

	@Override
	@NonNull SessionStateError setError(@NonNull final String reason) {
		logger.error("Error: {}", reason);
		return new SessionStateError(this.ctx);
	}
}
