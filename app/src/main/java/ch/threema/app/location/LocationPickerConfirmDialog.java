/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.location;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;

import org.slf4j.Logger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;


public class LocationPickerConfirmDialog extends ThreemaDialogFragment {
    private LocationConfirmDialogClickListener callback;

    private static final String ARG_TITLE = "title";
    private static final String ARG_NAME = "name";
    private static final String ARG_LAT_LNG = "latLng";
    private static final String ARG_LAT_LNG_BOUNDS = "latLngBounds";

    private static final Logger logger = LoggingUtil.getThreemaLogger("LocationPickerConfirmDialog");

    public static LocationPickerConfirmDialog newInstance(String title, String name, LatLng latLng, LatLngBounds latLngBounds, LocationConfirmDialogClickListener callback) {
        LocationPickerConfirmDialog dialog = new LocationPickerConfirmDialog();
        dialog.callback = callback;

        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_NAME, name);
        args.putParcelable(ARG_LAT_LNG, latLng);
        args.putParcelable(ARG_LAT_LNG_BOUNDS, latLngBounds);

        dialog.setArguments(args);
        return dialog;
    }

    public interface LocationConfirmDialogClickListener {
        void onOK(String tag, Object object);

        default void onCancel(String tag) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        String title = "";
        String name = "";
        LatLng latLng = null;

        Bundle arguments = getArguments();
        if (arguments != null) {
            title = arguments.getString(ARG_TITLE);
            name = arguments.getString(ARG_NAME);
            latLng = arguments.getParcelable(ARG_LAT_LNG);
        }

        final String tag = this.getTag();

        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_location_picker_confirm, null);

        TextView addressText = dialogView.findViewById(R.id.place_address);
        TextView nameText = dialogView.findViewById(R.id.place_name);
        TextView coordinatesText = dialogView.findViewById(R.id.place_coordinates);

        addressText.setVisibility(View.GONE);

        if (latLng != null) {
            new LocationNameAsyncTask(getContext(), addressText, latLng.getLatitude(), latLng.getLongitude()).execute();
        }

        if (!TestUtil.isEmptyOrNull(name)) {
            nameText.setText(name);
        } else {
            nameText.setVisibility(View.GONE);
        }

        if (latLng != null) {
            coordinatesText.setText(String.format(Locale.US, "%f, %f", latLng.getLatitude(), latLng.getLongitude()));
        } else {
            coordinatesText.setVisibility(View.GONE);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), getTheme());
        builder.setView(dialogView);

        if (!TestUtil.isEmptyOrNull(title)) {
            builder.setTitle(title);
        }

        builder.setPositiveButton(R.string.ok, (dialog, which) -> callback.onOK(tag, object));
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> callback.onCancel(tag)
        );

        AlertDialog alertDialog = builder.create();

        setCancelable(false);

        return alertDialog;
    }

    /**
     * AsyncTask that loads the address retrieved from the Geocoder to the supplied TextView
     */
    private static class LocationNameAsyncTask extends AsyncTask<Void, Void, String> {
        WeakReference<Context> contextWeakReference;
        WeakReference<TextView> textViewWeakReference;
        double latitude, longitude;

        public LocationNameAsyncTask(@Nullable Context context, @NonNull TextView textView, double latitude, double longitude) {
            this.contextWeakReference = new WeakReference<>(context);
            this.textViewWeakReference = new WeakReference<>(textView);
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        protected String doInBackground(Void... voids) {
            if (contextWeakReference != null && contextWeakReference.get() != null) {
                try {
                    return GeoLocationUtil.getAddressFromLocation(contextWeakReference.get(), latitude, longitude);
                } catch (IOException e) {
                    logger.error("Exception", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (!TestUtil.isEmptyOrNull(s)) {
                if (textViewWeakReference != null && textViewWeakReference.get() != null) {
                    textViewWeakReference.get().setText(s);
                    textViewWeakReference.get().setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
