/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import org.saltyrtc.client.SaltyRTCBuilder;
import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.client.exceptions.InvalidKeyException;

import java.security.NoSuchAlgorithmException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;

/**
 * The session is connecting.
 */
@WorkerThread
final class SessionStateConnecting extends SessionState {
	@NonNull private final SessionConnectionContext cctx;

	SessionStateConnecting(
		@NonNull final SessionContext ctx,
		@NonNull final SaltyRTCBuilder builder,
		@Nullable final String affiliationId
	)
		throws InvalidStateTransition {
		super(WebClientSessionState.CONNECTING, ctx);
		logger.info("Initializing");

		// Update affiliation id
		ctx.affiliationId = affiliationId;

		// Acquire resources
		logger.info("Acquire session resources...");
		this.ctx.acquireResources();

		// Create session connection context
		try {
			this.cctx = new SessionConnectionContext(ctx, builder);
		} catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException error) {
			logger.error("Cannot create session connection context:", error);
			throw new InvalidStateTransition(error.getMessage());
		}

		// Increment connection ID
		final int connectionId = ++this.ctx.connectionId;
		logger.info("Starting connection {} of session {}", connectionId, this.ctx.sessionId);

		// Connect to the SaltyRTC server asynchronously
		try {
			this.cctx.salty.connect();
		} catch (ConnectionException error) {
			logger.error("SaltyRTC connect failed", error);
			throw new InvalidStateTransition(error.getMessage());
		}

		// Create timer for the client-to-client connection
		this.ctx.handler.postDelayed(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				// Only error out when we're still in this state
				if (SessionStateConnecting.this.ctx.manager.getInternalState() == SessionStateConnecting.this) {
					SessionStateConnecting.this.ctx.manager.setError("Timeout while connecting to remote client");
				}
			}
		}, SessionConnectionContext.C2C_CONNECT_TIMEOUT_MS);
	}

	@Override
	@NonNull SessionStateConnected setConnected() {
		logger.info("Connected");
		return new SessionStateConnected(this.ctx, this.cctx);
	}

	@Override
	@NonNull SessionStateDisconnected setDisconnected(@NonNull final DisconnectContext reason) {
		logger.info("Disconnected (reason: {})", reason);
		return new SessionStateDisconnected(this.ctx, this.cctx, reason);
	}

	@Override
	@NonNull SessionStateError setError(@NonNull final String reason) {
		logger.error("Error: {}", reason);
		return new SessionStateError(this.ctx, this.cctx);
	}
}
