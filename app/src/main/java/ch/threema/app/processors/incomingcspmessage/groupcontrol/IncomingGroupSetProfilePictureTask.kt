package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.ExifInterface
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.contentEquals
import ch.threema.data.models.GroupModel
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingGroupSetProfilePictureTask")

class IncomingGroupSetProfilePictureTask(
    message: GroupSetProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSetProfilePictureMessage>(
    message,
    triggerSource,
    serviceManager,
) {
    private val fileService by lazy { serviceManager.fileService }
    private val apiService by lazy { serviceManager.apiService }
    private val groupService by lazy { serviceManager.groupService }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val preferenceService by lazy { serviceManager.preferenceService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.info("Processing incoming set-profile-picture message for group with id {}", message.apiGroupId)

        // Run the common group receive steps
        val group = runCommonGroupReceiveSteps(message, handle, serviceManager)
        if (group == null) {
            logger.warn("Discarding group set profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // Download the picture from the blob server but do not request the blob to be removed
        val blobLoader = apiService.createLoader(message.blobId)
        // TODO(ANDR-2869): Correctly handle blob server faults
        val blob = blobLoader.load(
            // since its an incoming message, always use the public scope
            BlobScope.Public,
        ) ?: throw IllegalStateException("Profile picture blob is null")
        NaCl.symmetricDecryptDataInPlace(
            blob,
            message.encryptionKey,
            ProtocolDefines.GROUP_PHOTO_NONCE,
        )

        if (!ExifInterface.isJpegFormat(blob)) {
            logger.warn("Received a group profile picture that is not a jpeg")
        }

        // If the received picture is already the current profile picture, discard the message and
        // abort these steps.
        if (isCurrentProfilePicture(group, blob)) {
            logger.info("Received group picture is already the current picture")
            return ReceiveStepsResult.DISCARD
        }

        // Reflect the group update if md is active
        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult = reflectGroupPicture(
                groupModel = group,
                blobId = message.blobId,
                encryptionKey = message.encryptionKey,
                blobNonce = ProtocolDefines.GROUP_PHOTO_NONCE,
                profilePictureBlob = blob,
                handle = handle,
            )

            when (reflectionResult) {
                is ReflectionResult.Failed -> {
                    logger.error("Could not reflect group picture", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.warn("Group sync race occurred: User is no group member anymore")
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.Success -> logger.info("Successfully reflected group profile picture")
            }
        }

        // Store and apply the profile picture to the group
        return updateGroupPictureLocally(group, blob)
    }

    private fun isCurrentProfilePicture(group: GroupModel, newGroupPhoto: ByteArray?): Boolean {
        return fileService.getGroupProfilePictureStream(group.getDatabaseId()).contentEquals(newGroupPhoto)
    }

    private suspend fun reflectGroupPicture(
        groupModel: GroupModel,
        blobId: ByteArray,
        encryptionKey: ByteArray,
        blobNonce: ByteArray,
        profilePictureBlob: ByteArray,
        handle: ActiveTaskCodec,
    ) = ReflectGroupSyncUpdateImmediateTask.ReflectGroupSetProfilePicture(
        blobId = blobId,
        encryptionKey = encryptionKey,
        blobNonce = blobNonce,
        groupIdentity = groupModel.groupIdentity,
        profilePictureBlob = profilePictureBlob,
    ).reflect(handle)

    private fun updateGroupPictureLocally(group: GroupModel, blob: ByteArray): ReceiveStepsResult {
        fileService.writeGroupProfilePicture(group, blob)

        ListenerManager.groupListeners.handle { it.onUpdatePhoto(group.groupIdentity) }

        ShortcutUtil.updateShareTargetShortcut(
            groupService.createReceiver(group),
            preferenceService.getContactNameFormat(),
        )

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group set profile picture from sync")
        return ReceiveStepsResult.DISCARD
    }
}
