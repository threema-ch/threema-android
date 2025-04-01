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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.UserService
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.Common
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel
import com.google.protobuf.kotlin.toByteString
import kotlinx.serialization.Serializable

/**
 * This task just reflects the currently stored user profile picture. This is a simple mechanism
 * that is not optimal when other devices also support user profile picture changes as in case of a
 * sync race condition a reflected profile picture may be reflected back. The advantage of this
 * approach is its simplicity and the fact that this task can be scheduled and run without causing
 * any damage.
 */
class ReflectUserProfilePictureSyncTask(
    private val userService: UserService,
    private val nonceFactory: NonceFactory,
    private val multiDeviceManager: MultiDeviceManager,
) : ActiveTask<Unit>, PersistableTask {

    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    override val type = "ReflectUserProfilePictureSyncTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        check(multiDeviceManager.isMultiDeviceActive) {
            "Multi device is not active and a user profile picture must not be reflected"
        }

        val profilePictureUploadData = userService.uploadUserProfilePictureOrGetPreviousUploadData()

        val profilePictureUpdate = deltaImage {
            if (profilePictureUploadData.blobId.contentEquals(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)) {
                removed = unit { }
            } else {
                updated = image {
                    type = Common.Image.Type.JPEG
                    blob = blob {
                        id = profilePictureUploadData.blobId.toByteString()
                        nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                        key = profilePictureUploadData.encryptionKey.toByteString()
                        uploadedAt = profilePictureUploadData.uploadedAt
                    }
                }
            }
        }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = MdD2D.TransactionScope.Scope.USER_PROFILE_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            val encryptedEnvelopeResult = getEncryptedUserProfileSyncUpdate(
                userProfile = userProfile {
                    profilePicture = profilePictureUpdate
                },
                mdProperties,
            )

            handle.reflectAndAwaitAck(encryptedEnvelopeResult, true, nonceFactory)
        }
    }

    override fun serialize(): SerializableTaskData = ReflectUserProfilePictureSyncTaskData

    @Serializable
    data object ReflectUserProfilePictureSyncTaskData : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            ReflectUserProfilePictureSyncTask(
                serviceManager.userService,
                serviceManager.nonceFactory,
                serviceManager.multiDeviceManager,
            )
    }
}
