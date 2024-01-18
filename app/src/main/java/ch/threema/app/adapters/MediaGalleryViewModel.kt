/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.adapters

import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.storage.models.AbstractMessageModel

class MediaGalleryViewModel @Keep constructor(messageReceiver: MessageReceiver<*>) : ViewModel() {
    private val repository: MediaGalleryRepository = MediaGalleryRepository()
    private var messageModels: LiveData<List<AbstractMessageModel?>?>? = null

    init {
        repository.setMessageReceiver(messageReceiver)
        repository.setFilter(null)
        messageModels = repository.getAbstractMessageModels()
    }

    fun getAbstractMessageModels(): LiveData<List<AbstractMessageModel?>?>? {
        return messageModels
    }

    fun setFilter(contentTypes: IntArray?) {
        repository.setFilter(contentTypes)
        repository.onDataChanged()
    }

    class MediaGalleryViewModelFactory(val messageReceiver: MessageReceiver<*>) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return modelClass.getConstructor(MessageReceiver::class.java)
                .newInstance(messageReceiver)
        }
    }
}
