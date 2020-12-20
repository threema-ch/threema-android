/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2020 Threema GmbH
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
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocationUtil;
import ch.threema.client.ProtocolStrings;

import static ch.threema.app.locationpicker.LocationPickerActivity.POI_RADIUS;

public class NearbyPoiUtil {
	private static final Logger logger = LoggerFactory.getLogger(NearbyPoiUtil.class);

	/**
	 * Fetch POIs around the specified location. Append them to the list `pois`.
	 */
	@WorkerThread
	public static void getPOIs(
		@NonNull LatLng center,
		@NonNull List<Poi> pois,
		int maxCount,
		@NonNull PreferenceService preferenceService
	) {
		long startTime = System.currentTimeMillis();

		try {
			final String poiUrl = LocationUtil.getPoiUrl(preferenceService);
			URL serverUrl = new URL(String.format(
				Locale.US,
				poiUrl,
				center.getLatitude(),
				center.getLongitude(),
				POI_RADIUS
			));

			if (center.getLatitude() == 0.0d && center.getLongitude() == 0.0d) {
				logger.debug("ignoring POI fetch request for 0/0");
				return;
			}

			logger.debug("getting POIs for " + serverUrl.toString());

			HttpsURLConnection urlConnection = null;
			try {
				urlConnection = (HttpsURLConnection) serverUrl.openConnection();
				urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(serverUrl.getHost()));
				urlConnection.setConnectTimeout(15000);
				urlConnection.setReadTimeout(30000);
				urlConnection.setRequestMethod("GET");
				urlConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT);
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

					logger.debug("*** retrieving POIs took " + (System.currentTimeMillis() - startTime) + " ms");
					try {
						parseJson(sb.toString(), pois, maxCount);
					} catch (JSONException e) {
						logger.error("Exception", e);
					}
				}
			} finally {
				if (urlConnection != null) {
					urlConnection.disconnect();
				}
			}
		} catch (IOException e) {
			logger.error("Exception", e);
		}
	}

	private static void parseJson(@NonNull String json, List<Poi> pois, int maxCount) throws JSONException {
		JSONArray results = new JSONArray(json);
		logger.debug("*** # of results: " + results.length());

		if (results.length() > 0) {
			for (int i = 0; i < results.length() && i < maxCount; i++) {
				JSONObject result = results.getJSONObject(i);
				if (result != null) {
					String name = result.optString("name", null);
					if (name != null) {
						if (!result.has("lat") || !result.has("lon")) {
							continue;
						}

						double lat = result.optDouble("lat");
						double lng = result.optDouble("lon");
						long id = result.optLong("id");

						Poi poi = new Poi();
						poi.setName(name);
						poi.setId(id);
						poi.setLatLng(new LatLng(lat, lng));

						// possible tags: amenity, shop, tourism, sport, leisure, natural, public_transport
						String type = result.optString("amenity", null);
						if (type == null) {
							type = result.optString("shop", null);
							if (type == null) {
								type = result.optString("tourism", null);
								if (type == null) {
									type = result.optString("sport", null);
									if (type == null) {
										type = result.optString("leisure", null);
										if (type == null) {
											type = result.optString("natural", null);
											if (type == null) {
												type = result.optString("public_transport", null);
												if (type == null) {
													type = result.optString("aeroway", null);
													if (type == null) {
														type = result.optString("aerialway", null);
														if (type == null) {
															type = result.optString("highway", null);
															if (type != null) {
																poi.setCategory("highway");
															}
														} else {
															type = "cablecar";
															poi.setCategory("cablecar");
														}
													} else {
														type = "airport";
														poi.setCategory("airport");
													}
												} else {
													poi.setCategory("public_transport");
												}
											} else {
												poi.setCategory("natural");
											}
										} else {
											poi.setCategory("leisure");
										}
									} else {
										poi.setCategory("sport");
									}
								} else {
									poi.setCategory("tourism");
								}
							} else {
								poi.setCategory("shop");
							}
						} else {
							poi.setCategory("amenity");
						}

						if (type == null) {
							continue;
						}

						switch (type) {
							case "fast_food":
							case "food_court":
								type = "food";
								break;
							case "hairdresser":
								type = "hair_care";
								break;
							case "fitness_centre":
								type = "gym";
								break;
							case "do_it_yourself":
							case "doityourself":
								type = "hardware_store";
								break;
							case "kindergarten":
								type = "child_care";
								break;
							case "nursing_home":
							case "clinic":
							case "social_facility":
								type = "hospital";
								break;
							case "parking_entrace":
								type = "parking";
								break;
							case "playground":
								type = "park";
								break;
							case "car_sharing":
								type = "car_rental";
								break;
							case "car":
								type = "car_dealer";
								break;
							case "hotel":
								type = "lodging";
								break;
							case "doctors":
								type = "doctor";
								break;
							case "recycling":
								type = "establishment";
								break;
							case "mall":
								type = "shopping_mall";
								break;
							case "swimming_pool":
							case "water_park":
								type = "swimming";
								break;
							case "arts_centre":
								type = "gallery";
								break;
							case "dry_cleaning":
								type = "laundry";
								break;
							case "second_hand":
								type = "furniture";
								break;
							case "boutique":
							case "clothes":
								type = "clothing_store";
								break;
							case "beauty":
							case "depilation":
								type = "beauty_salon";
								break;
							case "chemist":
								type = "health";
								break;
							case "townhall":
								type = "local_government_office";
								break;
							case "bicycle":
							case "bicycle_rental":
								type = "bicycle_store";
								break;
							case "cave_entrance":
								type = "park";
								break;
							case "peak":
							case "volcano":
								type = "mountain";
								break;
							case "station":
								type = "station";
								break;
							case "pub":
								type = "bar";
								break;
							case "stop_position":
								type = "bus_station";
								break;
							case "stationery":
							case "kiosk":
								type = "store";
								break;
							case "shoes":
								type = "shoe_store";
								break;
							case "aerialway":
								type = "cablecar";
								break;
							case "rest_area":
							case "services":
								type = "highway";
								break;
							case "cinema":
								type = "movie_theater";
								break;
							default:
								break;
						}

						poi.setType(type);

						pois.add(poi);
					}
				}
			}
		}
	}
}
