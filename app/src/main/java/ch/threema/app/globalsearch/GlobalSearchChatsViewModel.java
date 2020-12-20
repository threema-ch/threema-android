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

package ch.threema.app.globalsearch;

import android.app.Application;

import androidx.lifecycle.LiveData;

/**
 * The ViewModel's role is to provide data to the UI and survive configuration changes.
 * A ViewModel acts as a communication center between the Repository and the UI.
 *
 * Never pass context into ViewModel instances. Do not store Activity, Fragment, or View instances or
 * their Context in the ViewModel.
 */

public class GlobalSearchChatsViewModel extends GlobalSearchViewModel {
	protected GlobalSearchChatsRepository repository;

	public GlobalSearchChatsViewModel(Application application) {
		super(application);
		repository = new GlobalSearchChatsRepository(application);
		messageModels = repository.getMessageModels();
	}

	@Override
	public void onQueryChanged(String query) {
		repository.onQueryChanged(query);
	}

	@Override
	LiveData<Boolean> getIsLoading() {
		return repository.getIsLoading();
	}
}
