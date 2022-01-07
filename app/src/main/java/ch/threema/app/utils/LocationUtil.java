/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.locationpicker.Poi;
import ch.threema.app.services.PreferenceService;

public class LocationUtil {
	private static final Logger logger = LoggerFactory.getLogger(LocationUtil.class);

	public static int getPlaceDrawableRes(@NonNull Context context, @NonNull Poi poi, boolean returnDefault) {
		int id = 0;
		String defPackage = context.getPackageName();
		String type = poi.getType();

		if (!TestUtil.empty(poi.getType())) {
			id = context.getResources().getIdentifier("ic_places_" + type, "drawable", defPackage);
		}

		if (id > 0) {
			return id;
		}
		if (returnDefault) {
			return R.drawable.ic_location_on_filled;
		} else {
			return R.drawable.ic_stop_filled;
		}
	}

	/**
	 * Get the marker icon to be used on a MapBox map that represents the given Point of Interest
	 * @param context The Context
	 * @param poi The Point of Interest
	 * @return A MapBox icon
	 */
	public static @NonNull Icon getMarkerIcon(@NonNull Context context, @NonNull Poi poi) {
		int innerIconSize = context.getResources().getDimensionPixelSize(R.dimen.lp_marker_inner_icon_size);

		Drawable bgDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_solid_red_32dp);
		DrawableCompat.setTint(bgDrawable, context.getResources().getColor("natural".equals(poi.getCategory()) ? R.color.material_green : R.color.material_red));

		Drawable fgDrawable = AppCompatResources.getDrawable(context, getPlaceDrawableRes(context, poi, false));
		DrawableCompat.setTint(fgDrawable, context.getResources().getColor(R.color.lp_marker_icon));

		Bitmap bitmap = Bitmap.createBitmap(bgDrawable.getIntrinsicWidth(), bgDrawable.getIntrinsicHeight() * 2, Bitmap.Config.ARGB_8888);

		Canvas canvas = new Canvas(bitmap);

		bgDrawable.setBounds(0, 0, canvas.getWidth(), bgDrawable.getIntrinsicHeight());

		int left = (canvas.getWidth() - innerIconSize) / 2;
		int top = (bgDrawable.getIntrinsicHeight() - innerIconSize) / 3;
		int right = left + innerIconSize;
		int bottom = top + innerIconSize;

		fgDrawable.setBounds(left, top, right, bottom);

		bgDrawable.draw(canvas);
		fgDrawable.draw(canvas);

		return IconFactory.getInstance(context).fromBitmap(bitmap);
	}


	// URL for Threema POI server
	private static final String POI_HOST = "poi.threema.ch";

	private static @NonNull String getPoiHost(@NonNull PreferenceService preferenceService) {
		if (BuildConfig.DEBUG) {
			final String hostOverride = preferenceService.getPoiServerHostOverride();
			if (hostOverride != null) {
				return hostOverride;
			}
		}
		return POI_HOST;
	}

	/**
	 * Return the URL template for the "names" POI server lookup.
	 */
	public static @NonNull String getPlacesUrl(@NonNull PreferenceService preferenceService) {
		return "https://" + getPoiHost(preferenceService) + "/names/%f/%f/%s/";
	}

	/**
	 * Return the URL template for the "around" POI server lookup.
	 */
	public static @NonNull String getPoiUrl(@NonNull PreferenceService preferenceService) {
		return "https://" + getPoiHost(preferenceService) + "/around/%f/%f/%d/";
	}


	/**
	 * Move marker bitmap up so its bottom will be at the center of the resulting bitmap
	 * This is necessary because MapBox references the center of the marker image
	 * @return The resulting bitmap. Will have twice the height of the originating bitmap
	 */
	public static Bitmap moveMarker(Bitmap inBitmap) {
		Bitmap outBitmap = Bitmap.createBitmap(inBitmap.getWidth(), inBitmap.getHeight() * 2, Bitmap.Config.ARGB_8888);
		Canvas bitmapCanvas = new Canvas(outBitmap);
		Bitmap tempBitmap = inBitmap.copy(Bitmap.Config.ARGB_8888, false);
		bitmapCanvas.drawBitmap(tempBitmap, 0, 0, null);
		return outBitmap;
	}
}
