/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

import java.util.List;

import androidx.lifecycle.MutableLiveData;
import ch.threema.storage.models.AbstractMessageModel;

public class GlobalSearchChatsRepository extends GlobalSearchRepository {
	private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

	GlobalSearchChatsRepository(Application application) { super(application); }

	List<AbstractMessageModel> getMessagesForText(String queryString) {
		return messageService.getContactMessagesForText(queryString);
	}
}
