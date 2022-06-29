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
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import com.mapbox.mapboxsdk.constants.GeometryConstants;
import com.mapbox.mapboxsdk.geometry.LatLng;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.activities.MapActivity;
import ch.threema.app.services.MessageService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.LocationDataModel;

public class GeoLocationUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GeoLocationUtil");

	private static final String GEO_NUM = "-?\\d+(\\.\\d+)?";
	private static final Pattern GEO_PATTERN = Pattern.compile(
		"\\bgeo:-?" + GEO_NUM + "," + GEO_NUM + "(," + GEO_NUM + ")?"   // the geo keyword followed by 2 or 3 coordinates
			+ "(;[\\w\\-]+(=[\\[\\]:&+$\\w\\-.!~*'()%]+)?)*\\b"         // additional parameters, e.g., ';u=12;crs=Moon-2011'
	);

	private TextView targetView;

	private final static Map<String, String> addressCache = new HashMap<String, String>();

	public GeoLocationUtil(TextView targetView) {
		this.targetView = targetView;
	}

	@WorkerThread
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

	/**
	 * Get the geo URI pattern to detect geo URIs. This geo URI regex also matches invalid numbers
	 * or parameters, e.g.:
	 * <ul>
	 *     <li>invalid hexadecimal numbers as parameter values,</li>
	 *     <li>negative uncertainty values,</li>
	 *     <li>too large latitudes,</li>
	 *     <li>invalid altitudes, and</li>
	 *     <li>invalid additional parameters</li>
	 * </ul>
	 *
	 * To do checks on latitude and longitude use {@link #isValidGeoUri(Uri) isValidGeoUri}
	 *
	 * @return the geo URI pattern
	 */
	public static Pattern getGeoUriPattern() {
		return GEO_PATTERN;
	}

	/**
	 * Returns true if the given geo uri string is a valid geo uri. It checks that it is syntactically
	 * correct and also performs some checks on the latitude and longitude.
	 */
	public static boolean isValidGeoUri(@NonNull String geo) {
		return isValidGeoUri(Uri.parse(geo));
	}

	/**
	 * Returns true if the given geo uri is a valid geo uri. It checks that it is syntactically
	 * correct and also performs some checks on the latitude and longitude. Parameters like the
	 * uncertainty or altitude are ignored.
	 */
	public static boolean isValidGeoUri(@NonNull Uri uri) {
		return getLocationDataFromGeoUri(uri) != null;
	}

	/**
	 * Show the location data on a map.
	 *
	 * @return true if the device supports map libre and the location can be shown, false otherwise
	 */
	public static boolean viewLocation(@NonNull Context context, @NonNull LocationDataModel locationData) {
		if (ConfigUtils.hasNoMapLibreSupport()) {
			return false;
		}
		Intent intent = new Intent(context, MapActivity.class);
		IntentDataUtil.append(new LatLng(locationData.getLatitude(),
				locationData.getLongitude()),
			context.getString(R.string.app_name),
			locationData.getPoi(),
			locationData.getAddress(),
			intent);
		context.startActivity(intent);

		return true;
	}

	/**
	 * Show the location data on a map.
	 *
	 * @return true if the device supports map libre and the location can be shown, false otherwise
	 */
	public static boolean viewLocation(@NonNull Context context, @NonNull Uri uri) {
		LocationDataModel locationData = getLocationDataFromGeoUri(uri);
		if (locationData == null) {
			return false;
		}
		return viewLocation(context, locationData);
	}

	/**
	 * Get the location data from a geo uri.
	 *
	 * @param uri the uri where the necessary geo information is extracted
	 * @return the location data or null if the uri could not be parsed or contained invalid values
	 */
	@Nullable
	private static LocationDataModel getLocationDataFromGeoUri(@NonNull Uri uri) {
		String geoUri = uri.toString();
		if (!GEO_PATTERN.matcher(geoUri).matches()) {
			return null;
		}

		int separator = geoUri.indexOf(',');
		int longitudeEnd;
		for (longitudeEnd = separator + 1; longitudeEnd < geoUri.length(); longitudeEnd++) {
			if (geoUri.charAt(longitudeEnd) == ',' || geoUri.charAt(longitudeEnd) == ';') {
				break;
			}
		}

		double latitude;
		double longitude;

		try {
			latitude = Double.parseDouble(geoUri.substring(4, separator));
			longitude = Double.parseDouble(geoUri.substring(separator + 1, longitudeEnd));
		} catch (NumberFormatException nfe) {
			// Illegal number as latitude or longitude
			return null;
		}

		if (Math.abs(latitude) > GeometryConstants.MAX_LATITUDE) {
			// Too large absolute value of latitude
			return null;
		}

		if (longitude < GeometryConstants.MIN_LONGITUDE || longitude > GeometryConstants.MAX_LONGITUDE) {
			// Longitude is invalid (infinity)
			return null;
		}

		return new LocationDataModel(latitude, longitude, 0, "", "");
	}

}
