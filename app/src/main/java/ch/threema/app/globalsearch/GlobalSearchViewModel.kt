/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.globalsearch

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel

class GlobalSearchViewModel : ViewModel() {
    val messageModels: LiveData<List<AbstractMessageModel>>

    private val repository = GlobalSearchRepository()

    init {
        messageModels = repository.messageModels.map { messageModels ->
            messageModels.filter { message ->
                if (message is GroupMessageModel) {
                    message.groupId > 0
                } else {
                    message.identity != null
                }
            }
        }
    }

    fun onQueryChanged(query: String?, filterFlags: Int, allowEmpty: Boolean, sortAscending: Boolean) {
        repository.onQueryChanged(query, filterFlags, allowEmpty, sortAscending)
    }

    val isLoading: LiveData<Boolean>
        get() = repository.isLoading

    fun onDataChanged() {
        repository.onDataChanged()
    }
}
