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

package ch.threema.app.groupflows

import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.GroupService
import ch.threema.app.tasks.ReflectGroupSyncDeletePrecondition
import ch.threema.app.tasks.ReflectGroupSyncDeleteTask
import ch.threema.app.tasks.ReflectionFailed
import ch.threema.app.tasks.ReflectionPreconditionFailed
import ch.threema.app.tasks.ReflectionSuccess
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TriggerSource
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("RemoveGroupFlow")

private const val DIALOG_TAG_LEAVE_GROUP = "groupLeave"

class RemoveGroupFlow(
    private val fragmentManager: FragmentManager?,
    private val groupModel: GroupModel,
    private val groupService: GroupService,
    private val groupModelRepository: GroupModelRepository,
    private val multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
    private val taskManager: TaskManager,
) : BackgroundTask<Boolean> {
    override fun runBefore() {
        fragmentManager?.let {
            GenericProgressDialog.newInstance(R.string.updating_group, R.string.please_wait)
                .show(it, DIALOG_TAG_LEAVE_GROUP)
        }
    }

    override fun runInBackground(): Boolean {
        logger.info("Running remove group flow")

        if (groupModel.data.value?.isMember == true) {
            logger.error("Cannot remove group where the user is still a member")
            return false
        }

        // First, reflect the deletion (if md is active)
        if (multiDeviceManager.isMultiDeviceActive) {
            when (val reflectionResult = reflect()) {
                is ReflectionSuccess -> {
                    logger.info("Reflected group delete successfully")
                }

                is ReflectionFailed -> {
                    logger.error("Reflection failed", reflectionResult.exception)
                    return false
                }

                is ReflectionPreconditionFailed -> {
                    logger.error(
                        "Reflection failed due to precondition",
                        reflectionResult.transactionException,
                    )
                    return false
                }
            }
        }

        // Then persist the changes locally
        persist()

        // As the group has already been left or disbanded, there is no need to send any csp
        // messages

        return true
    }

    override fun runAfter(result: Boolean) {
        fragmentManager?.let {
            DialogUtil.dismissDialog(
                it,
                DIALOG_TAG_LEAVE_GROUP,
                true,
            )
        }
    }

    private fun reflect() = runBlocking {
        taskManager.schedule(
            ReflectGroupSyncDeleteTask(
                groupModel,
                ReflectGroupSyncDeletePrecondition.USER_IS_NO_MEMBER,
                nonceFactory,
                multiDeviceManager,
            ),
        ).await()
    }

    private fun persist() {
        groupService.removeGroupBelongings(groupModel, TriggerSource.LOCAL)
        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
    }
}
