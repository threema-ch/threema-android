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

package ch.threema.app.locationpicker;

import com.mapbox.mapboxsdk.geometry.LatLng;

import androidx.annotation.Nullable;

public class PoiQuery {
	PoiQuery(String query, LatLng center) {
		this.query = query;
		this.center = center;
	}

	private String query;
	private LatLng center;

	public String getQuery() {
		return query;
	}

	public LatLng getCenter() {
		return center;
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (other instanceof PoiQuery) {
			PoiQuery otherPoi = (PoiQuery) other;
			boolean centerIsSame = false;
			boolean queryIsSame = false;

			if (center == null && otherPoi.getCenter() == null) {
				centerIsSame = true;
			}

			if (query == null && otherPoi.getQuery() == null) {
				queryIsSame = true;
			}

			if (center != null && center.equals(otherPoi.getCenter())) {
				centerIsSame = true;
			}

			if (query != null && query.equals(otherPoi.getQuery())) {
				queryIsSame = true;
			}

			return centerIsSame && queryIsSame;
		}
		return false;
	}
}
