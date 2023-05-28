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

package ch.threema.storage.models.data.status;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.data.MessageDataInterface;

public abstract class StatusDataModel {
	private static final Logger logger = LoggingUtil.getThreemaLogger("StatusDataModel");

	public interface StatusDataModelInterface extends MessageDataInterface {
		int getType();
		void readData(String key, String value);
		void readData(String key, long value);
		void readData(String key, boolean value);
		void readDataNull(String key);
		void writeData(JsonWriter j) throws IOException;
	}

	/**
	 * convert a json string to a data model
	 */
	@Nullable
	public static StatusDataModelInterface convert(String s) {
		StatusDataModelInterface data = null;
		if (s != null) {
			JsonReader r = new JsonReader(new StringReader(s));

			try {
				r.beginArray();
				int type = r.nextInt();

				switch (type) {
					case VoipStatusDataModel.TYPE:
						data = new VoipStatusDataModel();
						break;
					case GroupCallStatusDataModel.TYPE:
						data = new GroupCallStatusDataModel();
						break;
					case ForwardSecurityStatusDataModel.TYPE:
						data = new ForwardSecurityStatusDataModel();
						break;
				}

				if (data != null) {
					r.beginObject();
					while (r.hasNext()) {
						String key = r.nextName();
						if (r.peek() == JsonToken.NULL) {
							r.skipValue();
							data.readDataNull(key);
						} else if (r.peek() == JsonToken.STRING) {
							data.readData(key, r.nextString());
						} else if (r.peek() == JsonToken.NUMBER) {
							data.readData(key, r.nextLong());
						} else if (r.peek() == JsonToken.BOOLEAN) {
							data.readData(key, r.nextBoolean());
						}
					}
					r.endObject();
				}
			}
			catch (Exception x) {
				logger.error("Exception", x);
			}
		}
		return data;
	}

	/**
	 * Convert a datamodel to a json string
	 */
	public static String convert(StatusDataModelInterface data) {
		StringWriter sw = new StringWriter();
		JsonWriter j = new JsonWriter(sw);

		try {
			j.beginArray();
			if (data != null) {
				j.value(data.getType());
				j.beginObject();
				data.writeData(j);
				j.endObject();
			}
			j.endArray();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			return null;
		}

		return sw.toString();
	}
}
