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

package ch.threema.app.archive;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ConversationService;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ConversationModel;

/**
 * A Repository is a class that abstracts access to multiple data sources.
 *
 * The Repository is not part of the Architecture Components libraries, but is a
 * suggested best practice for code separation and architecture. A Repository class
 * handles data operations. It provides a clean API to the rest of the app for app data.
 */
class ArchiveRepository {
	private MutableLiveData<List<ConversationModel>> conversationModels;
	private ConversationService conversationService;

	ArchiveRepository() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			conversationService = null;
			try {
				conversationService = serviceManager.getConversationService();
			} catch (ThreemaException e) {
				return;
			}
			if (conversationService != null) {
				conversationModels = new MutableLiveData<List<ConversationModel>>() {
					@Nullable
					@Override
					public List<ConversationModel> getValue() {
						return conversationService.getArchived();
					}
				};
			}
		}
	}

	LiveData<List<ConversationModel>> getConversationModels() {
		return conversationModels;
	}

	@SuppressLint("StaticFieldLeak")
	public void onDataChanged() {
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... strings) {
				conversationModels.postValue(conversationService.getArchived());
				return null;
			}
		}.execute();
	}
}
