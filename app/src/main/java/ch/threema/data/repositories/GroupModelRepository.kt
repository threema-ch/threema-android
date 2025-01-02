/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data.repositories

import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.GroupId

class GroupModelRepository(
    private val cache: ModelTypeCache<GroupIdentity, GroupModel>, // Note: Synchronize access
    private val databaseBackend: DatabaseBackend,
    private val coreServiceManager: CoreServiceManager,
) {
    private object GroupModelRepositoryToken : RepositoryToken

    init {
        // Register an "old" group listener that updates the "new" models
        ListenerManager.groupListeners.add(object : GroupListener {
            override fun onRename(groupModel: ch.threema.storage.models.GroupModel?) {
                onModified(groupModel)
            }

            override fun onNewMember(
                group: ch.threema.storage.models.GroupModel?,
                newIdentity: String?,
            ) {
                onModified(group)
            }

            override fun onMemberLeave(
                group: ch.threema.storage.models.GroupModel?,
                identity: String?,
            ) {
                onModified(group)
            }

            override fun onMemberKicked(
                group: ch.threema.storage.models.GroupModel?,
                identity: String?,
            ) {
                onModified(group)
            }

            override fun onUpdate(groupModel: ch.threema.storage.models.GroupModel?) {
                onModified(groupModel)
            }

            override fun onRemove(groupModel: ch.threema.storage.models.GroupModel?) {
                // Currently, a group is never completely removed except when the identity is
                // removed or in tests. Therefore, we do not need to handle this here.
                // TODO(ANDR-3049): Groups are now removed from the database and therefore must be
                //  handled here.
            }

            override fun onLeave(groupModel: ch.threema.storage.models.GroupModel?) {
                onModified(groupModel)
            }

            private fun onModified(group: ch.threema.storage.models.GroupModel?) {
                if (group != null) {
                    onModified(
                        GroupIdentity(group.creatorIdentity, group.apiGroupId.toLong())
                    )
                }
            }

            @Synchronized
            private fun onModified(groupIdentity: GroupIdentity) {
                cache.get(groupIdentity)?.refreshFromDb(GroupModelRepositoryToken)
            }
        })
    }

    /**
     * Get the group with the [localGroupDbId]. Note that this call always accesses the database.
     * Use [getByGroupIdentity] or [getByCreatorIdentityAndId] to reduce database accesses.
     */
    @Synchronized
    fun getByLocalGroupDbId(localGroupDbId: Long): GroupModel? {
        // Note that we need to access the database to get the corresponding group model. The
        // fetched group is needed to get the group identity. If the group is not cached, the
        // fetched group data is used to construct the group model. Otherwise the cached group model
        // is returned.
        val dbGroup = databaseBackend.getGroupByLocalGroupDbId(localGroupDbId) ?: return null
        val groupIdentity = GroupIdentity(dbGroup.creatorIdentity, GroupId(dbGroup.groupId).toLong())
        return cache.getOrCreate(groupIdentity) {
            GroupModel(
                groupIdentity,
                GroupModelDataFactory.toDataType(dbGroup),
                databaseBackend,
                coreServiceManager,
            )
        }
    }

    @Synchronized
    fun getByCreatorIdentityAndId(creatorIdentity: String, groupId: GroupId): GroupModel? {
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        return getByGroupIdentity(groupIdentity)
    }

    @Synchronized
    fun getByGroupIdentity(groupIdentity: GroupIdentity): GroupModel? {
        return cache.getOrCreate(groupIdentity) {
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity)
                ?: return@getOrCreate null
            GroupModel(
                groupIdentity,
                GroupModelDataFactory.toDataType(dbGroup),
                databaseBackend,
                coreServiceManager,
            )
        }
    }
}
