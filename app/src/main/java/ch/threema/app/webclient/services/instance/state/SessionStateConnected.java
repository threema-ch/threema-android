/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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
import androidx.annotation.WorkerThread;

import org.saltyrtc.client.signaling.state.SignalingState;

import java.nio.ByteBuffer;

import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.converter.ConnectionDisconnect;
import ch.threema.app.webclient.converter.ConnectionInfo;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.exceptions.WouldBlockException;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.services.instance.DisconnectContext.DisconnectReason;
import ch.threema.app.webclient.state.WebClientSessionState;

/**
 * The session is connected.
 */
@WorkerThread
final class SessionStateConnected extends SessionState {
	@NonNull private final SessionConnectionContext cctx;

	SessionStateConnected(@NonNull final SessionContext ctx, @NonNull final SessionConnectionContext cctx) {
		super(WebClientSessionState.CONNECTED, ctx);
		this.cctx = cctx;
		logger.info("Initializing");
		this.sendConnectionInfo();
	}

	@Override
	void send(@NonNull final ByteBuffer message, @NonNull final SendMode mode) {
		if (this.cctx.dcc == null) {
			logger.error("Could not send message: Data channel not established");
			return;
		}

		// Send
		switch (mode) {
			case ASYNC:
				logger.info("Sending message through data channel (async)");
				this.cctx.dcc.sendAsync(message);
				break;
			case UNSAFE_SYNC:
				logger.info("Sending message through data channel (sync)");
				try {
					this.cctx.dcc.sendSyncUnsafe(message);
				} catch (WouldBlockException e) {
					logger.warn("Sending operation would block, discarding message!");
				}
				break;
		}
	}

	/**
	 * Send a connectionInfo update to the peer.
	 */
	private void sendConnectionInfo() {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(Protocol.FIELD_TYPE, Protocol.TYPE_UPDATE);
		builder.put(Protocol.FIELD_SUB_TYPE, Protocol.SUB_TYPE_CONNECTION_INFO);
		builder.put(Protocol.FIELD_DATA, ConnectionInfo.convert());
		logger.info("Sending update/connectionInfo to peer");
		this.send(builder.consume(), SendMode.ASYNC);
	}

	/**
	 * Send a connectionDisconnect update to the peer.
	 */
	private void sendConnectionDisconnect(@DisconnectReason int reason) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(Protocol.FIELD_TYPE, Protocol.TYPE_UPDATE);
		builder.put(Protocol.FIELD_SUB_TYPE, Protocol.SUB_TYPE_CONNECTION_DISCONNECT);
		try {
			builder.put(Protocol.FIELD_DATA, ConnectionDisconnect.convert(reason));
		} catch (ConversionException e) {
			logger.error("Error when converting disconnect reason", e);
			return;
		}
		logger.info("Sending update/connectionDisconnect to peer (reason: {})", reason);
		this.send(builder.consume(), SendMode.ASYNC);
	}

	@Override
	@NonNull SessionStateDisconnected setDisconnected(@NonNull final DisconnectContext reason) {
		logger.info("Disconnected (reason: {})", reason);

		// Send disconnect message to peer
		if (reason instanceof DisconnectContext.ByUs && this.cctx.salty.getSignalingState() == SignalingState.TASK) {
			final DisconnectContext.ByUs ctx = (DisconnectContext.ByUs) reason;
			this.sendConnectionDisconnect(ctx.getReason());
		}

		return new SessionStateDisconnected(this.ctx, this.cctx, reason);
	}

	@Override
	@NonNull SessionStateError setError(@NonNull final String reason) {
		logger.error("Error: {}", reason);
		return new SessionStateError(this.ctx, this.cctx);
	}
}
