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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.contentEquals
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupModel
import com.neilalexander.jnacl.NaCl

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSetProfilePictureTask")

class IncomingGroupSetProfilePictureTask(
    message: GroupSetProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSetProfilePictureMessage>(message, triggerSource, serviceManager) {
    private val fileService by lazy { serviceManager.fileService }
    private val apiService by lazy { serviceManager.apiService }
    private val avatarCacheService by lazy { serviceManager.avatarCacheService }
    private val groupService by lazy { serviceManager.groupService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Run the common group receive steps
        val group = runCommonGroupReceiveSteps(message, handle, serviceManager)
        if (group == null) {
            logger.warn("Discarding group set profile picture message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Download the picture from the blob server but do not request the blob to be removed
        val blobLoader = apiService.createLoader(message.blobId)
        // TODO(ANDR-2869): Correctly handle blob server faults
        val blob = blobLoader.load(
            BlobScope.Public // since its an incoming message, always use the public scope
        ) ?: throw IllegalStateException("Profile picture blob is null")
        NaCl.symmetricDecryptDataInplace(
            blob,
            message.encryptionKey,
            ProtocolDefines.GROUP_PHOTO_NONCE
        )

        // 3. Store and apply the profile picture to the group (if it is different than the old one)
        if (hasDifferentGroupPhoto(group, blob)) {
            this.fileService.writeGroupAvatar(group, blob)

            this.avatarCacheService.reset(group)

            ListenerManager.groupListeners.handle { it.onUpdatePhoto(group) }

            ShortcutUtil.updateShareTargetShortcut(groupService.createReceiver(group))
        }

        return ReceiveStepsResult.SUCCESS
    }

    private fun hasDifferentGroupPhoto(group: GroupModel, newGroupPhoto: ByteArray?): Boolean {
        return !fileService.getGroupAvatarStream(group).contentEquals(newGroupPhoto)
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}
