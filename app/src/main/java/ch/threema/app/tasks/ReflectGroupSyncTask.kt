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

package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelData
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.TransactionScope.TransactionException
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.MdD2D.TransactionScope.Scope.GROUP_SYNC
import ch.threema.protobuf.d2d.sync.GroupKt.notificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.GroupKt.notificationTriggerPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.ConversationVisibility
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.NotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.NotificationTriggerPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.UserState
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.groupIdentity
import ch.threema.protobuf.identities
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.GroupModel
import com.google.protobuf.kotlin.toByteString

private val logger = LoggingUtil.getThreemaLogger("ReflectGroupSyncTask")

abstract class ReflectGroupSyncTask<TransactionResult, TaskResult>(
    multiDeviceManager: MultiDeviceManager,
) : ReflectSyncTask<TransactionResult, TaskResult>(multiDeviceManager, GROUP_SYNC) {
    /**
     * Runs the transaction. The group sync is performed inside of the transaction.
     */
    protected suspend fun runTransaction(handle: ActiveTaskCodec): ReflectionResult<TaskResult> {
        val result = try {
            reflectSync(handle)
        } catch (e: Exception) {
            when (e) {
                is TransactionException -> {
                    logger.error("Precondition failed for group sync task", e)
                    return ReflectionPreconditionFailed(e)
                }

                is NetworkException -> throw e
                else -> return ReflectionFailed(e)
            }
        }
        return ReflectionSuccess(result)
    }
}

/**
 * The result of executing a [ReflectGroupSyncTask]. It may either be a [ReflectionSuccess],
 * [ReflectionPreconditionFailed] or [ReflectionFailed].
 */
sealed interface ReflectionResult<T>

/**
 * This result indicates that the reflection task has completed successfully.
 */
data class ReflectionSuccess<T>(val result: T) : ReflectionResult<T>

/**
 * This result indicates that the reflection task has failed due to a failed precondition.
 */
data class ReflectionPreconditionFailed<T>(val transactionException: TransactionException) :
    ReflectionResult<T>

/**
 * This result indicates that there was an undefined error while reflecting.
 */
data class ReflectionFailed<T>(val exception: Exception) : ReflectionResult<T>

/**
 * Get a group sync from the given group model data.
 *
 * Note that the profile-picture is not set.
 *
 * For notification trigger and sound policy override, the default option is chosen if not provided.
 */
fun GroupModelData.toGroupSync(
    isPrivateChat: Boolean?,
    conversationVisibility: ConversationVisibility?,
    notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride? = null,
    notificationSoundPolicyOverride: NotificationSoundPolicyOverride? = null,
    profilePictureChange: ProfilePictureChange? = null,
): Group = group {
    val data = this@toGroupSync
    groupIdentity = getProtoGroupIdentity()
    data.name?.let {
        name = it
    }
    createdAt = data.createdAt.time
    userState = data.getProtoUserState()
    memberIdentities = data.getProtoMembers()
    if (isPrivateChat != null) {
        conversationCategory = if (isPrivateChat) {
            MdD2DSync.ConversationCategory.PROTECTED
        } else {
            MdD2DSync.ConversationCategory.DEFAULT
        }
    }
    if (conversationVisibility != null) {
        this.conversationVisibility = conversationVisibility
    }

    when (profilePictureChange) {
        is SetProfilePicture -> {
            profilePictureChange.profilePictureUploadResult?.let { uploadResult ->
                profilePicture = deltaImage {
                    updated = image {
                        blob = blob {
                            id = uploadResult.blobId.toByteString()
                            key = uploadResult.encryptionKey.toByteString()
                            nonce = ProtocolDefines.GROUP_PHOTO_NONCE.toByteString()
                        }
                    }
                }
            }
        }

        is RemoveProfilePicture -> {
            profilePicture = deltaImage {
                removed = unit { }
            }
        }

        null -> Unit
    }

    this.notificationTriggerPolicyOverride = notificationTriggerPolicyOverride
        ?: notificationTriggerPolicyOverride {
            default = unit { }
        }

    this.notificationSoundPolicyOverride = notificationSoundPolicyOverride
        ?: notificationSoundPolicyOverride {
            default = unit { }
        }
}

fun ch.threema.data.models.GroupModel.getProtoGroupIdentity() =
    groupIdentity.getProtoGroupIdentity()

fun GroupModelData.getProtoGroupIdentity() = groupIdentity.getProtoGroupIdentity()

fun GroupIdentity.getProtoGroupIdentity() = groupIdentity {
    creatorIdentity = this@getProtoGroupIdentity.creatorIdentity
    groupId = this@getProtoGroupIdentity.groupId
}

private fun GroupModelData.getProtoUserState() = when (userState) {
    GroupModel.UserState.MEMBER -> UserState.MEMBER
    GroupModel.UserState.LEFT -> UserState.LEFT
    GroupModel.UserState.KICKED -> UserState.KICKED
}

private fun GroupModelData.getProtoMembers() = identities {
    identities.addAll(this@getProtoMembers.otherMembers - groupIdentity.creatorIdentity)
}
