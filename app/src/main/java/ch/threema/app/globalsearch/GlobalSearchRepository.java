/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;

public class GlobalSearchRepository {
	private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
	private MutableLiveData<List<AbstractMessageModel>> messageModels;
	private MessageService messageService;

	private String queryString = "";

	GlobalSearchRepository() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			messageService = null;
			try {
				messageService = serviceManager.getMessageService();
			} catch (ThreemaException e) {
				return;
			}
			if (messageService != null) {
				messageModels = new MutableLiveData<List<AbstractMessageModel>>() {
					@Nullable
					@Override
					public List<AbstractMessageModel> getValue() {
						return getMessagesForText(queryString, GlobalSearchActivity.FILTER_CHATS | GlobalSearchActivity.FILTER_GROUPS | GlobalSearchActivity.FILTER_INCLUDE_ARCHIVED);
					}
				};
			}
		}
	}

	LiveData<List<AbstractMessageModel>> getMessageModels() {
		return messageModels;
	}

	List<AbstractMessageModel> getMessagesForText(String queryString, int filterFlags) {
		List<AbstractMessageModel> messageModels = new ArrayList<>();

		boolean includeArchived = (filterFlags & GlobalSearchActivity.FILTER_INCLUDE_ARCHIVED) == GlobalSearchActivity.FILTER_INCLUDE_ARCHIVED;
		if ((filterFlags & GlobalSearchActivity.FILTER_CHATS) == GlobalSearchActivity.FILTER_CHATS) {
			messageModels.addAll(messageService.getContactMessagesForText(queryString, includeArchived));
		}

		if ((filterFlags & GlobalSearchActivity.FILTER_GROUPS) == GlobalSearchActivity.FILTER_GROUPS) {
			messageModels.addAll(messageService.getGroupMessagesForText(queryString, includeArchived));
		}

		if (messageModels.size() > 0) {
			Collections.sort(messageModels, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
		}
		return messageModels;
	}

	@SuppressLint("StaticFieldLeak")
	void onQueryChanged(String query, int filterFlags) {
		queryString = query;

		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... strings) {
				if (messageService != null) {
					if (TestUtil.empty(query)) {
						messageModels.postValue(new ArrayList<>());
						isLoading.postValue(false);
					} else {
						isLoading.postValue(true);
						messageModels.postValue(getMessagesForText(query, filterFlags));
						isLoading.postValue(false);
					}
				}
				return null;
			}
		}.execute();
	}

	LiveData<Boolean> getIsLoading() {
		return isLoading;
	}
}
