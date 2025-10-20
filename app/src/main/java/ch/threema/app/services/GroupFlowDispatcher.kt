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

package ch.threema.app.services

import android.content.Context
import ch.threema.app.groupflows.CreateGroupFlow
import ch.threema.app.groupflows.DisbandGroupFlow
import ch.threema.app.groupflows.GroupChanges
import ch.threema.app.groupflows.GroupCreateProperties
import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.groupflows.GroupResyncFlow
import ch.threema.app.groupflows.LeaveGroupFlow
import ch.threema.app.groupflows.RemoveGroupFlow
import ch.threema.app.groupflows.UpdateGroupFlow
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseService
import kotlinx.coroutines.Deferred

/**
 * The group flow dispatcher acts as dispatcher and executor of different group flows. Group flows
 * are actions a user wants to perform on an existing or not yet existing group. These actions are
 * always initiated "from local", meaning on the android device or the connected webclient. As
 * these actions require reflection and sending csp messages, the flows are executed on a background
 * thread and a deferred is returned.
 */
class GroupFlowDispatcher(
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val groupCallManager: GroupCallManager,
    private val userService: UserService,
    private val contactStore: ContactStore,
    private val identityStore: IdentityStore,
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val nonceFactory: NonceFactory,
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val apiService: ApiService,
    private val apiConnector: APIConnector,
    private val fileService: FileService,
    private val databaseService: DatabaseService,
    private val taskManager: TaskManager,
    private val connection: ServerConnection,
) {
    private val outgoingCspMessageServices by lazy {
        OutgoingCspMessageServices(
            forwardSecurityMessageProcessor,
            identityStore,
            userService,
            contactStore,
            contactService,
            contactModelRepository,
            groupService,
            nonceFactory,
            blockedIdentitiesService,
            preferenceService,
            multiDeviceManager,
        )
    }

    /**
     * This executor is used to run the group flows.
     */
    private val backgroundExecutor by lazy { BackgroundExecutor() }

    /**
     * Create a new group from local.
     */
    fun runCreateGroupFlow(
        context: Context,
        groupCreateProperties: GroupCreateProperties,
    ): Deferred<GroupFlowResult> = backgroundExecutor.executeDeferred(
        CreateGroupFlow(
            context,
            groupCreateProperties,
            groupModelRepository,
            outgoingCspMessageServices,
            groupCallManager,
            apiService,
            fileService,
            taskManager,
            connection,
        ),
    )

    /**
     * Update an existing group from local.
     */
    fun runUpdateGroupFlow(
        groupChanges: GroupChanges,
        groupModel: GroupModel,
    ) = backgroundExecutor.executeDeferred(
        UpdateGroupFlow(
            groupChanges,
            groupModel,
            groupModelRepository,
            groupCallManager,
            outgoingCspMessageServices,
            apiService,
            fileService,
            taskManager,
            connection,
        ),
    )

    /**
     * Leave an existing group from local.
     */
    fun runLeaveGroupFlow(
        intent: GroupLeaveIntent,
        groupModel: GroupModel,
    ) = backgroundExecutor.executeDeferred(
        LeaveGroupFlow(
            intent,
            groupModel,
            groupModelRepository,
            groupCallManager,
            apiConnector,
            outgoingCspMessageServices,
            taskManager,
            connection,
        ),
    )

    /**
     * Disband an existing group from local.
     */
    fun runDisbandGroupFlow(
        intent: GroupDisbandIntent,
        groupModel: GroupModel,
    ) = backgroundExecutor.executeDeferred(
        DisbandGroupFlow(
            intent,
            groupModel,
            groupModelRepository,
            groupCallManager,
            apiConnector,
            outgoingCspMessageServices,
            taskManager,
            connection,
        ),
    )

    /**
     * Remove an existing group from local.
     */
    fun runRemoveGroupFlow(
        groupModel: GroupModel,
    ) = backgroundExecutor.executeDeferred(
        RemoveGroupFlow(
            groupModel,
            groupService,
            groupModelRepository,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            connection,
        ),
    )

    /**
     * Resync the given group. Note that this flow must only be used if the user is the creator of
     * the group and the group is not disbanded.
     */
    fun runGroupResyncFlow(
        groupModel: GroupModel,
    ) = backgroundExecutor.executeDeferred(
        GroupResyncFlow(
            groupModel,
            taskManager,
            contactModelRepository,
            contactStore,
            apiConnector,
            userService,
            apiService,
            fileService,
            groupCallManager,
            databaseService,
            outgoingCspMessageServices,
        ),
    )
}
