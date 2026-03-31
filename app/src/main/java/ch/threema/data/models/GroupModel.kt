package ch.threema.data.models

import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.GroupService
import ch.threema.app.services.GroupService.GroupState
import ch.threema.app.tasks.ReflectGroupSyncUpdateTask
import ch.threema.base.utils.Utils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toByteArray
import ch.threema.common.toHexString
import ch.threema.data.datatypes.IdColor
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.Common
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("data.GroupModel")

/**
 * The group identity uniquely identifies a group. It consists of the creator identity and the group
 * id.
 */
@Serializable
data class GroupIdentity(
    /** The creator identity string. Must be 8 characters long. */
    val creatorIdentity: IdentityString,
    /** The api group id of the group. */
    val groupId: Long,
) {
    /**
     * The hex representation of the group id.
     */
    val groupIdHexString: String by lazy { groupIdByteArray.toHexString() }

    /**
     * The group id as little endian byte array.
     */
    val groupIdByteArray: ByteArray by lazy { groupId.toByteArray(order = ByteOrder.LITTLE_ENDIAN) }

    /**
     * The group identity as protobuf data.
     */
    fun toProtobuf(): Common.GroupIdentity = Common.GroupIdentity.newBuilder()
        .setCreatorIdentity(creatorIdentity)
        .setGroupId(groupId)
        .build()
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
    /**
     * Is archived flag. Note that this information belongs to the 'conversation visibility' and should probably be moved to the new conversation
     * model. TODO(ANDR-3010)
     */
    @JvmField val isArchived: Boolean,
    /**
     * The precomputed id color if it is already known. If the id color is not set, it will be
     * computed lazily. Access the id color with [idColor].
     */
    private val precomputedIdColor: IdColor = IdColor.invalid(),
    /** The group description. */
    @JvmField val groupDescription: String?,
    /** The group description timestamp. */
    @JvmField val groupDescriptionChangedAt: Date?,
    /**
     * The group members' identities. This does not include the user's and creator's identity.
     *
     * Note that this set cannot be modified.
     */
    @JvmField val otherMembers: Set<IdentityString>,
    /** The group user state */
    @JvmField val userState: UserState,
    /**
     *  Encapsulates all logic of `Group.NotificationTriggerPolicyOverride.Policy` into a single `Long?` value.
     *  See [NotificationTriggerPolicyOverride] for possible values and their meanings.
     */
    @JvmField val notificationTriggerPolicyOverride: Long?,
) {
    init {
        require(groupIdentity.creatorIdentity !in otherMembers) {
            "the creator identity must not be included in member list"
        }
    }

    /**
     * Is true if the user state is set to member, false if the user has left the group or was
     * kicked.
     */
    val isMember: Boolean
        get() = userState == UserState.MEMBER

    /**
     * The group member's identities including the creator identity. Note that the user's identity is not included unless it is the creator.
     */
    val otherMembersAndCreator: Set<IdentityString> =
        otherMembers + groupIdentity.creatorIdentity

    /**
     * The group members' identities. This includes the user's identity and the creator's identity.
     *
     * Note that this set cannot be modified.
     *
     * @param myIdentity the user's identity
     */
    fun getAllMembers(myIdentity: IdentityString): Set<IdentityString> {
        return if (isMember) {
            // Note that the unmodifiable set is used to enforce immutability in java
            Collections.unmodifiableSet(setOf(myIdentity, groupIdentity.creatorIdentity) + otherMembers)
        } else {
            // Note that the unmodifiable set is used to enforce immutability in java
            Collections.unmodifiableSet(setOf(groupIdentity.creatorIdentity) + otherMembers)
        }
    }

    /**
     * The color index.
     */
    val idColor: IdColor by lazy {
        if (precomputedIdColor.isValid) {
            precomputedIdColor
        } else {
            IdColor.ofGroup(groupIdentity)
        }
    }

    val currentNotificationTriggerPolicyOverride
        get() = NotificationTriggerPolicyOverride.fromDbValueGroup(notificationTriggerPolicyOverride)
}

/**
 * A group.
 */
