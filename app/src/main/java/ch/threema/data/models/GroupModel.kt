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

package ch.threema.data.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.utils.runtimeAssert
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.domain.taskmanager.Task
import ch.threema.storage.models.GroupModel.UserState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("data.GroupModel")

/**
 * The group identity uniquely identifies a group. It consists of the creator identity and the group
 * id.
 */
@Serializable
data class GroupIdentity(
    /** The creator identity string. Must be 8 characters long. */
    val creatorIdentity: String,
    /** The group id of the group. */
    val groupId: Long,
) {
    /**
     * The hex representation of the group id.
     */
    val groupIdHexString: String by lazy { Utils.byteArrayToHexString(groupIdByteArray) }

    /**
     * The group id as little endian byte array.
     */
    val groupIdByteArray: ByteArray by lazy { Utils.longToByteArrayLittleEndian(groupId) }
}

data class GroupModelData(
    /** The identity of the group. */
    @JvmField val groupIdentity: GroupIdentity,
    /** The group name. */
    @JvmField val name: String?,
    /** The creation date. */
    @JvmField val createdAt: Date,
    /** Currently not used. Might be used for periodic group sync in the future. TODO(SE-146) */
    @JvmField val synchronizedAt: Date?,
    /** Last update flag. */
    @JvmField val lastUpdate: Date?,
    /** Deleted flag. */
    @JvmField val deleted: Boolean,
    /** Is archived flag. */
    @JvmField val isArchived: Boolean,
    /** The color index. */
    val colorIndex: UByte,
    /** The group description. */
    @JvmField val groupDescription: String?,
    /** The group description timestamp. */
    @JvmField val groupDescriptionChangedAt: Date?,
    /**
     * The group members' identities. This does not include the user's identity.
     *
     * Note that this set must not be modified.
     *
     * @throws UnsupportedOperationException if the set is being modified
     */
    @JvmField val members: Set<String>,
    /** The group user state */
    @JvmField val userState: UserState,
) {
    companion object {
        /**
         * Factory function using only Java-compatible types.
         */
        @JvmStatic
        fun javaCreate(
            creatorIdentity: String,
            groupId: Long,
            name: String?,
            createdAt: Date,
            synchronizedAt: Date,
            lastUpdate: Date?,
            deleted: Boolean,
            isArchived: Boolean,
            colorIndex: Int,
            groupDescription: String?,
            groupDescriptionChangedAt: Date?,
            members: Set<String>,
            userState: UserState,
        ): GroupModelData {
            if (colorIndex < 0 || colorIndex > 255) {
                throw IllegalArgumentException("colorIndex must be between 0 and 255")
            }
            return GroupModelData(
                GroupIdentity(creatorIdentity, groupId),
                name,
                createdAt,
                synchronizedAt,
                lastUpdate,
                deleted,
                isArchived,
                colorIndex.toUByte(),
                groupDescription,
                groupDescriptionChangedAt,
                Collections.unmodifiableSet(members),
                userState,
            )
        }
    }

    /**
     * Return the [colorIndex] as [Int].
     */
    fun colorIndexInt(): Int = colorIndex.toInt()
}

/**
 * A group.
 */
class GroupModel(
    val groupIdentity: GroupIdentity,
    data: GroupModelData,
    private val databaseBackend: DatabaseBackend,
    coreServiceManager: CoreServiceManager,
) : BaseModel<GroupModelData, Task<*, Any>>(
    MutableStateFlow(data),
    "GroupModel",
    coreServiceManager.multiDeviceManager,
    coreServiceManager.taskManager
) {

    init {
        runtimeAssert(
            groupIdentity == data.groupIdentity,
            "Group identity mismatch"
        )
    }

    /**
     * Update all data from database.
     *
     * Note: This method may only be called by the repository, in code that bridges the old models
     * to the new models. All other code does not need to refresh the data, the model's state flow
     * should always be up to date.
     *
     * Note: If the model is marked as deleted, then this will have no effect.
     */
    internal fun refreshFromDb(token: RepositoryToken) {
        logger.info("Refresh from database")
        synchronized(this) {
            if (mutableData.value == null) {
                logger.warn("Cannot refresh deleted ${this.modelName} from DB")
                return
            }
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity) ?: return
            val newData = GroupModelDataFactory.toDataType(dbGroup)
            runtimeAssert(
                newData.groupIdentity == groupIdentity,
                "Cannot update group model with data for different group: ${newData.groupIdentity} != $groupIdentity"
            )
            mutableData.value = newData
        }
    }

}

internal object GroupModelDataFactory : ModelDataFactory<GroupModelData, DbGroup> {
    override fun toDbType(value: GroupModelData): DbGroup = DbGroup(
        creatorIdentity = value.groupIdentity.creatorIdentity,
        groupId = value.groupIdentity.groupIdHexString,
        name = value.name,
        createdAt = value.createdAt,
        synchronizedAt = value.synchronizedAt,
        lastUpdate = value.lastUpdate,
        deleted = value.deleted,
        isArchived = value.isArchived,
        colorIndex = value.colorIndex,
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        members = value.members,
        userState = value.userState,
    )

    override fun toDataType(value: DbGroup): GroupModelData = GroupModelData(
        groupIdentity = GroupIdentity(value.creatorIdentity, groupIdDbToData(value.groupId)),
        name = value.name,
        createdAt = value.createdAt,
        synchronizedAt = value.synchronizedAt,
        lastUpdate = value.lastUpdate,
        deleted = value.deleted,
        isArchived = value.isArchived,
        colorIndex = value.colorIndex,
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        members = value.members,
        userState = value.userState,
    )

    private fun groupIdDbToData(littleEndianHexGroupId: String): Long {
        val byteArray = Utils.hexStringToByteArray(littleEndianHexGroupId)

        return ByteBuffer.wrap(byteArray)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getLong()
    }
}
