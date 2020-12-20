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

package ch.threema.storage.models.data.status;

import android.util.JsonWriter;

import java.io.IOException;

public class VoipStatusDataModel implements StatusDataModel.StatusDataModelInterface {
	public static final int MISSED = 1;
	public static final int FINISHED = 2;
	public static final int REJECTED = 3;
	public static final int ABORTED = 4;
	public static final int TYPE = 1;

	private int status;
	private Byte reason;
	private Integer duration;

	protected VoipStatusDataModel(){
		//called by the parser
	}


	@Override
	public int getType() {
		return TYPE;
	}

	@Override
	public void readData(String key, int value) {
		switch (key) {
			case "status":
				this.status = value;
				break;
			case "duration":
				this.duration = value;
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

	public int getStatus() {
		return this.status;
	}

	public Integer getDuration() {
		return this.duration;
	}

	public Byte getReason() {
		return this.reason;
	}

	public static VoipStatusDataModel createRejected(Byte reason) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.reason = reason;
		status.status = REJECTED;
		return status;
	}

	public static VoipStatusDataModel createFinished(int duration) {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.duration = duration;
		status.status = FINISHED;
		return status;
	}

	public static VoipStatusDataModel createMissed() {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.status = MISSED;
		return status;
	}

	public static VoipStatusDataModel createAborted() {
		VoipStatusDataModel status = (new VoipStatusDataModel());
		status.status = ABORTED;
		return status;
	}
}
