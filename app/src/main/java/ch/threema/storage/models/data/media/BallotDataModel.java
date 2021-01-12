/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.storage.models.data.media;

import android.util.JsonReader;
import android.util.JsonWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;

import ch.threema.app.utils.LogUtil;
import ch.threema.storage.models.data.MessageDataInterface;

public class BallotDataModel implements MessageDataInterface {
	private static final Logger logger = LoggerFactory.getLogger(BallotDataModel.class);

	public enum Type {
		BALLOT_CREATED(1), BALLOT_MODIFIED(2), BALLOT_CLOSED(3);
		private final int id;
		private Type(int id) {
			this.id = id;
		}
		public int getId() {
			return id;
		}
	};

	private Type type;
	private int ballotId;

	private BallotDataModel() {
	}

	public BallotDataModel(Type type, int ballotId) {
		this.type = type;
		this.ballotId = ballotId;
	}

	public Type getType() {
		return this.type;
	}

	public int getBallotId() {
		return this.ballotId;
	}

	public void fromString(String s) {
		JsonReader r = new JsonReader(new StringReader(s));

		try {
			r.beginArray();
			int typeId = r.nextInt();
			if(typeId == Type.BALLOT_CREATED.getId()) {
				this.type = Type.BALLOT_CREATED;
			}
			else if(typeId == Type.BALLOT_MODIFIED.getId()) {
				this.type = Type.BALLOT_MODIFIED;
			}
			else if(typeId == Type.BALLOT_CLOSED.getId()) {
				this.type = Type.BALLOT_CLOSED;
			}
			this.ballotId = r.nextInt();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			//DO NOTHING!!
		}

	}

	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		JsonWriter j = new JsonWriter(sw);

		try {
			j.beginArray();
			j
					.value(this.type.getId())
					.value(this.ballotId);
			j.endArray();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			return null;
		}

		return sw.toString();

	}

	public static BallotDataModel create(String s) {
		BallotDataModel m = new BallotDataModel();
		m.fromString(s);
		return m;
	}
}
