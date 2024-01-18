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

package ch.threema.storage.models.data.status;

import android.util.JsonWriter;

import java.io.IOException;
import java.util.Date;

import androidx.annotation.Nullable;

public class VoipStatusDataModel implements StatusDataModel.StatusDataModelInterface {
	public static final int MISSED = 1;
	public static final int FINISHED = 2;
	public static final int REJECTED = 3;
	public static final int ABORTED = 4;
	public static final int TYPE = 1;

	public static final long NO_CALL_ID = 0L;

	private long callId;
	private int status;
	private Byte reason;
	private Integer duration;
	private Date date;

	protected VoipStatusDataModel() {
		//called by the parser
	}


	@Override
	public int getType() {
		return TYPE;
	}

	@Override
	public void readData(String key, long value) {
		switch (key) {
			case "callId":
				this.callId = value;
				break;
			case "status":
				this.status = (int) value;
				break;
			case "duration":
				this.duration = (int) value;
				break;
			case "reason":
				if (value <= 0Xff) {
					this.reason = (byte) value;
				}
				break;
		}
	}

	@Override
	public void readData(String key, boolean value) {
	}

	@Override
	public void readData(String key, String value) {

	}

	@Override
	public void writeData(JsonWriter j) throws IOException {
		j.name("status").value(this.status);
		if (this.callId != NO_CALL_ID) {
			j.name("callId").value(this.callId);
		}
		if (this.reason != null) {
			j.name("reason").value(this.reason);
		}
		if (this.duration != null) {
			j.name("duration").value(this.duration);
		}
	}

	@Override
	public void readDataNull(String key) {
		// TODO
	}

	public long getCallId() {
		return this.callId;
	}

	public int getStatus() {
		return this.status;
	}

	public Integer getDuration() {
		return this.duration;
	}

	public Byte getReason() {
		return this.reason;
	}

	/**
	 * This is used for hangup messages that indicate a missed call. If it is null, then the
	 * current time should be used.
	 *
	 * @return
	 */
	@Nullable
	public Date getDate() {
		return date;
	}

	public static VoipStatusDataModel createRejected(long callId, Byte reason) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.callId = callId;
		status.reason = reason;
		status.status = REJECTED;
		return status;
	}

	public static VoipStatusDataModel createFinished(long callId, int duration) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.callId = callId;
		status.duration = duration;
		status.status = FINISHED;
		return status;
	}

	public static VoipStatusDataModel createMissed(long callId, @Nullable Date date) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.callId = callId;
		status.status = MISSED;
		status.date = date;
		return status;
	}

	public static VoipStatusDataModel createAborted(long callId) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.callId = callId;
		status.status = ABORTED;
		return status;
	}
}
