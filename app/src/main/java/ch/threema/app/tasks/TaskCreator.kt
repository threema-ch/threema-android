/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

private val logger = LoggingUtil.getThreemaLogger("TaskCreator")

open class TaskCreator(private val serviceManager: ServiceManager) {
    fun scheduleProfilePictureSendTaskAsync(toIdentity: String): Deferred<Unit> =
        scheduleTaskAsync {
            SendProfilePictureTask(toIdentity, serviceManager)
        }

    fun scheduleDeleteAndTerminateFSSessionsTaskAsync(
        contact: Contact,
        cause: Cause,
    ): Deferred<Unit> =
        scheduleTaskAsync {
            DeleteAndTerminateFSSessionsTask(
                serviceManager.forwardSecurityMessageProcessor,
                contact,
                cause,
            )
        }

    fun scheduleSendPushTokenTask(token: String, type: Int): Deferred<Unit> =
        scheduleTaskAsync { SendPushTokenTask(token, type, serviceManager) }

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

    fun scheduleDropDeviceTask(deviceId: DeviceId): Deferred<Unit> = scheduleTaskAsync {
        DropDeviceTask(deviceId)
    }

    fun scheduleDeactivateMultiDeviceIfAloneTask(): Deferred<Unit> = scheduleTaskAsync {
        DeactivateMultiDeviceIfAloneTask(serviceManager)
    }

    fun scheduleUserDefinedProfilePictureUpdate(identity: String) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate(
            identity = identity,
            contactModelRepository = serviceManager.modelRepositories.contacts,
            multiDeviceManager = serviceManager.multiDeviceManager,
            nonceFactory = serviceManager.nonceFactory,
            fileService = serviceManager.fileService,
            symmetricEncryptionService = serviceManager.symmetricEncryptionService,
            apiService = serviceManager.apiService,
        )
    }

    fun scheduleReflectUserProfilePictureTask() = scheduleTaskAsync {
        ReflectUserProfilePictureSyncTask(
            serviceManager.userService,
            serviceManager.nonceFactory,
            serviceManager.multiDeviceManager,
        )
    }

    fun scheduleReflectUserProfileIdentityLinksTask() = scheduleTaskAsync {
        ReflectUserProfileIdentityLinksTask(
            userService = serviceManager.userService,
            nonceFactory = serviceManager.nonceFactory,
            multiDeviceManager = serviceManager.multiDeviceManager,
        )
    }

    fun scheduleReflectBlockedIdentitiesTask() = scheduleTaskAsync {
        ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate(
            multiDeviceManager = serviceManager.multiDeviceManager,
            nonceFactory = serviceManager.nonceFactory,
            blockedIdentitiesService = serviceManager.blockedIdentitiesService,
        )
    }

    fun scheduleReflectContactConversationCategory(contactIdentity: String, isPrivateChat: Boolean) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate(
            contactIdentity = contactIdentity,
            isPrivateChat = isPrivateChat,
            contactModelRepository = serviceManager.modelRepositories.contacts,
            multiDeviceManager = serviceManager.multiDeviceManager,
            nonceFactory = serviceManager.nonceFactory,
            conversationCategoryService = serviceManager.conversationCategoryService,
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
                groupModel = groupModel,
                isPrivateChat = isPrivateChat,
                nonceFactory = serviceManager.nonceFactory,
                conversationCategoryService = serviceManager.conversationCategoryService,
                multiDeviceManager = serviceManager.multiDeviceManager,
            )
        }
    }

    fun scheduleReflectConversationVisibilityPinned(identity: String, isPinned: Boolean) = scheduleTaskAsync {
        ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate(
            isPinned = isPinned,
            contactIdentity = identity,
            conversationTagService = serviceManager.conversationTagService,
            contactModelRepository = serviceManager.modelRepositories.contacts,
            multiDeviceManager = serviceManager.multiDeviceManager,
            nonceFactory = serviceManager.nonceFactory,
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
                groupModel = groupModel,
                conversationTagService = serviceManager.conversationTagService,
                multiDeviceManager = serviceManager.multiDeviceManager,
                nonceFactory = serviceManager.nonceFactory,
            )
        }
    }

    fun scheduleDeactivateMultiDeviceTask() {
        scheduleTaskAsync {
            DeactivateMultiDeviceTask(serviceManager)
        }
    }

    fun scheduleReflectMultipleSettingsSyncUpdateTask(settingsCreators: List<(Settings.Builder) -> Unit>): Deferred<Unit> = scheduleTaskAsync {
        ReflectSettingsSyncTask.ReflectMultipleSettingsSyncUpdate(
            multiDeviceManager = serviceManager.multiDeviceManager,
            nonceFactory = serviceManager.nonceFactory,
            settingsCreators = settingsCreators,
        )
    }

    fun scheduleReflectUserProfileShareWithPolicySyncTask(
        profilePictureSharePolicy: ProfilePictureSharePolicy.Policy,
    ): Deferred<Unit> = scheduleTaskAsync {
        ReflectUserProfileShareWithPolicySyncTask(
            newPolicy = profilePictureSharePolicy,
            serviceManager = serviceManager,
        )
    }

    fun scheduleReflectUserProfileShareWithAllowListSyncTask(allowedIdentities: Set<String>): Deferred<Unit> = scheduleTaskAsync {
        ReflectUserProfileShareWithAllowListSyncTask(
            allowedIdentities = allowedIdentities,
            serviceManager,
        )
    }

    private fun <R> scheduleTaskAsync(createTask: () -> Task<R, TaskCodec>): Deferred<R> {
        return serviceManager.taskManager.schedule(createTask())
    }
}
