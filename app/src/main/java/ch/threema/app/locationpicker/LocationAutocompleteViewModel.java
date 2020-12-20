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

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

public class LocationAutocompleteViewModel extends ViewModel {
	private PoiRepository poiRepository;
	private MutableLiveData<PoiQuery> currentQuery = new MutableLiveData<>();
	private LiveData<List<Poi>> places = Transformations.switchMap(currentQuery, poiQuery ->
		poiRepository.getMutableLiveData(poiQuery));

	public LocationAutocompleteViewModel() {
		super();
		poiRepository = new PoiRepository();
		currentQuery.setValue(new PoiQuery("", null));
	}

	LiveData<List<Poi>> getPlaces() {
		return places;
	}

	LiveData<Boolean> getIsLoading() {
		return poiRepository.getIsLoading();
	}

	public void search(PoiQuery PoiQuery) {
		if (PoiQuery != null && !PoiQuery.equals(currentQuery.getValue())) {
			currentQuery.setValue(PoiQuery);
		}
	}
}
