package ch.threema.app.tasks

import ch.threema.app.protocolsteps.ExpectedProfilePictureChange
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.TransactionScope.TransactionException
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.groupIdentity
import ch.threema.protobuf.common.identities
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.d2d.sync.GroupKt.deprecatedNotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.GroupKt.notificationTriggerPolicyOverride
import ch.threema.protobuf.d2d.sync.group
import com.google.protobuf.kotlin.toByteString

private val logger = getThreemaLogger("ReflectGroupSyncTask")

abstract class ReflectGroupSyncTask<TransactionResult, TaskResult> : ReflectSyncTask<TransactionResult, TaskResult>(
    transactionScope = TransactionScope.Scope.GROUP_SYNC,
) {
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
                    return ReflectionResult.PreconditionFailed(e)
                }

                is NetworkException -> throw e
                else -> return ReflectionResult.Failed(e)
            }
        }
        return ReflectionResult.Success(result)
    }
}

/**
 * The result of executing a [ReflectGroupSyncTask]. It may either be a [Success],
 * [PreconditionFailed], [Failed], or [MultiDeviceNotActive].
 */
sealed interface ReflectionResult<T> {
    /**
     * This result indicates that the reflection task has completed successfully.
     */
    data class Success<T>(val result: T) : ReflectionResult<T>

    /**
     * This result indicates that the reflection task has failed due to a failed precondition.
     */
    data class PreconditionFailed<T>(val transactionException: TransactionException) :
        ReflectionResult<T>

    /**
     * This result indicates that there was an undefined error while reflecting.
     */
    data class Failed<T>(val exception: Exception) : ReflectionResult<T>

    /**
     * This result indicates that multi device is not active and the reflection therefore could not be done.
     */
    class MultiDeviceNotActive<T> : ReflectionResult<T>
}

/**
 * Get a group sync from the given group model data.
 *
 * Note that the profile-picture is not set.
 *
 * For notification trigger policy override, the default option is chosen if not provided.
 */
fun GroupModelData.toGroupSync(
    isPrivateChat: Boolean?,
    conversationVisibility: ConversationVisibility?,
    notificationTriggerPolicyOverride: Group.NotificationTriggerPolicyOverride? = null,
    expectedProfilePictureChange: ExpectedProfilePictureChange? = null,
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
            ConversationCategory.PROTECTED
        } else {
            ConversationCategory.DEFAULT
        }
    }
    if (conversationVisibility != null) {
        this.conversationVisibility = conversationVisibility
    }

    when (expectedProfilePictureChange) {
        is ExpectedProfilePictureChange.Set.WithUpload -> {
            expectedProfilePictureChange.profilePictureUploadResultSuccess.let { uploadResult ->
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

        is ExpectedProfilePictureChange.Set.WithoutUpload -> {
            logger.error("When reflecting group profile picture, it must have been uploaded first")
        }

        is ExpectedProfilePictureChange.Remove -> {
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

    this.deprecatedNotificationSoundPolicyOverride = deprecatedNotificationSoundPolicyOverride {
        default = unit {}
    }
}

fun GroupModel.getProtoGroupIdentity() =
    groupIdentity.getProtoGroupIdentity()

fun GroupModelData.getProtoGroupIdentity() = groupIdentity.getProtoGroupIdentity()

fun GroupIdentity.getProtoGroupIdentity() = groupIdentity {
    creatorIdentity = this@getProtoGroupIdentity.creatorIdentity
    groupId = this@getProtoGroupIdentity.groupId
}

private fun GroupModelData.getProtoUserState() = when (userState) {
    UserState.MEMBER -> Group.UserState.MEMBER
    UserState.LEFT -> Group.UserState.LEFT
    UserState.KICKED -> Group.UserState.KICKED
}

private fun GroupModelData.getProtoMembers() = identities {
    identities.addAll(this@getProtoMembers.otherMembers)
}
