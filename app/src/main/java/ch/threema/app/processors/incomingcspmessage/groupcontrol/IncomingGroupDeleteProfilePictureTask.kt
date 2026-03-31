package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingGroupDeleteProfilePictureTask")

class IncomingGroupDeleteProfilePictureTask(
    message: GroupDeleteProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupDeleteProfilePictureMessage>(
    message,
    triggerSource,
    serviceManager,
) {
    private val fileService by lazy { serviceManager.fileService }
    private val groupService by lazy { serviceManager.groupService }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val preferenceService by lazy { serviceManager.preferenceService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.info("Processing incoming delete-profile-picture message for group with id {}", message.apiGroupId)

        // Run the common group receive steps
        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
        if (groupModel == null) {
            logger.warn("Discarding group delete profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // If the group does not have a profile picture, discard this message
        if (!fileService.hasGroupProfilePicture(groupModel)) {
            logger.info("Discarding this message as group has no profile picture")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult =
                ReflectGroupSyncUpdateImmediateTask.ReflectGroupDeleteProfilePicture(
                    groupModel.groupIdentity,
                ).reflect(handle)

            when (reflectionResult) {
                is ReflectionResult.Success -> logger.info("Reflected removed group profile picture")
                is ReflectionResult.Failed -> {
                    logger.error(
                        "Could not reflect removed group profile picture",
                        reflectionResult.exception,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.error(
                        "Group sync race occurred: Profile picture could not be removed",
                        reflectionResult.transactionException,
                    )
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }
            }
        }

        fileService.removeGroupProfilePicture(groupModel)

        ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }

        ShortcutUtil.updateShareTargetShortcut(
            groupService.createReceiver(groupModel),
            preferenceService.getContactNameFormat(),
        )

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group delete profile picture from sync")
        return ReceiveStepsResult.DISCARD
    }
}
