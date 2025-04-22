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
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
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
import ch.threema.app.tasks.ReflectionFailed
import ch.threema.app.tasks.ReflectionPreconditionFailed
import ch.threema.app.tasks.ReflectionSuccess
import ch.threema.app.tasks.tryUploadingGroupPhoto
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.now
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("CreateGroupFlow")

private const val DIALOG_TAG_CREATING_GROUP = "groupCreate"

class ProfilePicture(val profilePicture: ByteArray?) {
    constructor(profilePictureFile: File?) : this(
        if (profilePictureFile != null) {
            try {
                BitmapFactory.decodeFile(profilePictureFile.path)
            } catch (e: Error) {
                logger.error("Could not decode avatar file", e)
                null
            }
        } else {
            null
        },
    )

    constructor(profilePictureBitmap: Bitmap?) : this(
        profilePictureBitmap?.let { BitmapUtil.bitmapToJpegByteArray(it) },
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
    private val fragmentManager: FragmentManager?,
    private val context: Context,
    private val groupCreateProperties: GroupCreateProperties,
    private val groupModelRepository: GroupModelRepository,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
    private val groupCallManager: GroupCallManager,
    private val apiService: ApiService,
    private val fileService: FileService,
    private val taskManager: TaskManager,
) : BackgroundTask<GroupModel?> {
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

    override fun runBefore() {
        fragmentManager?.let {
            GenericProgressDialog.newInstance(R.string.creating_group, R.string.please_wait)
                .show(it, DIALOG_TAG_CREATING_GROUP)
        }
    }

    override fun runInBackground(): GroupModel? {
        if (ConfigUtils.isWorkBuild() && AppRestrictionUtil.isCreateGroupDisabled(context)) {
            Toast.makeText(context, R.string.disabled_by_policy_short, Toast.LENGTH_LONG).show()
            return null
        }

        val groupModel = if (multiDeviceManager.isMultiDeviceActive) {
            runBlocking {
                val reflectionResult = taskManager.schedule(
                    ReflectGroupSyncCreateTask(
                        groupModelData,
                        contactModelRepository,
                        groupModelRepository,
                        nonceFactory,
                        uploadGroupPhoto = { uploadGroupPicture() },
                        finishGroupCreation = { createGroupLocally(it) },
                        multiDeviceManager,
                    ),
                ).await()

                when (reflectionResult) {
                    is ReflectionSuccess -> reflectionResult.result
                    is ReflectionPreconditionFailed -> run {
                        logger.error(
                            "Reflection of group sync create failed due to the precondition",
                            reflectionResult.transactionException,
                        )
                        return@runBlocking null
                    }

                    is ReflectionFailed -> run {
                        logger.error(
                            "Reflection of group sync create failed",
                            reflectionResult.exception,
                        )
                        return@runBlocking null
                    }
                }
            }
        } else {
            val profilePictureChange = uploadGroupPicture()
            createGroupLocally(profilePictureChange)
        }

        return groupModel
    }

    override fun runAfter(result: GroupModel?) {
        fragmentManager?.let {
            DialogUtil.dismissDialog(
                it,
                DIALOG_TAG_CREATING_GROUP,
                true,
            )
        }
    }

    private fun uploadGroupPicture(): ProfilePictureChange? {
        return groupCreateProperties.profilePicture.profilePicture?.let { profilePicture ->
            val uploadResult = tryUploadingGroupPhoto(profilePicture, apiService)
            SetProfilePicture(profilePicture, uploadResult)
        }
    }

    private fun createGroupLocally(profilePictureChange: ProfilePictureChange?): GroupModel? {
        // Persist the new group
        val groupModel = groupModelRepository.persistNewGroup(groupModelData)

        // Persist the group photo
        groupCreateProperties.profilePicture.profilePicture?.let {
            {
                fileService.writeGroupAvatar(groupModel, it)
            }.catchAllExceptNetworkException { e ->
                logger.error("Could not persist group photo", e)
            }
        }

        // Abort if there are no members
        if (groupCreateProperties.members.isEmpty()) {
            return groupModel
        }

        val groupModelData = groupModel.data.value ?: run {
            logger.error("Group model data expected to exist")
            return null
        }

        taskManager.schedule(
            GroupCreateTask(
                groupModelData.name,
                profilePictureChange ?: RemoveProfilePicture,
                groupModelData.otherMembers,
                groupModelData.groupIdentity,
                PredefinedMessageIds(),
                outgoingCspMessageServices,
                groupCallManager,
                fileService,
                apiService,
                groupModelRepository,
            ),
        )

        return groupModel
    }
}
