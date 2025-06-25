/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ch.threema.app.ThreemaApplication
import ch.threema.storage.DatabaseService
import ch.threema.storage.factories.ServerMessageModelFactory

class ServerMessageViewModel : ViewModel() {
    private val serverMessageModelFactory: ServerMessageModelFactory?

    private val serverMessage = MutableLiveData<String?>()
    fun getServerMessage(): LiveData<String?> = serverMessage

    init {
        val serviceManager = ThreemaApplication.getServiceManager()
        val databaseService: DatabaseService? = serviceManager?.databaseService
        serverMessageModelFactory = databaseService?.serverMessageModelFactory

        serverMessage.postValue(serverMessageModelFactory?.popServerMessageModel()?.message)
    }

    fun markServerMessageAsRead() {
        // Delete currently shown message from database if the same message arrived again in the
        // meantime.
        serverMessage.value?.let {
            serverMessageModelFactory?.delete(it)
        }
        // Post the next message. If it is null, then no server message is available
        serverMessage.postValue(serverMessageModelFactory?.popServerMessageModel()?.message)
    }
}
