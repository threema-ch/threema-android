/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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
import ch.threema.client.BadMessageException;
import ch.threema.client.JSONUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VoipCallRingingData extends VoipCallData<VoipCallRingingData> {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallRingingData.class);

	//region Serialization

	public static @NonNull VoipCallRingingData parse(@NonNull String jsonObjectString) throws BadMessageException {
		final JSONObject o;
		if (jsonObjectString.trim().isEmpty()) {
			// Historically, ringing messages may be empty
			o = new JSONObject();
		} else {
			try {
				o = new JSONObject(jsonObjectString);
			} catch (JSONException e) {
				logger.error("Bad VoipCallRingingData: Invalid JSON string", e);
				throw new BadMessageException("TM064", true);
			}
		}

		final VoipCallRingingData callRingingData = new VoipCallRingingData();

		try {
			final Long callId = JSONUtil.getLongOrThrow(o, KEY_CALL_ID);
			if (callId != null) {
				callRingingData.setCallId(callId);
			}
		} catch (Exception e) {
			logger.error("Bad VoipCallRingingData: Invalid Call ID", e);
			throw new BadMessageException("TM064", true);
		}

		return callRingingData;
	}

	public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(UTF_8));
	}

	private @NonNull String generateString() throws Exception {
		final JSONObject o = this.buildJsonObject();
		return o.toString();
	}

	//endregion
}
