/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.groupcontrol.csp

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.MessageProcessor.ProcessingResult
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.DISCARD_MESSAGE
import ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT
import ch.threema.app.groupcontrol.IncomingGroupControlTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage
import ch.threema.storage.models.GroupModel
import com.neilalexander.jnacl.NaCl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSetProfilePictureTask")

class IncomingGroupSetProfilePictureTask(
    private val setProfilePictureMessage: GroupSetPhotoMessage,
    serviceManager: ServiceManager,
) : IncomingGroupControlTask {
    private val groupService = serviceManager.groupService
    private val fileService = serviceManager.fileService
    private val apiService = serviceManager.apiService
    private val avatarCacheService = serviceManager.avatarCacheService


    override fun run(): ProcessingResult {
        return executeTask()
    }

    override suspend fun invoke(scope: CoroutineScope): ProcessingResult {
        return withContext(scope.coroutineContext) { executeTask() }
    }

    private fun executeTask(): ProcessingResult {
        // 1. Run the common group receive steps
        val stepsResult = groupService.runCommonGroupReceiveSteps(setProfilePictureMessage)
        if (stepsResult == DISCARD_MESSAGE || stepsResult == SYNC_REQUEST_SENT) {
            return ProcessingResult.IGNORED
        }

        val group = groupService.getByGroupMessage(setProfilePictureMessage)
        if (group == null) {
            logger.warn("Discarding group set profile picture message because group could not be found")
            return ProcessingResult.IGNORED
        }

        // 2. Download the picture from the blob server but do not request the blob to be removed
        val blobLoader = apiService.createLoader(setProfilePictureMessage.blobId)
        val blob = blobLoader.load(false)
        NaCl.symmetricDecryptDataInplace(
            blob,
            setProfilePictureMessage.encryptionKey,
            ProtocolDefines.GROUP_PHOTO_NONCE
        )

        // 3. Store and apply the profile picture to the group (if it is different than the old one)
        if (hasDifferentGroupPhoto(group, blob)) {
            this.fileService.writeGroupAvatar(group, blob)

            this.avatarCacheService.reset(group)

            ListenerManager.groupListeners.handle { it.onUpdatePhoto(group) }
        }

        return ProcessingResult.SUCCESS
    }

    private fun hasDifferentGroupPhoto(group: GroupModel, newGroupPhoto: ByteArray?): Boolean {
        var differentGroupPhoto = true
        fileService.getGroupAvatarStream(group).use { existingAvatar ->
            if (newGroupPhoto != null && existingAvatar != null) {
                var index = 0
                var next: Int
                while (existingAvatar.read().also { next = it } != -1) {
                    if (next.toByte() != newGroupPhoto[index]) {
                        break
                    }
                    index++
                }
                differentGroupPhoto = index != newGroupPhoto.size
            }
        }
        return differentGroupPhoto
    }

}
