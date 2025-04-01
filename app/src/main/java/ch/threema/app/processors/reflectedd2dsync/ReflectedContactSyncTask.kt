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

package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ConversationTagServiceImpl
import ch.threema.app.services.DeadlineListService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.contentEquals
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.protobuf.Common
import ch.threema.protobuf.Common.Blob
import ch.threema.protobuf.d2d.MdD2D.ContactSync
import ch.threema.protobuf.d2d.MdD2D.ContactSync.Create
import ch.threema.protobuf.d2d.MdD2D.ContactSync.Update
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy
import ch.threema.protobuf.d2d.sync.MdD2DSync.NotificationSoundPolicy
import ch.threema.protobuf.d2d.sync.contactDefinedProfilePictureOrNull
import ch.threema.protobuf.d2d.sync.userDefinedProfilePictureOrNull
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ConversationModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedContactSyncTask")

class ReflectedContactSyncTask(
    private val contactSync: ContactSync,
    private val contactModelRepository: ContactModelRepository,
    private val serviceManager: ServiceManager,
) {
    private val hiddenChatListService by lazy { serviceManager.hiddenChatsListService }
    private val mutedChatsService by lazy { serviceManager.mutedChatsListService }
    private val ringtoneService by lazy { serviceManager.ringtoneService }
    private val symmetricEncryptionService by lazy { serviceManager.symmetricEncryptionService }
    private val fileService by lazy { serviceManager.fileService }
    private val contactService by lazy { serviceManager.contactService }

    private val multiDeviceProperties by lazy { serviceManager.multiDeviceManager.propertiesProvider }

    // Used when chats are pinned
    private val conversationTagService by lazy { serviceManager.conversationTagService }
    private val conversationService by lazy { serviceManager.conversationService }

    fun run() {
        when (contactSync.actionCase) {
            ContactSync.ActionCase.CREATE -> handleContactCreate(contactSync.create)
            ContactSync.ActionCase.UPDATE -> handleContactUpdate(contactSync.update)
            ContactSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for contact sync")
            null -> logger.warn("Action is null for contact sync")
        }
    }

    private fun handleContactCreate(contactCreate: Create) {
        logger.info("Processing reflected contact create")

        if (!contactCreate.hasContact()) {
            logger.warn("No contact provided in reflected contact create")
            return
        }

        // Check whether this contact already exists
        if (contactModelRepository.getByIdentity(contactCreate.contact.identity) != null) {
            logger.error(
                "Discarding 'create' message, contact {} already exists.",
                contactCreate.contact.identity
            )
            return
        }

        // Build contact model data based on the reflected data
        val contactModelData = try {
            contactCreate.contact.toNewContactModelData()
        } catch (e: MissingPropertyException) {
            logger.error(
                "Property {} is missing for a new contact. Discarding contact sync create message.",
                e.propertyName
            )
            return
        }

        // Create new contact
        try {
            contactModelRepository.createFromSync(contactModelData)
        } catch (e: ContactCreateException) {
            logger.error("Could not create contact", e)
            return
        }

        applyProfilePictures(contactCreate.contact)

        logger.info("New contact {} successfully created from sync", contactCreate.contact.identity)
    }

    private fun handleContactUpdate(contactUpdate: Update) {
        logger.info("Processing reflected contact update")

        val identity = contactUpdate.contact.identity

        val contactModel = contactModelRepository.getByIdentity(identity)
        if (contactModel != null) {
            applyContactModelUpdate(contactModel, contactUpdate.contact)
        } else {
            logger.error("Got a contact update for an unknown contact: {}", identity)
        }
    }

    private fun applyContactModelUpdate(contactModel: ContactModel, contact: Contact) {
        applyNames(contactModel, contact)

        applyVerificationLevel(contactModel, contact)

        applyIdentityType(contactModel, contact)

        applyAcquaintanceLevel(contactModel, contact)

        applyActivityState(contactModel, contact)

        applyFeatureMask(contactModel, contact)

        applySyncState(contactModel, contact)

        applyReadReceiptPolicyOverride(contactModel, contact)

        applyTypingIndicatorPolicyOverride(contactModel, contact)

        applyNotificationTriggerPolicy(contact)

        applyNotificationSoundPolicy(contact)

        applyProfilePictures(contact)

        applyConversationCategory(contact)

        applyConversationVisibility(contact)
    }

    private fun applyNames(contactModel: ContactModel, contact: Contact) {
        contact.getFirstNameOrNull()?.let {
            contactModel.setFirstNameFromSync(it)
        }
        contact.getLastNameOrNull()?.let {
            contactModel.setLastNameFromSync(it)
        }
        contact.getNicknameOrNull()?.let {
            contactModel.setNicknameFromSync(it)
        }
    }

    private fun applyVerificationLevel(contactModel: ContactModel, contact: Contact) {
        contact.getVerificationLevelOrNull()?.let {
            contactModel.setVerificationLevelFromSync(it)
        }
        contact.getWorkVerificationLevelOrNull()?.let {
            contactModel.setWorkVerificationLevelFromSync(it)
        }
    }

    private fun applyIdentityType(contactModel: ContactModel, contact: Contact) {
        contact.getIdentityTypeOrNull()?.let {
            contactModel.setIdentityTypeFromSync(it)
        }
    }

    private fun applyAcquaintanceLevel(contactModel: ContactModel, contact: Contact) {
        contact.getAcquaintanceLevelOrNull()?.let {
            contactModel.setAcquaintanceLevelFromSync(it)
        }
    }

    private fun applyActivityState(contactModel: ContactModel, contact: Contact) {
        contact.getActivityStateOrNull()?.let {
            contactModel.setActivityStateFromSync(it)
        }
    }

    private fun applyFeatureMask(contactModel: ContactModel, contact: Contact) {
        contact.getFeatureMaskOrNull()?.let {
            contactModel.setFeatureMaskFromSync(it)
        }
    }

    private fun applySyncState(contactModel: ContactModel, contact: Contact) {
        contact.getSyncStateOrNull()?.let {
            contactModel.setSyncStateFromSync(it)
        }
    }

    private fun applyReadReceiptPolicyOverride(contactModel: ContactModel, contact: Contact) {
        contact.getReadReceiptPolicyOrNull()?.let {
            contactModel.setReadReceiptPolicyFromSync(it)
        }
    }

    private fun applyTypingIndicatorPolicyOverride(contactModel: ContactModel, contact: Contact) {
        contact.getTypingIndicatorPolicyOrNull()?.let {
            contactModel.setTypingIndicatorPolicyFromSync(it)
        }
    }

    private fun applyConversationCategory(contact: Contact) {
        if (contact.hasConversationCategory()) {
            when (contact.conversationCategory) {
                MdD2DSync.ConversationCategory.DEFAULT -> false
                MdD2DSync.ConversationCategory.PROTECTED -> true
                MdD2DSync.ConversationCategory.UNRECOGNIZED -> unrecognizedValue("conversation category")
                null -> nullValue("conversation category")
            }?.let { isPrivateChat ->
                val uid = ContactUtil.getUniqueIdString(contact.identity)
                val hiddenChatListContainsUid = hiddenChatListService.has(uid)
                if (isPrivateChat) {
                    if (!hiddenChatListContainsUid) {
                        hiddenChatListService.add(uid, DeadlineListService.DEADLINE_INDEFINITE)
                    }
                } else {
                    if (hiddenChatListContainsUid) {
                        hiddenChatListService.remove(uid)
                    }
                }
            }
        }
    }

    private fun applyNotificationTriggerPolicy(contact: Contact) {
        val uid = ContactUtil.getUniqueIdString(contact.identity)

        if (contact.hasNotificationTriggerPolicyOverride()) {
            if (contact.notificationTriggerPolicyOverride.hasDefault()) {
                mutedChatsService.remove(uid)
            } else if (contact.notificationTriggerPolicyOverride.hasPolicy()) {
                val policy = contact.notificationTriggerPolicyOverride.policy
                val expiresAt = if (policy.hasExpiresAt()) {
                    policy.expiresAt
                } else {
                    DeadlineListService.DEADLINE_INDEFINITE
                }
                when (policy.policy) {
                    NotificationTriggerPolicy.NEVER -> mutedChatsService.add(uid, expiresAt)
                    NotificationTriggerPolicy.UNRECOGNIZED -> unrecognizedValue("notification trigger policy")
                    null -> nullValue("notification trigger policy")
                }
            } else {
                logger.warn("Notification trigger policy does not contain default or policy")
            }
        }
    }

    private fun applyNotificationSoundPolicy(contact: Contact) {
        val uid = ContactUtil.getUniqueIdString(contact.identity)

        if (contact.hasNotificationSoundPolicyOverride()) {
            if (contact.notificationSoundPolicyOverride.hasDefault()) {
                if (ringtoneService.isSilent(uid, false)) {
                    ringtoneService.setRingtone(uid, ringtoneService.defaultContactRingtone)
                }
            } else if (contact.notificationSoundPolicyOverride.hasPolicy()) {
                when (contact.notificationSoundPolicyOverride.policy) {
                    NotificationSoundPolicy.MUTED -> ringtoneService.setRingtone(uid, null)
                    NotificationSoundPolicy.UNRECOGNIZED -> unrecognizedValue("notification sound policy")
                    null -> nullValue("notification sound policy")
                }
            } else {
                logger.warn("Notification sound policy does not contain default or policy")
            }
        }
    }

    private fun applyProfilePictures(contact: Contact) {
        applyContactDefinedProfilePicture(contact)
        applyUserDefinedProfilePicture(contact)
    }

    private fun applyContactDefinedProfilePicture(contact: Contact) {
        when (contact.contactDefinedProfilePictureOrNull?.imageCase) {
            Common.DeltaImage.ImageCase.UPDATED -> {
                contact.contactDefinedProfilePicture.updated.blob.downloadBlob { blob ->
                    if (!fileService.getContactDefinedProfilePictureStream(contact.identity)
                            .contentEquals(blob)
                    ) {
                        logger.info("Setting contact defined profile picture from sync")
                        fileService.writeContactDefinedProfilePicture(contact.identity, blob)
                        onAvatarChanged(contact.identity)
                    }
                }
            }

            Common.DeltaImage.ImageCase.REMOVED -> {
                if (fileService.hasContactDefinedProfilePicture(contact.identity)) {
                    logger.info("Removing contact defined profile picture from sync")
                    fileService.removeContactDefinedProfilePicture(contact.identity)
                    onAvatarChanged(contact.identity)
                }
            }

            Common.DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Contact defined profile picture is not set")
            null -> Unit
        }
    }

    private fun applyUserDefinedProfilePicture(contact: Contact) {
        when (contact.userDefinedProfilePictureOrNull?.imageCase) {
            Common.DeltaImage.ImageCase.UPDATED -> {
                contact.userDefinedProfilePicture.updated.blob.downloadBlob { blob ->
                    if (!fileService.getUserDefinedProfilePictureStream(contact.identity)
                            .contentEquals(blob)
                    ) {
                        logger.info("Setting user defined profile picture from sync")
                        fileService.writeUserDefinedProfilePicture(contact.identity, blob)
                        onAvatarChanged(contact.identity)
                    }
                }
            }

            Common.DeltaImage.ImageCase.REMOVED -> {
                if (fileService.hasUserDefinedProfilePicture(contact.identity)) {
                    logger.info("Removing user defined profile picture from sync")
                    fileService.removeUserDefinedProfilePicture(contact.identity)
                    onAvatarChanged(contact.identity)
                }
            }

            Common.DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("User defined profile picture is not set")
            null -> Unit
        }
    }

    private fun onAvatarChanged(identity: String) {
        ListenerManager.contactListeners.handle { it.onAvatarChanged(identity) }
        ShortcutUtil.updateShareTargetShortcut(contactService.createReceiver(identity))
    }

    private fun Blob.downloadBlob(persistBlob: (blob: ByteArray) -> Unit) {
        val blobLoader = getBlobLoader()
        val encryptedBlob = {
            blobLoader.load(BlobScope.Local)
        }.catchAllExceptNetworkException { e ->
            logger.error("Could not download blob", e)
            return
        }

        val decryptedBlob = {
            symmetricEncryptionService.decrypt(
                encryptedBlob,
                key.toByteArray(),
                nonce.toByteArray().let { nonceBytes ->
                    if (nonceBytes.isEmpty()) {
                        ProtocolDefines.CONTACT_PHOTO_NONCE
                    } else {
                        nonceBytes
                    }
                }
            )
        }.catchAllExceptNetworkException { e ->
            logger.error("Could not decrypt blob", e)
            return
        }

        if (decryptedBlob == null) {
            logger.warn("Decrypted blob is null")
            blobLoader.markAsDone(id.toByteArray(), BlobScope.Local)
            return
        }

        persistBlob(decryptedBlob)

        blobLoader.markAsDone(id.toByteArray(), BlobScope.Local)
    }

    private fun Blob.getBlobLoader() = BlobLoader.mirror(
        baseOkHttpClient = serviceManager.okHttpClient,
        blobId = id.toByteArray(),
        version = ThreemaApplication.getAppVersion(),
        shouldLogHttp = ConfigUtils.isDevBuild(),
        serverAddressProvider = serviceManager.serverAddressProviderService.serverAddressProvider,
        progressListener = null,
        multiDevicePropertyProvider = multiDeviceProperties,
    )

    private fun applyConversationVisibility(contact: Contact) {
        if (contact.hasConversationVisibility()) {
            when (contact.conversationVisibility) {
                MdD2DSync.ConversationVisibility.NORMAL -> {
                    // TODO(ANDR-3010): Use new conversation model
                    val archivedConversationModel = getArchivedConversationModel(contact.identity)
                    if (archivedConversationModel != null) {
                        conversationService.unarchive(listOf(archivedConversationModel))
                    } else {
                        unPinConversation(contact.identity)
                    }
                }

                MdD2DSync.ConversationVisibility.ARCHIVED -> {
                    getConversationModel(contact.identity)?.let {
                        conversationService.archive(it)
                    }
                }

                MdD2DSync.ConversationVisibility.PINNED -> {
                    getArchivedConversationModel(contact.identity)?.let {
                        conversationService.unarchive(listOf(it))
                    }
                    pinConversation(contact.identity)
                }

                MdD2DSync.ConversationVisibility.UNRECOGNIZED -> unrecognizedValue("conversation visibility")

                null -> nullValue("conversation visibility")
            }
        }
    }

    private fun pinConversation(identity: String) {
        // TODO(ANDR-3010): Use new conversation model
        val conversationModel = getConversationModel(identity) ?: return
        val tagModel = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN)
        conversationTagService.addTagAndNotify(conversationModel, tagModel)
        conversationModel.setIsPinTagged(true)
    }

    private fun unPinConversation(identity: String) {
        // TODO(ANDR-3010): Use new conversation model
        val conversationModel = getConversationModel(identity) ?: return
        val tagModel = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN)
        conversationTagService.removeTagAndNotify(conversationModel, tagModel)
        conversationModel.setIsPinTagged(false)
    }

    private fun getConversationModel(identity: String): ConversationModel? {
        return conversationService.getAll(false)
            .find { it.contact?.identity == identity }.also {
                if (it == null) {
                    logger.warn("Could not find conversation model for contact {}", identity)
                }
            }
    }

    private fun getArchivedConversationModel(identity: String): ConversationModel? {
        return conversationService.getArchived("")
            .find { it.contact?.identity == identity }.also {
                if (it == null) {
                    logger.warn(
                        "Could not find archived conversation model for contact {}",
                        identity
                    )
                }
            }
    }

    private fun unrecognizedValue(valueName: String): Nothing? {
        logger.warn("Unrecognized {}", valueName)
        return null
    }

    private fun nullValue(valueName: String): Nothing? {
        logger.warn("Value {} is null", valueName)
        return null
    }

    private fun Contact.getFirstNameOrNull(): String? {
        return if (hasFirstName()) {
            firstName
        } else {
            null
        }
    }

    private fun Contact.getLastNameOrNull(): String? {
        return if (hasLastName()) {
            lastName
        } else {
            null
        }
    }

    private fun Contact.getNicknameOrNull(): String? {
        return if (hasNickname()) {
            nickname
        } else {
            null
        }
    }

    private fun Contact.getVerificationLevelOrNull(): VerificationLevel? {
        return if (hasVerificationLevel()) {
            when (verificationLevel) {
                Contact.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
                Contact.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
                Contact.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
                Contact.VerificationLevel.UNRECOGNIZED -> unrecognizedValue("verification level")
                null -> nullValue("verification level")
            }
        } else {
            null
        }
    }

    private fun Contact.getWorkVerificationLevelOrNull(): WorkVerificationLevel? {
        return if (hasWorkVerificationLevel()) {
            when (workVerificationLevel) {
                Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
                Contact.WorkVerificationLevel.NONE -> WorkVerificationLevel.NONE
                Contact.WorkVerificationLevel.UNRECOGNIZED -> unrecognizedValue("work verification level")
                null -> nullValue("work verification level")
            }
        } else {
            null
        }
    }

    private fun Contact.getIdentityTypeOrNull(): IdentityType? {
        return if (hasIdentityType()) {
            when (identityType) {
                Contact.IdentityType.REGULAR -> IdentityType.NORMAL
                Contact.IdentityType.WORK -> IdentityType.WORK
                Contact.IdentityType.UNRECOGNIZED -> unrecognizedValue("identity type")
                null -> nullValue("identity type")
            }
        } else {
            null
        }
    }

    private fun Contact.getAcquaintanceLevelOrNull(): AcquaintanceLevel? {
        return if (hasAcquaintanceLevel()) {
            when (acquaintanceLevel) {
                Contact.AcquaintanceLevel.DIRECT -> AcquaintanceLevel.DIRECT
                Contact.AcquaintanceLevel.GROUP_OR_DELETED -> AcquaintanceLevel.GROUP
                Contact.AcquaintanceLevel.UNRECOGNIZED -> unrecognizedValue("acquaintance level")
                null -> nullValue("acquaintance level")
            }
        } else {
            null
        }
    }

    private fun Contact.getActivityStateOrNull(): IdentityState? {
        return if (hasActivityState()) {
            when (activityState) {
                Contact.ActivityState.ACTIVE -> IdentityState.ACTIVE
                Contact.ActivityState.INACTIVE -> IdentityState.INACTIVE
                Contact.ActivityState.INVALID -> IdentityState.INVALID
                Contact.ActivityState.UNRECOGNIZED -> unrecognizedValue("activity state")
                null -> nullValue("activity state")
            }
        } else {
            null
        }
    }

    private fun Contact.getFeatureMaskOrNull(): ULong? {
        return if (hasFeatureMask()) {
            featureMask.toULong()
        } else {
            null
        }
    }

    private fun Contact.getSyncStateOrNull(): ContactSyncState? {
        return if (hasSyncState()) {
            when (syncState) {
                Contact.SyncState.INITIAL -> ContactSyncState.INITIAL
                Contact.SyncState.IMPORTED -> ContactSyncState.IMPORTED
                Contact.SyncState.CUSTOM -> ContactSyncState.CUSTOM
                Contact.SyncState.UNRECOGNIZED -> unrecognizedValue("sync state")
                null -> nullValue("sync state")
            }
        } else {
            null
        }
    }

    private fun Contact.getReadReceiptPolicyOrNull(): ReadReceiptPolicy? {
        return if (hasReadReceiptPolicyOverride()) {
            when {
                readReceiptPolicyOverride.hasDefault() -> ReadReceiptPolicy.DEFAULT
                readReceiptPolicyOverride.hasPolicy() -> when (readReceiptPolicyOverride.policy) {
                    MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT -> ReadReceiptPolicy.SEND
                    MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> ReadReceiptPolicy.DONT_SEND
                    MdD2DSync.ReadReceiptPolicy.UNRECOGNIZED -> unrecognizedValue("read receipt policy override")
                    null -> nullValue("read receipt policy override")
                }

                else -> {
                    logger.warn("Read receipt policy override does not have default nor policy")
                    null
                }
            }
        } else {
            null
        }
    }

    private fun Contact.getTypingIndicatorPolicyOrNull(): TypingIndicatorPolicy? {
        return if (hasTypingIndicatorPolicyOverride()) {
            when {
                typingIndicatorPolicyOverride.hasDefault() -> TypingIndicatorPolicy.DEFAULT
                typingIndicatorPolicyOverride.hasPolicy() -> when (typingIndicatorPolicyOverride.policy) {
                    MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.SEND
                    MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.DONT_SEND
                    MdD2DSync.TypingIndicatorPolicy.UNRECOGNIZED -> unrecognizedValue("typing indicator policy")
                    null -> nullValue("typing indicator policy")
                }

                else -> {
                    logger.warn("Typing indicator policy override does not have default nor policy")
                    null
                }
            }
        } else {
            null
        }
    }

    private fun Contact.getPublicKeyOrNull(): ByteArray? {
        return if (hasPublicKey()) {
            publicKey.toByteArray()
        } else {
            null
        }
    }

    private fun Contact.getCreatedAtOrNull(): Date? {
        return if (hasCreatedAt()) {
            return Date(createdAt)
        } else {
            null
        }
    }

    /**
     * Get the contact model data for the synced contact. Note that this expects a new contact and
     * therefore requires all mandatory properties to be set according to the protocol. If an
     * optional property is not set, default values are used.
     *
     * @throws MissingPropertyException if a required property for a new contact is missing
     */
    private fun Contact.toNewContactModelData() = ContactModelData(
        identity = identity,
        publicKey = getPublicKeyOrNull() ?: missingProperty("publicKey"),
        createdAt = getCreatedAtOrNull() ?: missingProperty("createdAt"),
        firstName = getFirstNameOrNull() ?: "",
        lastName = getLastNameOrNull() ?: "",
        nickname = getNicknameOrNull(),
        verificationLevel = getVerificationLevelOrNull() ?: VerificationLevel.UNVERIFIED,
        workVerificationLevel = getWorkVerificationLevelOrNull() ?: WorkVerificationLevel.NONE,
        identityType = getIdentityTypeOrNull() ?: IdentityType.NORMAL,
        acquaintanceLevel = getAcquaintanceLevelOrNull() ?: AcquaintanceLevel.DIRECT,
        activityState = getActivityStateOrNull() ?: IdentityState.ACTIVE,
        syncState = getSyncStateOrNull() ?: ContactSyncState.INITIAL,
        featureMask = getFeatureMaskOrNull() ?: missingProperty("featureMask"),
        readReceiptPolicy = getReadReceiptPolicyOrNull() ?: ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = getTypingIndicatorPolicyOrNull() ?: TypingIndicatorPolicy.DEFAULT,
        androidContactLookupKey = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
    )

    private fun missingProperty(propertyName: String): Nothing =
        throw MissingPropertyException(propertyName)

    private class MissingPropertyException(val propertyName: String) :
        ThreemaException("Missing property '")
}
