/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.testutils

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.data.models.GroupIdentity
import ch.threema.storage.runTransaction

fun clearDatabaseAndCaches(serviceManager: ServiceManager) {
    // First get all available contacts and groups
    val contactIdentities =
        serviceManager.databaseService.contactModelFactory.all.map { contact ->
            contact.identity
        }
    val groupModelRepository = serviceManager.modelRepositories.groups
    val groups = serviceManager.databaseService.groupModelFactory.all
        .map { group ->
            GroupIdentity(group.creatorIdentity, group.apiGroupId.toLong())
        }.onEach {
            // Because the column id is lazily loaded, we need to access it at least once
            // before the group model is deleted below
            groupModelRepository.getByGroupIdentity(it)?.getDatabaseId()
        }

    // Clear entire database
    serviceManager.databaseService.writableDatabase.runTransaction {
        rawExecSQL("PRAGMA foreign_keys = OFF;")

        query("SELECT name FROM sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { cursor ->
            while (cursor.moveToNext()) {
                rawExecSQL("DELETE FROM ${cursor.getString(0)};")
            }
        }

        rawExecSQL("PRAGMA foreign_keys = ON;")
    }

    // Clear caches in services and trigger listeners to refresh the new models from database
    val contactService = serviceManager.contactService
    val myIdentity = serviceManager.identityStore.getIdentity()
    contactIdentities.forEach { identity ->
        contactService.invalidateCache(identity)
        ListenerManager.contactListeners.handle { it.onRemoved(identity) }
        serviceManager.dhSessionStore.deleteAllDHSessions(myIdentity, identity)
    }
    val groupService = serviceManager.groupService
    groupService.removeAll()
    groups.forEach { groupIdentity ->
        groupService.removeFromCache(groupIdentity)
        groupModelRepository.persistRemovedGroup(groupIdentity)
    }
    serviceManager.conversationService.reset()
}
