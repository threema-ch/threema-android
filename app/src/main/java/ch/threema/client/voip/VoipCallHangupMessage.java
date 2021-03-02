/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
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

package ch.threema.client.voip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

/**
 * This packet is sent to indicate that one of the call participants has ended the call.
 */
public class VoipCallHangupMessage extends VoipMessage {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallHangupMessage.class);

	private @Nullable VoipCallHangupData callHangupData;

	public VoipCallHangupMessage() {
		super();
	}

	public VoipCallHangupMessage setData(@NonNull VoipCallHangupData callHangupData) {
		this.callHangupData = callHangupData;
		return this;
	}

	public @Nullable VoipCallHangupData getData() {
		return this.callHangupData;
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_VOIP_CALL_HANGUP;
	}

	@Override
	@NonNull
	public byte[] getBody() throws ThreemaException  {
		try {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			this.callHangupData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error("Could not serialize VoipCallHangupMessage", e);
			throw new ThreemaException("Could not serialize VoipCallHangupMessage");
		}
	}
}
