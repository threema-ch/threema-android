package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.Contact
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.app.protocolsteps.Init
import ch.threema.app.protocolsteps.Invalid
import ch.threema.app.protocolsteps.SpecialContact
import ch.threema.app.protocolsteps.UserContact
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.BasicContact
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingGroupLeaveTask")

class IncomingGroupLeaveTask(
    message: GroupLeaveMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupLeaveMessage>(message, triggerSource, serviceManager), KoinComponent {
    private val groupService: GroupService by inject()
    private val userService: UserService by inject()
    private val groupCallManager: GroupCallManager by inject()
    private val contactStore: ContactStore by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val identityBlockedSteps: IdentityBlockedSteps by inject()
    private val validContactsLookupSteps: ValidContactsLookupSteps by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.info("Processing incoming group-leave message for group with id {}", message.apiGroupId)

        val creatorIdentity = message.groupCreator
        val senderIdentity = message.fromIdentity

        // If the sender is the creator of the group, abort these steps
        if (senderIdentity == creatorIdentity) {
            logger.warn("Discarding group leave message from group creator")
            return ReceiveStepsResult.DISCARD
        }

        val groupIdentity = GroupIdentity(message.groupCreator, message.apiGroupId.toLong())

        // Look up the group
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity)

        val groupModelData = groupModel?.data

        if (groupModelData == null || !groupModelData.isMember) {
            if (userService.identity == creatorIdentity) {
                logger.info("The user is the creator of the group")
                return ReceiveStepsResult.DISCARD
            }
            if (identityBlockedSteps.run(identity = creatorIdentity).isBlocked()) {
                logger.info("Discarding group leave for unknown or left group with blocked creator")
                return ReceiveStepsResult.DISCARD
            }
            val creatorContact = fetchAndCacheCreatorContact(creatorIdentity) ?: run {
                logger.error("Could not get creator contact")
                return ReceiveStepsResult.DISCARD
            }
            runGroupSyncRequestSteps(
                creatorContact,
                groupIdentity,
                serviceManager,
                handle,
            )
            return ReceiveStepsResult.DISCARD
        }

        if (!groupModelData.otherMembers.contains(senderIdentity)) {
            logger.info("Sender is not a member")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult = ReflectGroupSyncUpdateImmediateTask.ReflectMemberLeft(
                leftMemberIdentity = senderIdentity,
                groupIdentity = groupModel.groupIdentity,
            ).reflect(handle)
            when (reflectionResult) {
                is ReflectionResult.PreconditionFailed -> {
                    logger.warn(
                        "Group sync race: Could not reflect contact leave",
                        reflectionResult.transactionException,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.Failed -> {
                    logger.error("Could not reflect contact leave", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.Success -> Unit
            }
        }

        // If the user and the sender are participating in a group call of this group, remove the
        // sender from the group call (handle it as if the sender left the call)
        groupCallManager.removeGroupCallParticipants(setOf(senderIdentity), groupModel)

        // Remove the member from the group
        groupModel.removeLeftMemberFromRemote(senderIdentity)

        groupService.resetCache(groupModel.getDatabaseId().toInt())

        // Run the rejected messages refresh steps for the group
        groupService.runRejectedMessagesRefreshSteps(groupModel)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group leave from sync")
        return ReceiveStepsResult.DISCARD
    }

    private fun fetchAndCacheCreatorContact(identity: IdentityString): BasicContact? =
        when (val contactOrInit = validContactsLookupSteps.run(identity)) {
            is Contact -> contactOrInit.contactModel.data!!.toBasicContact()
            is SpecialContact -> contactOrInit.cachedContact
            is Init -> contactOrInit.contactModelData.toBasicContact().also { basicContact ->
                contactStore.addCachedContact(basicContact)
            }

            is Invalid -> null
            is UserContact -> error("This can never happen")
        }
}
