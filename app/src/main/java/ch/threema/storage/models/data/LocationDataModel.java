/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.storage.models.data;

import androidx.annotation.NonNull;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import org.slf4j.Logger;

import ch.threema.base.utils.LoggingUtil;

import java.io.StringReader;
import java.io.StringWriter;

public class LocationDataModel implements MessageDataInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LocationDataModel");

	private double latitude;
	private double longitude;
	private long accuracy;
	private String address;
	private String poi;

	private LocationDataModel() {
	}

	public LocationDataModel(double latitude, double longitude, long accuracy, String address, @NonNull String poi) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracy = accuracy;
		this.address = address;
		this.poi = poi;
	}

	public float getAccuracy() {
		return this.accuracy;
	}

	public double getLongitude() {
		return this.longitude;
	}

	public double getLatitude() {
		return this.latitude;
	}

	public String getAddress() {
		return this.address;
	}
	public void setAddress(String address) {
		this.address = address;
	}

	@NonNull
	public String getPoi() {
		return this.poi;
	}

	public void setPoi(String poi) {
		this.poi = poi;
	}

	public void fromString(String s) {
		if(s != null) {
			JsonReader r = new JsonReader(new StringReader(s));

			try {
				r.beginArray();
				this.latitude = r.nextDouble();
				this.longitude = r.nextDouble();
				this.accuracy = r.nextLong();
				if(r.hasNext()) {
					if (r.peek() != JsonToken.NULL) {
						this.address = r.nextString();
					}
					else {
						r.nextNull();
					}
					if (r.hasNext()) {
						this.poi = r.nextString();
					}
				}
			}
			catch (Exception x) {
//				logger.error("Exception", x);
				//DO NOTHING!!
			}
		}
	}

	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		JsonWriter j = new JsonWriter(sw);

		try {
			j.beginArray();
			j
				.value(this.getLatitude())
				.value(this.getLongitude())
				.value(this.getAccuracy())
				.value(this.getAddress())
				.value(this.getPoi());

			j.endArray();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			return null;
		}

		return sw.toString();
	}

	public static LocationDataModel create(String s) {
		LocationDataModel m = new LocationDataModel();
		m.fromString(s);
		return m;
	}
}