class GroupModel(
    val groupIdentity: GroupIdentity,
    data: GroupModelData,
    private val databaseBackend: DatabaseBackend,
    coreServiceManager: CoreServiceManager,
) : BaseModel<GroupModelData, ReflectGroupSyncUpdateTask>(
    MutableStateFlow(data),
    "GroupModel",
    coreServiceManager.multiDeviceManager,
    coreServiceManager.taskManager,
) {
    private val databaseId: Long? by lazy { databaseBackend.getGroupDatabaseId(groupIdentity) }

    private val myIdentity by lazy { coreServiceManager.identityStore.getIdentityString()!! }

    /**
     *  We have to make the bridge over to the old GroupService in order
     *  to keep the new and old caches both correct.
     */
    private val deprecatedGroupService: GroupService? by lazy {
        val serviceManager: ServiceManager? = ThreemaApplication.getServiceManager()
        if (serviceManager == null) {
            logger.warn("Tried to get the groupService before the service-manager was created.")
        }
        serviceManager?.groupService
    }

    init {
        require(groupIdentity == data.groupIdentity) {
            "Group identity mismatch"
        }
    }

    /**
     * Get the database id of the group.
     */
    fun getDatabaseId(): Long {
        return databaseId ?: throw IllegalStateException("Database id of group is null")
    }

    /**
     * Checks whether the group is a notes group or not. If the group does not exist anymore, null
     * is returned.
     */
    fun isNotesGroup(): Boolean? {
        val groupModelData = data ?: return null
        return groupIdentity.creatorIdentity == myIdentity && groupModelData.otherMembers.isEmpty()
    }

    /**
     * Checks whether the user is the creator of the group or not.
     */
    fun isCreator(): Boolean {
        return groupIdentity.creatorIdentity == myIdentity
    }

    /**
     * Checks whether the user has been kicked from the group or not.
     */
    fun isKicked(): Boolean =
        data?.userState == UserState.KICKED

    /**
     * Checks whether the user is a member of the group or not. The user is also considered a member if the user is the creator and hasn't disbanded
     * the group.
     *
     * Note that a reason for not being a member may be that the group no longer exists.
     */
    fun isMember(): Boolean {
        val groupModelData = data ?: return false
        return groupModelData.isMember
    }

    /**
     * Checks whether the group can be left or not. Note that if the group has been deleted, this method returns false.
     *
     * A group can be left if the user is *not* the creator and still a member.
     */
    fun isLeavable(): Boolean =
        !isCreator() && isMember()

    /**
     * Checks whether the group can be disbanded or not. Note that if the group has been deleted, this method returns false.
     *
     * A group can be disbanded if the user is the creator and still a member and there is at least one other member.
     */
    fun isDisbandable(): Boolean =
        isCreator() && isMember() && data?.otherMembers?.isNotEmpty() ?: false

    /**
     * Get the set of identities that should get the messages of the user. This includes all members as well as the creator. Note that the user is
     * never included in the set - even if they are the creator.
     */
    fun getRecipients(): Set<IdentityString> {
        val groupModelData = data ?: return emptySet()
        return if (isCreator()) {
            groupModelData.otherMembers
        } else {
            groupModelData.otherMembers + groupIdentity.creatorIdentity
        }
    }

    /**
     * Set new group name from sync.
     */
    fun setNameFromSync(newName: String) {
        updateFields(
            methodName = "setNameFromSync",
            detectChanges = { originalData -> originalData.name != newName },
            updateData = { originalData -> originalData.copy(name = newName) },
            updateDatabase = ::updateDatabase,
            onUpdated = { notifyDeprecatedOnRenameListeners() },
        )
    }

    /**
     * Persist the group name. Note that this change is not reflected and must therefore only used
     * in cases where the reflection already is done.
     */
    fun persistName(newName: String) {
        updateFields(
            methodName = "persistName",
            detectChanges = { originalData -> originalData.name != newName },
            updateData = { originalData -> originalData.copy(name = newName) },
            updateDatabase = ::updateDatabase,
            onUpdated = { notifyDeprecatedOnRenameListeners() },
        )
    }

    /**
     * Set the members from sync. Note that this does not trigger any listeners.
     */
    fun setMembersFromSync(members: Set<String>) {
        updateFields(
            methodName = "setMembersFromSync",
            detectChanges = { originalData -> originalData.otherMembers != members },
            updateData = { originalData ->
                originalData.copy(
                    otherMembers = Collections.unmodifiableSet(members),
                )
            },
            updateDatabase = ::updateDatabase,
            onUpdated = { },
        )
    }

    /**
     * Persist changes of the group members. Note that this change is not reflected and must
     * therefore only used in cases where the reflection already is done.
     */
    fun persistMemberChanges(addedMembers: Set<String>, removedMembers: Set<String>) {
        val data = ensureNotDeleted("persistMemberChanges")
        val newMemberSet = data.otherMembers.toMutableSet().apply { removeAll(removedMembers) } + addedMembers

        updateFields(
            methodName = "persistMemberChanges",
            detectChanges = { originalData -> originalData.otherMembers != newMemberSet },
            updateData = { originalData -> originalData.copy(otherMembers = newMemberSet) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                addedMembers.forEach {
                    notifyDeprecatedOnNewMemberListeners(it)
                }
                removedMembers.forEach {
                    notifyDeprecatedOnMemberKickedListeners(it)
                }
            },
        )
    }

    /**
     * Set the user state from sync.
     */
    fun setUserStateFromSync(userState: UserState) {
        persistUserState(userState)
    }

    /**
     * Persist the group user state. Note that this change is not reflected and must therefore only
     * be used in cases where the reflection already is done.
     */
    fun persistUserState(userState: UserState) {
        updateFields(
            methodName = "persistUserState",
            detectChanges = { originalData -> originalData.userState != userState },
            updateData = { originalData -> originalData.copy(userState = userState) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                when (userState) {
                    UserState.MEMBER -> notifyDeprecatedOnNewMemberListeners(myIdentity)
                    UserState.LEFT -> {
                        notifyDeprecatedOnMemberLeaveListeners(myIdentity)
                        notifyDeprecatedOnLeaveListeners()
                    }

                    UserState.KICKED -> notifyDeprecatedOnMemberKickedListeners(myIdentity)
                }
            },
        )
    }

    /**
     * Remove a member from remote. This will update the database and trigger the corresponding
     * listeners.
     */
    @Synchronized
    fun removeLeftMemberFromRemote(memberIdentity: IdentityString) {
        val data = ensureNotDeleted("removeLeftMemberFromRemote")
        val previousMembers = data.otherMembers
        val newMembers = previousMembers.filter { it != memberIdentity }.toSet()
        val previousGroupState = if (isNotesGroup()!!) GroupService.NOTES else GroupService.PEOPLE

        updateFields(
            methodName = "removeLeftMemberFromRemote",
            detectChanges = { originalData -> originalData.otherMembers != newMembers },
            updateData = { originalData -> originalData.copy(otherMembers = newMembers) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                notifyDeprecatedOnMemberLeaveListeners(memberIdentity)
                val newGroupState = if (isNotesGroup()!!) GroupService.NOTES else GroupService.PEOPLE
                if (previousGroupState != newGroupState) {
                    notifyDeprecatedOnGroupStateChangeListeners(
                        oldState = previousGroupState,
                        newState = newGroupState,
                    )
                }
            },
        )
    }

    /**
     * Update the group's notification-trigger-policy-override.
     *
     * @throws [ModelDeletedException] if model is deleted.
     *
     * @see NotificationTriggerPolicyOverride
     */
    fun setNotificationTriggerPolicyOverrideFromSync(notificationTriggerPolicyOverride: Long?) {
        updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromSync",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                notifyDeprecatedOnModifiedListeners()
            },
        )
    }

    /**
     * Update the group's notification-trigger-policy-override and reflecting the change.
     *
     * @throws [ModelDeletedException] if model is deleted.
     *
     * @see NotificationTriggerPolicyOverride
     */
    fun setNotificationTriggerPolicyOverrideFromLocal(notificationTriggerPolicyOverride: Long?) {
        updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromLocal",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                notifyDeprecatedOnModifiedListeners()
            },
            reflectUpdateTask = ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate(
                newNotificationTriggerPolicyOverride = NotificationTriggerPolicyOverride.fromDbValueGroup(
                    notificationTriggerPolicyOverride,
                ),
                groupIdentity = groupIdentity,
            ),
        )
    }

    /**
     * Archive or unarchive the group.
     *
     * TODO(ANDR-3721): As long as it is possible to mark a group as pinned outside of the group model, this method must be used extremely carefully
     *  as a group can never be archived *and* pinned.
     */
    fun setIsArchivedFromLocalOrRemote(isArchived: Boolean) {
        this.updateFields(
            methodName = "setIsArchiveFromLocalOrRemote",
            detectChanges = { originalData -> originalData.isArchived != isArchived },
            updateData = { originalData -> originalData.copy(isArchived = isArchived) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                notifyDeprecatedOnModifiedListeners()
            },
            reflectUpdateTask = ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityArchiveUpdate(
                isArchived = isArchived,
                groupIdentity = groupIdentity,
            ),
        )
    }

    /**
     * Archive or unarchive the group.
     *
     * TODO(ANDR-3721): As long as it is possible to mark a group as pinned outside of the group model, this method must be used extremely carefully
     *  as a group can never be archived *and* pinned.
     */
    fun setIsArchivedFromSync(isArchived: Boolean) {
        this.updateFields(
            methodName = "setIsArchivedFromSync",
            detectChanges = { originalData -> originalData.isArchived != isArchived },
            updateData = { originalData -> originalData.copy(isArchived = isArchived) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                notifyDeprecatedOnModifiedListeners()
            },
        )
    }

    private fun updateDatabase(updatedData: GroupModelData) {
        databaseBackend.updateGroup(GroupModelDataFactory.toDbType(updatedData))
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
    internal fun refreshFromDb(@Suppress("UNUSED_PARAMETER") token: RepositoryToken) {
        logger.info("Refresh from database")
        synchronized(this) {
            if (mutableData.value == null) {
                logger.warn("Cannot refresh deleted ${this.modelName} from DB")
                return
            }
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity) ?: run {
                mutableData.value = null
                return
            }
            val newData = GroupModelDataFactory.toDataType(dbGroup)
            check(newData.groupIdentity == groupIdentity) {
                "Cannot update group model with data for different group: ${newData.groupIdentity} != $groupIdentity"
            }
            mutableData.value = newData
        }
    }

    /**
     * Synchronously notify group modified listeners.
     */
    private fun notifyDeprecatedOnModifiedListeners() {
        ListenerManager.groupListeners.handle { it.onUpdate(groupIdentity) }
    }

    /**
     * Synchronously notify group rename listeners.
     */
    private fun notifyDeprecatedOnRenameListeners() {
        ListenerManager.groupListeners.handle { it.onRename(groupIdentity) }
    }

    /**
     * Synchronously notify new group member listeners.
     */
    private fun notifyDeprecatedOnNewMemberListeners(newIdentity: IdentityString) {
        ListenerManager.groupListeners.handle { it.onNewMember(groupIdentity, newIdentity) }
    }

    /**
     * Synchronously notify group member left listeners.
     */
    private fun notifyDeprecatedOnMemberLeaveListeners(leftIdentity: IdentityString) {
        ListenerManager.groupListeners.handle { it.onMemberLeave(groupIdentity, leftIdentity) }
    }

    /**
     * Synchronously notify group left listeners.
     */
    private fun notifyDeprecatedOnLeaveListeners() {
        ListenerManager.groupListeners.handle { it.onLeave(groupIdentity) }
    }

    /**
     * Synchronously notify group member kicked listeners.
     */
    private fun notifyDeprecatedOnMemberKickedListeners(kickedIdentity: IdentityString) {
        ListenerManager.groupListeners.handle { it.onMemberKicked(groupIdentity, kickedIdentity) }
    }

    /**
     * Synchronously notify group state change listeners.
     */
    private fun notifyDeprecatedOnGroupStateChangeListeners(
        @GroupState oldState: Int,
        @GroupState newState: Int,
    ) {
        ListenerManager.groupListeners.handle {
            it.onGroupStateChanged(
                groupIdentity,
                oldState,
                newState,
            )
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
        isArchived = value.isArchived,
        colorIndex = value.idColor.colorIndex,
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        members = value.otherMembers,
        userState = value.userState,
        notificationTriggerPolicyOverride = value.notificationTriggerPolicyOverride,
    )

    override fun toDataType(value: DbGroup): GroupModelData = GroupModelData(
        groupIdentity = GroupIdentity(value.creatorIdentity, groupIdDbToData(value.groupId)),
        name = value.name,
        createdAt = value.createdAt,
        synchronizedAt = value.synchronizedAt,
        lastUpdate = value.lastUpdate,
        isArchived = value.isArchived,
        precomputedIdColor = IdColor(value.colorIndex),
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        otherMembers = value.members,
        userState = value.userState,
        notificationTriggerPolicyOverride = value.notificationTriggerPolicyOverride,
    )

    private fun groupIdDbToData(littleEndianHexGroupId: String): Long {
        val byteArray = Utils.hexStringToByteArray(littleEndianHexGroupId)

        return ByteBuffer.wrap(byteArray)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getLong()
    }
}
