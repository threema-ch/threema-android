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

package ch.threema.app.groupflows

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.ReflectGroupSyncCreateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.tasks.tryUploadingGroupPhoto
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.now
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.runCatchingExceptNetworkException
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("CreateGroupFlow")

class ProfilePicture(val profilePicture: ByteArray?) {
    constructor(profilePictureFile: File?) : this(
        profilePictureFile?.let {
            try {
                BitmapFactory.decodeFile(profilePictureFile.path)
            } catch (e: Error) {
                logger.error("Could not decode avatar file", e)
                null
            }
        },
    )

    constructor(profilePictureBitmap: Bitmap?) : this(
        profilePictureBitmap?.let {
            BitmapUtil.bitmapToJpegByteArray(profilePictureBitmap)
        },
    )
}

class GroupCreateProperties(
    val name: String,
    val profilePicture: ProfilePicture,
    val members: Set<String>,
)

/**
 * This class is used to create a group from local. Note that it is recommended to call
 * [GroupFlowDispatcher.runCreateGroupFlow] instead of running this task directly. This ensures that
 * group flows are not executed concurrently.
 */
class CreateGroupFlow(
    private val context: Context,
    private val groupCreateProperties: GroupCreateProperties,
    private val groupModelRepository: GroupModelRepository,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupCallManager: GroupCallManager,
    private val apiService: ApiService,
    private val fileService: FileService,
    private val taskManager: TaskManager,
    private val connection: ServerConnection,
) : BackgroundTask<GroupFlowResult> {
    private val contactModelRepository = outgoingCspMessageServices.contactModelRepository
    private val multiDeviceManager = outgoingCspMessageServices.multiDeviceManager
    private val nonceFactory = outgoingCspMessageServices.nonceFactory

    private val myIdentity by lazy { outgoingCspMessageServices.identityStore.identity }

    private val groupModelData by lazy {
        val now = now()
        GroupModelData(
            groupIdentity = GroupIdentity(
                myIdentity,
                SecureRandom().nextLong(),
            ),
            name = groupCreateProperties.name,
            createdAt = now,
            synchronizedAt = null,
            lastUpdate = now,
            isArchived = false,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            otherMembers = groupCreateProperties.members - myIdentity,
            userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
            notificationTriggerPolicyOverride = null,
        )
    }

    override fun runInBackground(): GroupFlowResult {
        if (ConfigUtils.isWorkBuild() && AppRestrictionUtil.isCreateGroupDisabled(context)) {
            return GroupFlowResult.Failure.Other
        }
        val groupFlowResult = if (multiDeviceManager.isMultiDeviceActive) {
            if (connection.connectionState != ConnectionState.LOGGEDIN) {
                return GroupFlowResult.Failure.Network
            }
            runBlocking {
                val reflectionResult: ReflectionResult<GroupModel?> = taskManager.schedule(
                    ReflectGroupSyncCreateTask(
                        groupModelData = groupModelData,
                        contactModelRepository = contactModelRepository,
                        groupModelRepository = groupModelRepository,
                        nonceFactory = nonceFactory,
                        uploadGroupPhoto = ::uploadGroupPicture,
                        finishGroupCreation = ::createGroupLocally,
                        multiDeviceManager = multiDeviceManager,
                    ),
                ).await()
                when (reflectionResult) {
                    is ReflectionResult.Success -> GroupFlowResult.Success(reflectionResult.result!!)
                    is ReflectionResult.PreconditionFailed -> {
                        logger.error("Reflection of group sync create failed due to the precondition", reflectionResult.transactionException)
                        GroupFlowResult.Failure.Other
                    }

                    is ReflectionResult.Failed -> {
                        logger.error("Reflection of group sync create failed", reflectionResult.exception)
                        GroupFlowResult.Failure.Other
                    }

                    is ReflectionResult.MultiDeviceNotActive -> {
                        // Note that this is a very rare edge case that should not be possible at all. If it happens, we need to return a failure as
                        // the changes are not persisted and not sent to the members.
                        logger.warn("Reflection failed because multi device is not active")
                        GroupFlowResult.Failure.Other
                    }
                }
            }
        } else {
            val profilePictureChange =
                runCatching {
                    uploadGroupPicture()
                }.getOrElse { exception ->
                    logger.error("Failed to upload the group photo", exception)
                    // While this could also have internal reasons, a network failure is more likely
                    // TODO(ANDR-3823): Distinguish between different failures from uploading process
                    return GroupFlowResult.Failure.Network
                }
            createGroupLocally(profilePictureChange)
        }

        return groupFlowResult
    }

    private fun uploadGroupPicture(): ProfilePictureChange? =
        groupCreateProperties.profilePicture.profilePicture?.let { profilePicture ->
            val uploadResult = tryUploadingGroupPhoto(profilePicture, apiService)
            SetProfilePicture(profilePicture, uploadResult)
        }

    /**
     *  TODO(ANDR-3823): Remove this warning once corrected
     *
     *  Warning: At this point, before `ANDR-3823` is implemented, *any* specific type of [GroupFlowResult.Failure]
     *  returned from this method will effectively result in a [GroupFlowResult.Failure.Other] *if MD is active*.
     */
    private fun createGroupLocally(profilePictureChange: ProfilePictureChange?): GroupFlowResult {
        // Persist the new group
        val groupModel = groupModelRepository.persistNewGroup(groupModelData)

        // Persist the group photo
        groupCreateProperties.profilePicture.profilePicture?.let { profilePictureBytes ->
            runCatchingExceptNetworkException {
                fileService.writeGroupAvatar(groupModel, profilePictureBytes)
            }.onFailure { throwable ->
                logger.error("Could not persist group photo", throwable)
            }
        }

        // Abort if there are no members
        if (groupCreateProperties.members.isEmpty()) {
            return GroupFlowResult.Success(groupModel)
        }

        val groupModelData = groupModel.data.value ?: run {
            logger.error("Group model data expected to exist")
            return GroupFlowResult.Failure.Other
        }

        // Can be suppressed because we do not wait for the task to complete
        @Suppress("DeferredResultUnused")
        taskManager.schedule(
            GroupCreateTask(
                name = groupModelData.name,
                profilePictureChange = profilePictureChange ?: RemoveProfilePicture,
                members = groupModelData.otherMembers,
                groupIdentity = groupModelData.groupIdentity,
                predefinedMessageIds = PredefinedMessageIds(),
                outgoingCspMessageServices = outgoingCspMessageServices,
                groupCallManager = groupCallManager,
                fileService = fileService,
                apiService = apiService,
                groupModelRepository = groupModelRepository,
            ),
        )

        return GroupFlowResult.Success(groupModel)
    }
}
