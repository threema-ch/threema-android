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

package ch.threema.app.archive;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import ch.threema.storage.models.ConversationModel;

/**
 * The ViewModel's role is to provide data to the UI and survive configuration changes.
 * A ViewModel acts as a communication center between the Repository and the UI.
 *
 * Never pass context into ViewModel instances. Do not store Activity, Fragment, or View instances or
 * their Context in the ViewModel.
 */

public class ArchiveViewModel extends ViewModel {
	private LiveData<List<ConversationModel>> conversationModels;
	private ArchiveRepository repository;

	public ArchiveViewModel() {
		super();
		repository = new ArchiveRepository();
		conversationModels = repository.getConversationModels();
	}

	LiveData<List<ConversationModel>> getConversationModels() {
		return conversationModels;
	}

	public void onDataChanged() {
		repository.onDataChanged();
	}

	public void filter(String constraint) {
		repository.setFilter(constraint);
		repository.onDataChanged();
	}
}
