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

package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.GroupId
import ch.threema.domain.types.Identity

@SessionScoped
class GroupModelRepository(
    // Note: Synchronize access
    private val cache: ModelTypeCache<GroupIdentity, GroupModel>,
    private val databaseBackend: DatabaseBackend,
    private val coreServiceManager: CoreServiceManager,
) {
    private object GroupModelRepositoryToken : RepositoryToken

    private val myIdentity by lazy { coreServiceManager.identityStore.getIdentity()!! }

    init {
        // Register an "old" group listener that updates the "new" models
        ListenerManager.groupListeners.add(object : GroupListener {
            override fun onRename(groupIdentity: GroupIdentity) {
                onModified(groupIdentity)
            }

            override fun onNewMember(
                groupIdentity: GroupIdentity,
                identityNew: String?,
            ) {
                onModified(groupIdentity)
            }

            override fun onMemberLeave(
                groupIdentity: GroupIdentity,
                identityLeft: String,
            ) {
                onModified(groupIdentity)
            }

            override fun onMemberKicked(
                groupIdentity: GroupIdentity,
                identityKicked: String?,
            ) {
                onModified(groupIdentity)
            }

            override fun onUpdate(groupIdentity: GroupIdentity) {
                onModified(groupIdentity)
            }

            override fun onLeave(groupIdentity: GroupIdentity) {
                onModified(groupIdentity)
            }

            @Synchronized
            private fun onModified(groupIdentity: GroupIdentity) {
                cache.get(groupIdentity)?.refreshFromDb(GroupModelRepositoryToken)
            }
        })
    }

    @Synchronized
    fun getAll(): Collection<GroupModel> =
        databaseBackend.getAllGroups()
            .mapNotNull { dbGroup ->
                val groupIdentity = GroupIdentity(dbGroup.creatorIdentity, GroupId(dbGroup.groupId).toLong())
                cache.getOrCreate(groupIdentity) {
                    GroupModel(
                        groupIdentity = groupIdentity,
                        data = GroupModelDataFactory.toDataType(dbGroup),
                        databaseBackend = databaseBackend,
                        coreServiceManager = coreServiceManager,
                    )
                }
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
        val groupIdentity =
            GroupIdentity(dbGroup.creatorIdentity, GroupId(dbGroup.groupId).toLong())
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
    fun getByCreatorIdentityAndId(creatorIdentity: Identity, groupId: GroupId): GroupModel? {
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        return getByGroupIdentity(groupIdentity)
    }

    @Synchronized
    fun getByGroupIdentity(groupIdentity: GroupIdentity): GroupModel? {
        return cache.getOrCreate(groupIdentity) {
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity) ?: return@getOrCreate null
            GroupModel(
                groupIdentity,
                GroupModelDataFactory.toDataType(dbGroup),
                databaseBackend,
                coreServiceManager,
            )
        }
    }

    /**
     * Creates the given group. Note that this change is not reflected! The group is just persisted.
     *
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws GroupAlreadyExistsException if the group already exists
     */
    fun createFromSync(groupModelData: GroupModelData): GroupModel {
        return persistNewGroup(groupModelData)
    }

    /**
     * Creates the given group. Note that this change is not reflected!
     *
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws GroupAlreadyExistsException if the group already exists
     */
    fun persistNewGroup(groupModelData: GroupModelData): GroupModel {
        val groupModel = synchronized(this) {
            if (getByGroupIdentity(groupModelData.groupIdentity) != null) {
                throw GroupAlreadyExistsException()
            }
            try {
                databaseBackend.createGroup(GroupModelDataFactory.toDbType(groupModelData))
            } catch (e: SQLiteException) {
                throw GroupStoreException(e)
            }

            getByGroupIdentity(groupModelData.groupIdentity)
                ?: throw IllegalStateException("Group must exist at this point")
        }

        notifyDeprecatedListenersOnCreate(groupModel.groupIdentity)

        if (groupModelData.isMember) {
            notifyDeprecatedOnNewMemberListeners(groupModel.groupIdentity, myIdentity)
        }

        groupModelData.otherMembers.forEach {
            notifyDeprecatedOnNewMemberListeners(groupModel.groupIdentity, it)
        }

        return groupModel
    }

    /**
     * Remove the group with the given group identity. Note that this only removes data associated
     * to the group that is present in the database. Other data belonging to the group like avatars,
     * files, or chat settings are not affected and must be deleted separately.
     */
    fun persistRemovedGroup(groupIdentity: GroupIdentity) {
        val groupModel = getByGroupIdentity(groupIdentity)
        val groupDbColumnId = groupModel?.getDatabaseId() ?: run {
            return
        }

        synchronized(this) {
            databaseBackend.removeGroup(groupDbColumnId)
            cache.remove(groupIdentity)
            groupModel.refreshFromDb(GroupModelRepositoryToken)
        }

        notifyDeprecatedOnRemoveListeners(groupDbColumnId)
    }

    /**
     * Synchronously notify on create listeners.
     */
    private fun notifyDeprecatedListenersOnCreate(groupIdentity: GroupIdentity) {
        ListenerManager.groupListeners.handle { it.onCreate(groupIdentity) }
    }

    /**
     * Synchronously notify new group member listeners.
     */
    private fun notifyDeprecatedOnNewMemberListeners(
        groupIdentity: GroupIdentity,
        newIdentity: Identity,
    ) {
        ListenerManager.groupListeners.handle { it.onNewMember(groupIdentity, newIdentity) }
    }

    /**
     * Synchronously notify on remove group listeners.
     */
    private fun notifyDeprecatedOnRemoveListeners(groupDbColumnId: Long) {
        ListenerManager.groupListeners.handle { it.onRemove(groupDbColumnId) }
    }
}

/**
 * This exception is thrown if the group could not be added.
 */
sealed class GroupCreateException(msg: String, e: Exception? = null) : ThreemaException(msg, e)

/**
 * This exception is thrown if the group could not be added.
 */
class GroupStoreException(e: SQLiteException) : GroupCreateException("Failed to store the group", e)

/**
 * This exception is thrown if the group already exists.
 */
class GroupAlreadyExistsException : GroupCreateException("Group already exists")
