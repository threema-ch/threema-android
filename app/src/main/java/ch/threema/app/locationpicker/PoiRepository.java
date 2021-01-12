/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.locationpicker;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

import com.mapbox.mapboxsdk.geometry.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.ProtocolStrings;

class PoiRepository {
	private static final Logger logger = LoggerFactory.getLogger(PoiRepository.class);
	public static final int QUERY_MIN_LENGTH = 3;

	private List<Poi> places = new ArrayList<>();
	private final MutableLiveData<List<Poi>> mutableLiveData = new MutableLiveData<>();
	private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

	@SuppressLint("StaticFieldLeak")
	MutableLiveData<List<Poi>> getMutableLiveData(PoiQuery poiQuery) {
		logger.debug("getMutableLiveData");

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected void onPreExecute() {
				isLoading.setValue(true);
			}

			@Override
			protected Void doInBackground(Void... voids) {
				places.clear();

				if (TestUtil.empty(poiQuery.getQuery()) || poiQuery.getCenter() == null ) {
					return null;
				}
				if (poiQuery.getCenter().getLatitude() == 0.0d &&  poiQuery.getCenter().getLongitude() == 0.0d) {
					return null;
				}
				if (poiQuery.getQuery().length() < QUERY_MIN_LENGTH) {
					return null;
				}

				final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
				if (serviceManager == null) {
					logger.error("Could not obtain service manager");
					return null;
				}
				final PreferenceService preferenceService = serviceManager.getPreferenceService();
				if (preferenceService == null) {
					logger.error("Could not obtain preference service");
					return null;
				}

				try {
					if (!"".equals(poiQuery.getQuery())) {
						final String placesUrl = LocationUtil.getPlacesUrl(preferenceService);
						URL serverUrl = new URL(String.format(
							Locale.US,
							placesUrl,
							poiQuery.getCenter().getLatitude(),
							poiQuery.getCenter().getLongitude(),
							Uri.encode(poiQuery.getQuery())
						));
						logger.debug("query: " + serverUrl.toString());
						HttpsURLConnection urlConnection = null;
						try {
							urlConnection = (HttpsURLConnection) serverUrl.openConnection();
							urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(serverUrl.getHost()));
							urlConnection.setConnectTimeout(15000);
							urlConnection.setReadTimeout(30000);
							urlConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT);
							urlConnection.setRequestMethod("GET");
							urlConnection.setDoOutput(false);

							int responseCode = urlConnection.getResponseCode();

							if (responseCode == HttpsURLConnection.HTTP_OK) {
								StringBuilder sb = new StringBuilder();
								try (BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
									String line;
									while ((line = br.readLine()) != null) {
										sb.append(line).append("\n");
									}
								}
								try {
									parseJson(sb.toString());
								} catch (JSONException e) {
									logger.error("Exception", e);
								}
							} else if (responseCode != 400 && responseCode != 504) {
								// Server returns a 400 if string is too short
								RuntimeUtil.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										Toast.makeText(ThreemaApplication.getAppContext(), "Server Error: " + responseCode, Toast.LENGTH_SHORT).show();
									}
								});
							} else {
								logger.info("Unable to fetch POI names: " + urlConnection.getResponseMessage());
							}
						} finally {
							if (urlConnection != null) {
								urlConnection.disconnect();
							}
						}
					}
				} catch (IOException e) {
					logger.error("Exception", e);
				}

				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				isLoading.setValue(false);
				mutableLiveData.setValue(places);
			}
		}.execute();

		return mutableLiveData;
	}

	MutableLiveData<Boolean> getIsLoading() {
		return isLoading;
	}

	private void parseJson(@NonNull String json) throws JSONException {
		JSONArray jsonArray = new JSONArray(json);
		if (jsonArray.length() > 0) {
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject result = jsonArray.getJSONObject(i);

				if (result != null) {
					Poi place = new Poi();
					double lat = result.getDouble("lat");
					double lng = result.getDouble("lon");
					place.setLatLng(new LatLng(lat, lng));
					place.setName(result.getString("name"));

					String placeS = result.optString("place");
					String highway = result.optString("highway");

					if (result.has("dist")) {
						place.setDistance(result.getInt("dist"));
					} else {
						place.setDistance(-1);
					}

					if (!TestUtil.empty(highway)) {
						place.setDescription("street");
					} else {
						place.setDescription(placeS);
					}

					places.add(place);
				}
			}
		}
	}
}
