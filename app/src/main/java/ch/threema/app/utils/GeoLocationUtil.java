/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import org.maplibre.android.constants.GeometryConstants;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.location.MapActivity;
import ch.threema.app.services.MessageService;
import ch.threema.domain.protocol.csp.messages.location.Poi;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.LocationDataModel;

import static ch.threema.app.location.LocationExtensionsKt.toCoordinates;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class GeoLocationUtil {
    private static final Logger logger = getThreemaLogger("GeoLocationUtil");

    private static final String GEO_NUM = "-?\\d+(\\.\\d+)?";
    private static final String GEO_PARAMS = "(;[\\w\\-]+(=[\\[\\]:&+$\\w\\-.!~*'()%]+)?)*";
    private static final String GEO_LABEL = "(\\([\\w+%\\-&]*\\))?";
    private static final String GEO_QUERY = "\\?q=" + GEO_NUM + "," + GEO_NUM;
    private static final String GEO_ZOOM = "\\?z=\\d+";
    private static final String GEO_ANDROID = "(" + GEO_QUERY + GEO_LABEL + "|" + GEO_ZOOM + ")?";
    private static final Pattern GEO_PATTERN = Pattern.compile(
        "\\bgeo:-?" + GEO_NUM + "," + GEO_NUM + "(," + GEO_NUM + ")?("      // the geo keyword followed by 2 or 3 coordinates
            + GEO_PARAMS + "|"                                              // additional parameters, e.g., ';u=12;crs=Moon-2011', or
            + GEO_ANDROID + ")?(?![\\?;])(\\b|(?<=\\)))"                    // support Android query geo extension (only for coordinates)
    );

    private TextView targetView;

    private final static Map<String, String> addressCache = new HashMap<String, String>();

    public GeoLocationUtil(TextView targetView) {
        this.targetView = targetView;
    }

    public static void deleteAddressCache() {
        addressCache.clear();
    }

    /**
     * @return The looked up address, or a default value of "Unknown address" if
     * the address could not be determined by the given coordinates.
     */
    @WorkerThread
    @NonNull
    public static String getAddressFromLocation(@NonNull Context context, double latitude, double longitude) throws IOException {
        @Nullable String addressString = null;
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
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        if (address != null) {
                            addressString = addressToString(address);
                            addressCache.put(key, addressString);
                        }
                    }
                }
            }
        }
        return Objects.requireNonNullElseGet(
            addressString,
            () -> context.getString(R.string.unknown_address)
        );
    }

    private static String addressToString(@NonNull Address address) {
        var addressLine = address.getAddressLine(0);
        var addressLocality = address.getLocality();

        var stringBuilder = new StringBuilder();
        if (addressLine != null && !addressLine.isEmpty()) {
            stringBuilder.append(addressLine);
        }
        if (addressLocality != null && !addressLocality.isEmpty()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(addressLocality);
        }
        return stringBuilder.toString();
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
        public void handleMessage(@NonNull Message message) {
            if (message.what != 1) {
                return;
            }

            final @NonNull Bundle bundle = message.getData();
            final @Nullable String address = bundle.getString("address");
            targetView.setText(address);

            if (TestUtil.required(this.messageModel, this.messageService)) {

                // Apply the passed ´address´ value to the existing message model
                LocationDataModel locationDataModel = messageModel.getLocationData();
                final @Nullable Poi updatedPoi;
                if (address != null && locationDataModel.poiNameOrNull != null) {
                    updatedPoi = new Poi.Named(locationDataModel.poiNameOrNull, address);
                } else if (address != null) {
                    updatedPoi = new Poi.Unnamed(address);
                } else {
                    updatedPoi = null;
                }

                messageModel.setLocationData(
                    new LocationDataModel(
                        locationDataModel.latitude,
                        locationDataModel.longitude,
                        locationDataModel.accuracy,
                        updatedPoi
                    )
                );
                this.messageService.save(messageModel);
            }
        }
    }

    public static Uri getLocationUri(@NonNull AbstractMessageModel model) {
        final double latitude = model.getLocationData().latitude;
        final double longitude = model.getLocationData().longitude;
        final @Nullable String poiName = model.getLocationData().poiNameOrNull;
        final @Nullable String poiAddress = model.getLocationData().poiAddressOrNull;

        return getLocationUri(latitude, longitude, poiName, poiAddress);
    }

    public static Uri getLocationUri(double latitude, double longitude, final @Nullable String poiName, final @Nullable String poiAddress) {
        String geoString = "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude;

        @Nullable String locationName = poiName;

        if (TestUtil.isEmptyOrNull(locationName)) {
            locationName = poiAddress;
        }

        if (!TestUtil.isEmptyOrNull(locationName)) {
            try {
                locationName = URLEncoder.encode(locationName, "utf-8");
                return Uri.parse(geoString + "(" + locationName + ")");
            } catch (Exception exception) {
                logger.error("Exception", exception);
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
     * <p>
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
    public static boolean isValidGeoUri(@Nullable Uri uri) {
        return uri != null && getLocationDataFromGeoUri(uri) != null;
    }

    /**
     * Show the location data on a map.
     *
     * @return true if the device supports map libre and the location can be shown, false otherwise
     */
    public static boolean viewLocation(@NonNull Context context, @NonNull LocationDataModel locationData) {
        // Note that this check does only work for some devices. Other devices may report version
        // 3.0 even if they do not support it. This may lead to a crash when displaying a location.
        if (ConfigUtils.getSupportedGlEsVersion(context) < 3.0) {
            // Note that we do not abort here if the supported version could not be determined. The
            // risk of showing the location without support for gl es 3.0 is low, as this does not
            // necessarily lead to a crash. The potential damage would be larger if we would prevent
            // supported devices from displaying locations.
            return false;
        }

        Intent intent = new Intent(context, MapActivity.class);
        IntentDataUtil.append(
            toCoordinates(locationData),
            context.getString(R.string.app_name),
            locationData.poiNameOrNull,
            locationData.poiAddressOrNull,
            intent
        );
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
    public static LocationDataModel getLocationDataFromGeoUri(@NonNull Uri uri) {
        String geoUri = uri.toString();
        if (!GEO_PATTERN.matcher(geoUri).matches()) {
            return null;
        }

        int latitudeStart;
        int longitudeEnd;
        int separator;

        latitudeStart = geoUri.indexOf("?q=");
        if (latitudeStart == -1) {
            // There is no query attribute therefore we take the lat/long data at the beginning
            latitudeStart = 4;
        } else {
            // There is a query attribute so that latitude value starts 3 characters later than '?q='
            latitudeStart += 3;
        }
        // Find position of separator
        separator = latitudeStart + 1;
        while (geoUri.charAt(separator) != ',') {
            separator++;
        }
        // Find position where longitude ends (either '(' because a label follows, ',' because an
        // altitude follows, ';' because an RFC5870 parameter follows or '?' because an android geo
        // uri can contain a zoom level starting with "?z=")
        longitudeEnd = separator + 1;
        while (longitudeEnd < geoUri.length() &&
            geoUri.charAt(longitudeEnd) != '(' &&
            geoUri.charAt(longitudeEnd) != ',' &&
            geoUri.charAt(longitudeEnd) != ';' &&
            geoUri.charAt(longitudeEnd) != '?'
        ) {
            longitudeEnd++;
        }

        double latitude;
        double longitude;

        try {
            latitude = Double.parseDouble(geoUri.substring(latitudeStart, separator));
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

        return new LocationDataModel(latitude, longitude, 0.0, null);
    }

    /**
     * Get the location from a geo uri.
     *
     * @param uri the uri where the necessary geo information is extracted
     * @return the location data or null if the uri could not be parsed or contained invalid values
     */
    @Nullable
    public static Location getLocationFromUri(@NonNull Uri uri) {
        LocationDataModel locationData = getLocationDataFromGeoUri(uri);
        if (locationData == null) {
            return null;
        }

        Location location = new Location("");
        location.setLatitude(locationData.latitude);
        location.setLongitude(locationData.longitude);
        location.setAccuracy((float) locationData.accuracyOrFallback);
        return location;
    }

}
