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
 * This packet is sent by the receiver of a call.
 * It indicates towards the caller that the phone is ringing.
 */
public class VoipCallRingingMessage extends VoipMessage {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallRingingMessage.class);

	private @Nullable VoipCallRingingData callRingingData;

	public VoipCallRingingMessage() {
		super();
	}

	public VoipCallRingingMessage setData(@NonNull VoipCallRingingData callRingingData) {
		this.callRingingData = callRingingData;
		return this;
	}

	public @Nullable VoipCallRingingData getData() {
		return this.callRingingData;
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_VOIP_CALL_RINGING;
	}

	@Override
	@NonNull
	public byte[] getBody() throws ThreemaException {
		try {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			this.callRingingData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error("Could not serialize VoipCallRingingMessage", e);
			throw new ThreemaException("Could not serialize VoipCallRingingMessage");
		}
	}

}
