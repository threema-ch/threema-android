package ch.threema.app.tasks

import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefinite
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedUntil
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride.NotMuted
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.common.Image
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt
import ch.threema.protobuf.d2d.sync.ContactKt.deprecatedNotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.storage.models.ContactModel
import com.google.protobuf.kotlin.toByteString

abstract class ReflectContactSyncTask<TransactionResult, TaskResult>() : ReflectSyncTask<TransactionResult, TaskResult>(
    transactionScope = TransactionScope.Scope.CONTACT_SYNC,
) {
    protected suspend fun reflectContactSync(handle: ActiveTaskCodec): TaskResult {
        return reflectSync(handle)
    }
}

fun ContactModelData.toFullSyncContact(
    conversationService: ConversationService? = null,
    conversationCategoryService: ConversationCategoryService? = null,
    contactDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
    userDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
): Contact {
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
        notificationTriggerPolicyOverride = data.getSyncNotificationTriggerPolicyOverride()
        deprecatedNotificationSoundPolicyOverride = deprecatedNotificationSoundPolicyOverride {
            default = unit {}
        }
        contactDefinedProfilePicture = contactDefinedProfilePictureUpload.toDeltaImage()
        userDefinedProfilePicture = userDefinedProfilePictureUpload.toDeltaImage()
        conversationCategory = data.getSyncConversationCategory(conversationCategoryService)
        conversationVisibility = data.getSyncConversationVisibility(conversationService)

        if (ConfigUtils.supportsAvailabilityStatus() && data.availabilityStatus != AvailabilityStatus.None) {
            workAvailabilityStatus = data.availabilityStatus.toProtocolModel()
        }
    }
}

private fun ContactModelData.getSyncVerificationLevel(): Contact.VerificationLevel =
    when (this.verificationLevel) {
        VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
        VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
        VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
    }

private fun ContactModelData.getSyncWorkVerificationLevel(): Contact.WorkVerificationLevel =
    when (this.workVerificationLevel) {
        WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
        WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
    }

private fun ContactModelData.getSyncIdentityType(): Contact.IdentityType =
    when (this.identityType) {
        IdentityType.NORMAL -> Contact.IdentityType.REGULAR
        IdentityType.WORK -> Contact.IdentityType.WORK
    }

private fun ContactModelData.getSyncAcquaintanceLevel(): Contact.AcquaintanceLevel =
    when (this.acquaintanceLevel) {
        ContactModel.AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
        ContactModel.AcquaintanceLevel.GROUP -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
    }

private fun ContactModelData.getSyncActivityState(): Contact.ActivityState =
    when (this.activityState) {
        IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
        IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
        IdentityState.INVALID -> Contact.ActivityState.INVALID
    }

private fun ContactModelData.getSyncSyncState(): Contact.SyncState =
    // TODO(ANDR-2327): Consolidate this mechanism
    if (androidContactLookupInfo != null) {
        Contact.SyncState.IMPORTED
    } else if (lastName.isBlank() && firstName.isBlank()) {
        Contact.SyncState.INITIAL
    } else {
        Contact.SyncState.CUSTOM
    }

private fun ContactModelData.getSyncReadReceiptPolicyOverride(): Contact.ReadReceiptPolicyOverride =
    ContactKt.readReceiptPolicyOverride {
        when (readReceiptPolicy) {
            ReadReceiptPolicy.DEFAULT -> default = unit { }
            ReadReceiptPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.SEND_READ_RECEIPT
            ReadReceiptPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
        }
    }

private fun ContactModelData.getSyncTypingIndicatorPolicyOverride(): Contact.TypingIndicatorPolicyOverride =
    ContactKt.typingIndicatorPolicyOverride {
        when (typingIndicatorPolicy) {
            TypingIndicatorPolicy.DEFAULT -> default = unit { }
            TypingIndicatorPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR
            TypingIndicatorPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
        }
    }

private fun ContactModelData.getSyncNotificationTriggerPolicyOverride(): Contact.NotificationTriggerPolicyOverride {
    val notificationTriggerPolicyOverride = NotificationTriggerPolicyOverride.fromDbValueContact(this.notificationTriggerPolicyOverride)
    return ContactKt.notificationTriggerPolicyOverride {
        when (notificationTriggerPolicyOverride) {
            NotMuted -> default = unit {}

            MutedIndefinite -> policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                policy = Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
            }

            MutedIndefiniteExceptMentions -> throw IllegalStateException(
                "Contact receivers can never have this setting",
            )

            is MutedUntil -> policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                policy = Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                expiresAt = notificationTriggerPolicyOverride.utcMillis
            }
        }
    }
}

// TODO(ANDR-3034): Use conversation category from the new contact model
private fun ContactModelData.getSyncConversationCategory(
    conversationCategoryService: ConversationCategoryService?,
): ConversationCategory {
    return conversationCategoryService
        ?.getConversationCategory(ContactUtil.getUniqueIdString(identity))
        ?: ConversationCategory.DEFAULT
}

private fun ContactModelData.getSyncConversationVisibility(
    conversationService: ConversationService?,
): ConversationVisibility {
    // TODO(ANDR-3035): Use conversation visibility from the new contact model
    if (conversationService == null) {
        return ConversationVisibility.NORMAL
    }

    // Check whether the contact is archived. In case it is archived, it can't be pinned.
    if (isArchived) {
        return ConversationVisibility.ARCHIVED
    }

    // In case there is a conversation with the contact: Check the pin tag, otherwise it is normal
    conversationService.getAll(true).find { it.contact?.identity == identity }?.let {
        return if (it.isPinTagged) {
            ConversationVisibility.PINNED
        } else {
            ConversationVisibility.NORMAL
        }
    }

    // In case there is no conversation with the contact: The visibility is normal.
    return ConversationVisibility.NORMAL
}

private fun ProfilePictureUploadData?.toDeltaImage(): DeltaImage {
    return if (this == null) {
        deltaImage { removed = unit { } }
    } else {
        val uploadData = this
        deltaImage {
            updated = image {
                type = Image.Type.JPEG
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
