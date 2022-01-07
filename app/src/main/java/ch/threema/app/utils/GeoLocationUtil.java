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

package ch.threema.app.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.LocationDataModel;

public class GeoLocationUtil {
	private static final Logger logger = LoggerFactory.getLogger(GeoLocationUtil.class);

	private TextView targetView;

	private final static Map<String, String> addressCache = new HashMap<String, String>();

	public GeoLocationUtil(TextView targetView) {
		this.targetView = targetView;
	}

	public static String getAddressFromLocation(Context context, double latitude, double longitude) throws IOException {
		String addressString = context.getString(R.string.unknown_address);
		String key = String.valueOf(latitude) + '|' + String.valueOf(longitude);

		if (Geocoder.isPresent()) {
			synchronized (addressCache) {
				if (addressCache.containsKey(key)) {
					addressString = addressCache.get(key);
				} else {
					List<Address> addresses = null;
					try {
						Geocoder geocoder = new Geocoder(context, Locale.getDefault());
						addresses = geocoder.getFromLocation(latitude, longitude, 1);
					} catch (Exception e) {
						logger.error("Exception", e);
					}
					if (addresses != null && addresses.size() > 0) {
						Address address = addresses.get(0);
						if (address != null) {
							addressString = StringConversionUtil.join(
								", ",
								address.getAddressLine(0),
								address.getLocality());
							addressCache.put(key, addressString);
						}
					}
				}
			}
		}
		return addressString;
	}

	public void updateAddressAndModel(Context context, Location location) {
		if (location == null) return;

		GeocoderHandler handler = new GeocoderHandler();
		getAddress(context, location.getLatitude(), location.getLongitude(), handler);
	}

	public void getAddress(final Context context, final double latitude, final double longitude, final Handler handler) {

		new Thread(new Runnable() {
			@Override
			public void run() {
				String addressText = null;

				try {
					addressText = GeoLocationUtil.getAddressFromLocation(context, latitude, longitude);
				} catch (IOException e) {
					logger.error("Exception", e);
				} finally {
					Message msg = Message.obtain();
					msg.setTarget(handler);
					if (addressText != null) {
						msg.what = 1;
						Bundle bundle = new Bundle();
						bundle.putString("address", addressText);
						msg.setData(bundle);
					} else
						msg.what = 0;
					msg.sendToTarget();
				}

			}
		}).start();
	}

	public class GeocoderHandler extends Handler {
		private MessageService messageService;
		private AbstractMessageModel messageModel;

		public GeocoderHandler() {
		}
		public GeocoderHandler(MessageService messageService, AbstractMessageModel messageModel) {
			this.messageService = messageService;
			this.messageModel = messageModel;
		}
		@Override
		public void handleMessage(Message message) {
			String result;
			switch (message.what) {
				case 1:
					Bundle bundle = message.getData();
					result = bundle.getString("address");
					targetView.setText(result);

					if(TestUtil.required(this.messageModel, this.messageService)) {
						LocationDataModel l = messageModel.getLocationData();
						if(l != null) {
							l.setAddress(result);
							this.messageService.save(messageModel);
						}
					}
					break;
				default:
					result = null;
			}
		}
	}

	public static Uri getLocationUri(AbstractMessageModel model) {
		double latitude = model.getLocationData().getLatitude();
		double longitude = model.getLocationData().getLongitude();
		String locationName = model.getLocationData().getPoi();
		String address = model.getLocationData().getAddress();

		return getLocationUri(latitude, longitude, locationName, address);
	}

	public static Uri getLocationUri(double latitude, double longitude, String locationName, String address) {
		String geoString = "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude;

		if (TestUtil.empty(locationName)) {
			locationName = address;
		}

		if (!TestUtil.empty(locationName)) {
			try {
				locationName = URLEncoder.encode(locationName, "utf-8");
				return Uri.parse(geoString + "(" + locationName + ")");
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
		return Uri.parse(geoString);
	}
}
