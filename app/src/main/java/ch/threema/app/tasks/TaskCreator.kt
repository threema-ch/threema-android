package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

private val logger = getThreemaLogger("TaskCreator")

@SessionScoped
open class TaskCreator(private val serviceManager: ServiceManager) {
    fun scheduleProfilePictureSendTaskAsync(toIdentity: IdentityString): Deferred<Unit> =
        scheduleTaskAsync {
            SendProfilePictureTask(toIdentity)
        }

    fun scheduleDeleteAndTerminateFSSessionsTaskAsync(
        contact: Contact,
        cause: Cause,
    ): Deferred<Unit> =
        scheduleTaskAsync {
            DeleteAndTerminateFSSessionsTask(
                identity = contact.identity,
                cause = cause,
            )
        }

    fun scheduleSendPushTokenTask(token: String, type: Int): Deferred<Unit> =
        scheduleTaskAsync { SendPushTokenTask(token, type) }

    fun scheduleDeviceLinkingPartOneTask(
        deviceLinkingController: DeviceLinkingController,
        deviceJoinOfferUri: String,
        taskCancelledSignal: CompletableDeferred<Unit>,
    ): Deferred<Result<RendezvousConnection>> = scheduleTaskAsync {
        DeviceLinkingPartOneTask(
            deviceLinkingController = deviceLinkingController,
            deviceJoinOfferUri = deviceJoinOfferUri,
            serviceManager = serviceManager,
            taskCancelledSignal = taskCancelledSignal,
        )
    }

    fun scheduleDeviceLinkingPartTwoTask(
        rendezvousConnection: RendezvousConnection,
        serviceManager: ServiceManager,
        taskCancelledSignal: Deferred<Unit>,
    ): Deferred<Result<Unit>> = scheduleTaskAsync {
        DeviceLinkingPartTwoTask(
            rendezvousConnection = rendezvousConnection,
            serviceManager = serviceManager,
            taskCancelledSignal = taskCancelledSignal,
        )
    }

    fun scheduleGetLinkedDevicesTask(): Deferred<GetLinkedDevicesTask.LinkedDevicesResult> = scheduleTaskAsync {
        GetLinkedDevicesTask(serviceManager)
    }

    fun scheduleDeactivateMultiDeviceIfAloneTask(): Deferred<Unit> = scheduleTaskAsync {
        DeactivateMultiDeviceIfAloneTask()
    }

    fun scheduleUserDefinedProfilePictureUpdate(identity: IdentityString) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate(
            identity = identity,
        )
    }

    fun scheduleReflectUserProfilePictureTask() = scheduleTaskAsync {
        ReflectUserProfilePictureSyncTask()
    }

    fun scheduleReflectUserProfileIdentityLinksTask() = scheduleTaskAsync {
        ReflectUserProfileIdentityLinksTask()
    }

    fun scheduleReflectBlockedIdentitiesTask() = scheduleTaskAsync {
        ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate()
    }

    fun scheduleReflectExcludeFromSyncIdentitiesTask() = scheduleTaskAsync {
        ReflectSettingsSyncTask.ReflectExcludeFromSyncIdentitiesSyncUpdate()
    }

    fun scheduleReflectContactConversationCategory(contactIdentity: IdentityString, isPrivateChat: Boolean) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate(
            contactIdentity = contactIdentity,
            isPrivateChat = isPrivateChat,
        )
    }

    fun scheduleReflectGroupConversationCategory(groupDatabaseId: Long, isPrivateChat: Boolean): Deferred<Unit> {
        val groupModel = serviceManager.modelRepositories.groups.getByLocalGroupDbId(groupDatabaseId)
        if (groupModel == null) {
            logger.error("Group model with id {} could not be found. Cannot reflect conversation category.", groupDatabaseId)
            return CompletableDeferred<Unit>().also {
                it.completeExceptionally(IllegalArgumentException("Could not find group with id $groupDatabaseId"))
            }
        }

        return scheduleTaskAsync {
            ReflectGroupSyncUpdateTask.ReflectGroupConversationCategoryUpdateTask(
                groupIdentity = groupModel.groupIdentity,
                isPrivateChat = isPrivateChat,
            )
        }
    }

    fun scheduleReflectConversationVisibilityPinned(identity: IdentityString, isPinned: Boolean) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate(
            isPinned = isPinned,
            contactIdentity = identity,
        )
    }

    fun scheduleReflectGroupConversationVisibilityPinned(localGroupDatabaseId: Long, isPinned: Boolean) {
        val groupModel = serviceManager.modelRepositories.groups.getByLocalGroupDbId(localGroupDatabaseId)
        if (groupModel == null) {
            logger.error("Could not schedule conversation visibility pinned task as the group model could not be found")
            return
        }

        scheduleTaskAsync {
            ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityPinnedUpdate(
                isPinned = isPinned,
                groupIdentity = groupModel.groupIdentity,
            )
        }
    }

    fun scheduleDeactivateMultiDeviceTask() =
        scheduleTaskAsync {
            DeactivateMultiDeviceTask()
        }

    fun scheduleReflectMultipleSettingsSyncUpdateTask(settingsCreators: List<(Settings.Builder) -> Unit>): Deferred<Unit> = scheduleTaskAsync {
        ReflectSettingsSyncTask.ReflectMultipleSettingsSyncUpdate(
            settingsCreators = settingsCreators,
        )
    }

    fun scheduleReflectUserProfileShareWithPolicySyncTask(
        profilePictureSharePolicy: ProfilePictureSharePolicy.Policy,
    ): Deferred<Unit> = scheduleTaskAsync {
        ReflectUserProfileShareWithPolicySyncTask(
            newPolicy = profilePictureSharePolicy,
        )
    }

    fun scheduleReflectUserProfileShareWithAllowListSyncTask(allowedIdentities: Set<IdentityString>): Deferred<Unit> = scheduleTaskAsync {
        ReflectUserProfileShareWithAllowListSyncTask(
            allowedIdentities = allowedIdentities,
        )
    }

    private fun <R> scheduleTaskAsync(createTask: () -> Task<R, TaskCodec>): Deferred<R> {
        return serviceManager.taskManager.schedule(createTask())
    }
}
