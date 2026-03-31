package ch.threema.app.processors.incomingcspmessage.groupcontrol

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContact
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date

private val logger = getThreemaLogger("IncomingGroupMessageUtils")

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    groupIdentity: GroupIdentity,
    fromIdentity: IdentityString,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? = runCommonGroupReceiveSteps(
    creatorIdentity = groupIdentity.creatorIdentity,
    groupId = GroupId(groupIdentity.groupId),
    fromIdentity = fromIdentity,
    handle = handle,
    serviceManager = serviceManager,
)

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    message: AbstractGroupMessage,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? = runCommonGroupReceiveSteps(
    creatorIdentity = message.groupCreator,
    groupId = message.apiGroupId,
    fromIdentity = message.fromIdentity,
    handle = handle,
    serviceManager = serviceManager,
)

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    creatorIdentity: IdentityString,
    groupId: GroupId,
    fromIdentity: IdentityString,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? {
    logger.info("Running common group receive steps for group with creator {} and id {} for message from {}", creatorIdentity, groupId, fromIdentity)
    val myIdentity = serviceManager.userService.identity
    val isCreator = myIdentity == creatorIdentity

    // Look up the group
    val group = serviceManager.modelRepositories.groups.getByCreatorIdentityAndId(creatorIdentity, groupId)
    val groupModelData = group?.data

    // If the group could not be found
    if (groupModelData == null) {
        logger.warn("Group not found.")
        if (!isCreator) {
            logger.info("Running group sync request steps because group couldn't be found.")
            runGroupSyncRequestSteps(
                groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong()),
                serviceManager = serviceManager,
                handle = handle,
            )
        }
        return null
    }

    // If the group is marked as left
    if (!groupModelData.isMember) {
        logger.warn("The user is not a member of the group. User state: {}", groupModelData.userState)
        val sender = getContactForIdentity(fromIdentity, serviceManager)
        if (isCreator) {
            logger.info("Sending group setup message without members.")
            handle.runBundledMessagesSendSteps(
                outgoingCspMessageHandle = OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupSetupMessage().apply {
                            members = emptyArray()
                        }
                    },
                ),
                services = serviceManager.getOutgoingCspMessageServices(),
                identityBlockedSteps = serviceManager.identityBlockedSteps,
            )
        } else {
            logger.info("Sending group leave message.")
            handle.runBundledMessagesSendSteps(
                outgoingCspMessageHandle = OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupLeaveMessage()
                    },
                ),
                services = serviceManager.getOutgoingCspMessageServices(),
                identityBlockedSteps = serviceManager.identityBlockedSteps,
            )
        }

        return null
    }

    if (!groupModelData.otherMembersAndCreator.contains(fromIdentity)) {
        logger.warn("The sender is not a member of the group.")
        val sender = getContactForIdentity(fromIdentity, serviceManager)
        if (isCreator) {
            logger.info("Sending a group setup message without members.")
            handle.runBundledMessagesSendSteps(
                outgoingCspMessageHandle = OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupSetupMessage().apply {
                            members = emptyArray()
                        }
                    },
                ),
                services = serviceManager.getOutgoingCspMessageServices(),
                identityBlockedSteps = serviceManager.identityBlockedSteps,
            )
        } else {
            logger.info("Running group sync request steps because the sender is no member.")
            runGroupSyncRequestSteps(
                groupIdentity = groupModelData.groupIdentity,
                serviceManager = serviceManager,
                handle = handle,
            )
        }

        return null
    }

    return group
}

private fun getContactForIdentity(identity: IdentityString, serviceManager: ServiceManager): BasicContact =
    serviceManager.contactStore.getCachedContact(identity)
        ?: serviceManager.modelRepositories.contacts.getByIdentity(identity)?.data?.toBasicContact()
        ?: throw IllegalStateException("Could not get cached contact for identity $identity")

suspend fun runGroupSyncRequestSteps(
    groupCreator: BasicContact,
    groupIdentity: GroupIdentity,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    if (groupIdentity.creatorIdentity == serviceManager.userService.identity) {
        logger.error("Cannot run the group sync request steps for a group where the user is the creator")
        return
    }

    if (groupCreator.identity != groupIdentity.creatorIdentity) {
        logger.error("Cannot run the group sync request with a contact that is not the group creator")
        return
    }

    // If the group has been recently resynced (less than one hour ago), abort these steps
    val syncFactory = serviceManager.databaseService.outgoingGroupSyncRequestLogModelFactory
    val syncModel = syncFactory[groupIdentity]

    val lastSyncedTimestamp = syncModel?.lastRequest?.time ?: 0
    val now = Date()
    val oneHourAgoTimestamp = now.time - DateUtils.HOUR_IN_MILLIS

    if (lastSyncedTimestamp > oneHourAgoTimestamp) {
        logger.info("Group has already been synced at {}", lastSyncedTimestamp)
        return
    }

    // Send a group sync request
    handle.runBundledMessagesSendSteps(
        outgoingCspMessageHandle = OutgoingCspMessageHandle(
            groupCreator,
            OutgoingCspGroupMessageCreator(
                MessageId.random(),
                Date(),
                groupIdentity,
            ) {
                GroupSyncRequestMessage()
            },
        ),
        services = serviceManager.getOutgoingCspMessageServices(),
        identityBlockedSteps = serviceManager.identityBlockedSteps,
    )

    // Mark the group as recently resynced
    syncFactory.createOrUpdate(groupIdentity, now)
}

/**
 * Fetches the group creator if not locally available and runs the group sync request steps.
 */
private suspend fun runGroupSyncRequestSteps(
    groupIdentity: GroupIdentity,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    runGroupSyncRequestSteps(
        groupCreator = groupIdentity.creatorIdentity.toBasicContact(
            serviceManager.modelRepositories.contacts,
            serviceManager.contactStore,
            serviceManager.apiConnector,
        ),
        groupIdentity = groupIdentity,
        serviceManager = serviceManager,
        handle = handle,
    )
}
