package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.AbstractMessageModel

private val logger = getThreemaLogger("IncomingGroupReactionMessageTask")

class IncomingGroupReactionMessageTask(
    message: GroupReactionMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupReactionMessage>(message, triggerSource, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }
    private val groupService by lazy { serviceManager.groupService }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from remote id: {}", message.data.messageId)

        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
            ?: return ReceiveStepsResult.DISCARD

        return applyGroupReaction(groupModel)
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from sync id: {}", message.data.messageId)

        val groupModel = groupModelRepository.getByGroupIdentity(
            GroupIdentity(
                message.groupCreator,
                message.apiGroupId.toLong(),
            ),
        )

        if (groupModel == null) {
            logger.error("Received a reflected group reaction message in an unknown group")
            return ReceiveStepsResult.DISCARD
        }

        return applyGroupReaction(groupModel)
    }

    private fun applyGroupReaction(groupModel: GroupModel): ReceiveStepsResult {
        val receiver = groupService.createReceiver(groupModel) ?: run {
            logger.error("Could not create receiver for group")
            return ReceiveStepsResult.DISCARD
        }

        val targetMessage: AbstractMessageModel = runCommonReactionMessageReceiveSteps(
            reactionMessage = message,
            receiver = receiver,
            messageService = messageService,
        ) ?: return ReceiveStepsResult.DISCARD

        val emojiSequence: String = runCommonReactionMessageReceiveEmojiSequenceConversion(
            emojiSequenceBytes = message.data.emojiSequenceBytes,
        ) ?: return ReceiveStepsResult.DISCARD

        val savedSuccessfully: Boolean = messageService.saveEmojiReactionMessage(
            /* targetMessage = */
            targetMessage,
            /* senderIdentity = */
            message.fromIdentity,
            /* actionCase = */
            message.data.actionCase,
            /* emojiSequence = */
            emojiSequence,
        )
        return if (savedSuccessfully) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
