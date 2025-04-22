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

package ch.threema.app.services

import ch.threema.app.managers.ListenerManager
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.ServerMessageModel

interface ServerMessageService {
    fun saveIncomingServerMessage(msg: ServerMessageModel)
}

class ServerMessageServiceImpl(
    private val databaseService: DatabaseServiceNew,
) : ServerMessageService {
    override fun saveIncomingServerMessage(msg: ServerMessageModel) {
        // store message
        databaseService.serverMessageModelFactory.storeServerMessageModel(msg)

        // notify listeners
        ListenerManager.serverMessageListeners.handle {
            if (msg.type == ServerMessageModel.TYPE_ALERT) {
                it.onAlert(msg)
            } else {
                it.onError(msg)
            }
        }
    }
}
