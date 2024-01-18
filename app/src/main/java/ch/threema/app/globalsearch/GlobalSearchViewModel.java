/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.globalsearch;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import ch.threema.storage.models.AbstractMessageModel;

public class GlobalSearchViewModel extends AndroidViewModel {
	private final LiveData<List<AbstractMessageModel>> messageModels;

	public LiveData<List<AbstractMessageModel>> getMessageModels() {
		return messageModels;
	}

	private final GlobalSearchRepository repository;

	public GlobalSearchViewModel(Application application) {
		super(application);
		repository = new GlobalSearchRepository();
		messageModels = repository.getMessageModels();
	}

	public void onQueryChanged(String query, int filterFlags, boolean allowEmpty, boolean sortAscending) {
		repository.onQueryChanged(query, filterFlags, allowEmpty, sortAscending);
	}

	public LiveData<Boolean> getIsLoading() {
		return repository.getIsLoading();
	}

    public void onDataChanged() {
		repository.onDataChanged();
    }
}
