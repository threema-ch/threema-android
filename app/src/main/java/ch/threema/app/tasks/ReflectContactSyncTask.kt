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

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DeadlineListService
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.ContactUtil
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.Common
import ch.threema.protobuf.Common.DeltaImage
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.ContactKt
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.NotificationSoundPolicy
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel
import com.google.protobuf.kotlin.toByteString

abstract class ReflectContactSyncTask<TransactionResult, TaskResult>(
    protected val multiDeviceManager: MultiDeviceManager,
) {
    protected val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    /**
     * This is run as the precondition of the transaction that is used when reflecting the contact
     * sync with [reflectContactSync].
     */
    protected abstract val runPrecondition: () -> Boolean

    /**
     * This is run inside the transaction of the contact sync reflection in [reflectContactSync].
     */
    protected abstract val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> TransactionResult

    /**
     * This is run after the transaction has been successfully executed.
     */
    protected abstract val runAfterSuccessfulTransaction: (transactionResult: TransactionResult) -> TaskResult

    /**
     * The transaction ttl that is used for the transaction in [reflectContactSync].
     */
    protected open val transactionTTL: UInt = TRANSACTION_TTL_MAX

    /**
     * Reflect the contact sync. Note that this creates a transaction with [transactionTTL] and runs
     * [runPrecondition] as precondition of it. Inside the transaction [runInsideTransaction] is
     * run.
     *
     * @throws IllegalStateException if multi device is not active
     */
    protected suspend fun reflectContactSync(handle: ActiveTaskCodec): TaskResult {
        if (!multiDeviceManager.isMultiDeviceActive) {
            throw IllegalStateException("Multi device is not active and a contact sync must not be reflected")
        }

        val transactionResult = handle.createTransaction(
            mdProperties.keys, MdD2D.TransactionScope.Scope.CONTACT_SYNC, transactionTTL
        ) {
            runPrecondition()
        }.execute {
            runInsideTransaction(handle)
        }

        return runAfterSuccessfulTransaction(transactionResult)
    }

    protected fun ContactModelData.toFullSyncContact(
        conversationService: ConversationService? = null,
        hiddenChatListService: DeadlineListService? = null,
        mutedChatsService: DeadlineListService? = null,
        ringtoneService: RingtoneService? = null,
        contactDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
        userDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
    ): MdD2DSync.Contact {
        val data = this
        return contact {
            identity = data.identity
            publicKey = data.publicKey.toByteString()
            createdAt = data.createdAt.time
            firstName = data.firstName
            lastName = data.lastName
            data.nickname?.let { nickname = it }
            verificationLevel = data.getSyncVerificationLevel()
            workVerificationLevel = data.getSyncWorkVerificationLevel()
            identityType = data.getSyncIdentityType()
            acquaintanceLevel = data.getSyncAcquaintanceLevel()
            activityState = data.getSyncActivityState()
            featureMask = data.featureMask.toLong()
            syncState = data.getSyncSyncState()
            readReceiptPolicyOverride = data.getSyncReadReceiptPolicyOverride()
            typingIndicatorPolicyOverride = data.getSyncTypingIndicatorPolicyOverride()
            notificationTriggerPolicyOverride =
                data.getSyncNotificationTriggerPolicyOverride(mutedChatsService)
            notificationSoundPolicyOverride =
                getSyncNotificationSoundPolicyOverride(ringtoneService)
            contactDefinedProfilePicture = contactDefinedProfilePictureUpload.toDeltaImage()
            userDefinedProfilePicture = userDefinedProfilePictureUpload.toDeltaImage()
            conversationCategory = data.getSyncConversationCategory(hiddenChatListService)
            conversationVisibility = data.getSyncConversationVisibility(conversationService)
        }
    }

    private fun ContactModelData.getSyncVerificationLevel(): MdD2DSync.Contact.VerificationLevel =
        when (this.verificationLevel) {
            VerificationLevel.FULLY_VERIFIED -> MdD2DSync.Contact.VerificationLevel.FULLY_VERIFIED
            VerificationLevel.SERVER_VERIFIED -> MdD2DSync.Contact.VerificationLevel.SERVER_VERIFIED
            VerificationLevel.UNVERIFIED -> MdD2DSync.Contact.VerificationLevel.UNVERIFIED
        }

    private fun ContactModelData.getSyncWorkVerificationLevel(): MdD2DSync.Contact.WorkVerificationLevel =
        when (this.workVerificationLevel) {
            WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> MdD2DSync.Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            WorkVerificationLevel.NONE -> MdD2DSync.Contact.WorkVerificationLevel.NONE
        }

    private fun ContactModelData.getSyncIdentityType(): MdD2DSync.Contact.IdentityType =
        when (this.identityType) {
            IdentityType.NORMAL -> MdD2DSync.Contact.IdentityType.REGULAR
            IdentityType.WORK -> MdD2DSync.Contact.IdentityType.WORK
        }

    private fun ContactModelData.getSyncAcquaintanceLevel(): MdD2DSync.Contact.AcquaintanceLevel =
        when (this.acquaintanceLevel) {
            ContactModel.AcquaintanceLevel.DIRECT -> MdD2DSync.Contact.AcquaintanceLevel.DIRECT
            ContactModel.AcquaintanceLevel.GROUP -> MdD2DSync.Contact.AcquaintanceLevel.GROUP_OR_DELETED
        }

    private fun ContactModelData.getSyncActivityState(): MdD2DSync.Contact.ActivityState =
        when (this.activityState) {
            IdentityState.ACTIVE -> MdD2DSync.Contact.ActivityState.ACTIVE
            IdentityState.INACTIVE -> MdD2DSync.Contact.ActivityState.INACTIVE
            IdentityState.INVALID -> MdD2DSync.Contact.ActivityState.INVALID
        }

    private fun ContactModelData.getSyncSyncState(): MdD2DSync.Contact.SyncState =
        // TODO(ANDR-2327): Consolidate this mechanism
        if (androidContactLookupKey != null) {
            MdD2DSync.Contact.SyncState.IMPORTED
        } else if (lastName.isBlank() && firstName.isBlank()) {
            MdD2DSync.Contact.SyncState.INITIAL
        } else {
            MdD2DSync.Contact.SyncState.CUSTOM
        }

    private fun ContactModelData.getSyncReadReceiptPolicyOverride(): MdD2DSync.Contact.ReadReceiptPolicyOverride =
        ContactKt.readReceiptPolicyOverride {
            when (readReceiptPolicy) {
                ReadReceiptPolicy.DEFAULT -> default = unit { }
                ReadReceiptPolicy.SEND -> policy = MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
                ReadReceiptPolicy.DONT_SEND -> policy =
                    MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
        }

    private fun ContactModelData.getSyncTypingIndicatorPolicyOverride(): MdD2DSync.Contact.TypingIndicatorPolicyOverride =
        ContactKt.typingIndicatorPolicyOverride {
            when (typingIndicatorPolicy) {
                TypingIndicatorPolicy.DEFAULT -> default = unit { }

                TypingIndicatorPolicy.SEND ->
                    policy = MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR

                TypingIndicatorPolicy.DONT_SEND ->
                    policy = MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
        }

    // TODO(ANDR-2998): Use notification trigger policy override from new contact model
    private fun ContactModelData.getSyncNotificationTriggerPolicyOverride(
        mutedChatsService: DeadlineListService?,
    ): MdD2DSync.Contact.NotificationTriggerPolicyOverride {
        return if (mutedChatsService != null) {
            ContactKt.notificationTriggerPolicyOverride {
                val mutedUntil =
                    mutedChatsService.getDeadline(ContactUtil.getUniqueIdString(identity))
                if (mutedUntil == DeadlineListService.DEADLINE_INDEFINITE || mutedUntil > 0) {
                    policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                        policy =
                            MdD2DSync.Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                        if (mutedUntil > 0) {
                            expiresAt = mutedUntil
                        }
                    }
                } else {
                    default = unit { }
                }
            }
        } else {
            ContactKt.notificationTriggerPolicyOverride {
                default = unit { }
            }
        }
    }

    // TODO(ANDR-2998): Use notification sound policy override from new contact model
    private fun ContactModelData.getSyncNotificationSoundPolicyOverride(
        ringtoneService: RingtoneService?,
    ): MdD2DSync.Contact.NotificationSoundPolicyOverride {
        return if (ringtoneService != null) {
            ContactKt.notificationSoundPolicyOverride {
                if (ringtoneService.isSilent(ContactUtil.getUniqueIdString(identity), false)) {
                    policy = NotificationSoundPolicy.MUTED
                } else {
                    default = unit { }
                }
            }
        } else {
            ContactKt.notificationSoundPolicyOverride {
                default = unit { }
            }
        }
    }

    // TODO(ANDR-3034): Use conversation category from the new contact model
    private fun ContactModelData.getSyncConversationCategory(
        hiddenChatListService: DeadlineListService?,
    ): MdD2DSync.ConversationCategory {
        return if (hiddenChatListService != null) {
            if (hiddenChatListService.has(ContactUtil.getUniqueIdString(identity))) {
                MdD2DSync.ConversationCategory.PROTECTED
            } else {
                MdD2DSync.ConversationCategory.DEFAULT
            }
        } else {
            MdD2DSync.ConversationCategory.DEFAULT
        }
    }

    private fun ContactModelData.getSyncConversationVisibility(
        conversationService: ConversationService?,
    ): MdD2DSync.ConversationVisibility {
        // TODO(ANDR-3035): Use conversation visibility from the new contact model
        if (conversationService != null) {
            // In case there is a conversation with the contact: Check the pin tag, otherwise it is normal
            conversationService.getAll(true).find { it.contact?.identity == identity }?.let {
                return if (it.isPinTagged) {
                    MdD2DSync.ConversationVisibility.PINNED
                } else {
                    MdD2DSync.ConversationVisibility.NORMAL
                }
            }

            // In case there is an archived conversation with the contact: The visibility is archived.
            conversationService.getArchived(null).find { it.contact?.identity == identity }?.let {
                return MdD2DSync.ConversationVisibility.PINNED
            }
        }

        // In case there is no conversation with the contact: The visibility is normal.
        return MdD2DSync.ConversationVisibility.NORMAL
    }

    private fun ProfilePictureUploadData?.toDeltaImage(): DeltaImage {
        return if (this == null) {
            deltaImage { removed = unit { } }
        } else {
            val uploadData = this
            deltaImage {
                updated = image {
                    type = Common.Image.Type.JPEG
                    blob = blob {
                        id = uploadData.blobId.toByteString()
                        nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                        key = uploadData.encryptionKey.toByteString()
                        uploadedAt = uploadData.uploadedAt
                    }
                }
            }
        }
    }
}
