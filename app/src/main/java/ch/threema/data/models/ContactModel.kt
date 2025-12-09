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

package ch.threema.data.models

import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE_EXCEPT_MENTIONS
import ch.threema.app.tasks.ReflectContactSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectContactSyncUpdateTask
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.runtimeAssert
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toDate
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow

private val logger = getThreemaLogger("data.ContactModel")

/**
 * A contact.
 */
class ContactModel(
    val identity: Identity,
    data: ContactModelData,
    private val databaseBackend: DatabaseBackend,
    private val contactModelRepository: ContactModelRepository,
    coreServiceManager: CoreServiceManager,
) : BaseModel<ContactModelData, ReflectContactSyncUpdateTask>(
    MutableStateFlow(data),
    "ContactModel",
    coreServiceManager.multiDeviceManager,
    coreServiceManager.taskManager,
) {
    private val nonceFactory by lazy { coreServiceManager.nonceFactory }

    init {
        runtimeAssert(identity == data.identity, "Contact model identity mismatch")
    }

    /**
     *  We have to make the bridge over to the old ContactService in order
     *  to keep the new and old caches both correct.
     *
     *  TODO(ANDR-4361): Remove this
     */
    private val deprecatedContactService: ContactService? by lazy {
        val serviceManager: ServiceManager? = ThreemaApplication.getServiceManager()
        if (serviceManager == null) {
            logger.warn("Tried to get the contactService before the service-manager was created.")
        }
        serviceManager?.contactService
    }

    /**
     * Update the contact's first and last name.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setNameFromLocal(firstName: String, lastName: String) {
        this.updateFields(
            methodName = "setNameFromLocal",
            detectChanges = { originalData -> originalData.firstName != firstName || originalData.lastName != lastName },
            updateData = { originalData ->
                originalData.copy(
                    firstName = firstName,
                    lastName = lastName,
                )
            },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectNameUpdate(
                newFirstName = firstName,
                newLastName = lastName,
                contactIdentity = identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's jobTitle
     *
     * @throws ModelDeletedException if model is deleted.
     *
     * TODO(ANDR-3611): Reflect change to device group
     */
    fun setJobTitleFromLocal(jobTitle: String?) {
        this.updateFields(
            methodName = "setJobTitleFromLocal",
            detectChanges = { originalData -> originalData.jobTitle != jobTitle },
            updateData = { originalData -> originalData.copy(jobTitle = jobTitle) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = null,
        )
    }

    /**
     * Update the contact's department
     *
     * @throws ModelDeletedException if model is deleted.
     *
     * TODO(ANDR-3611): Reflect change to device group
     */
    fun setDepartmentFromLocal(department: String?) {
        this.updateFields(
            methodName = "setDepartmentFromLocal",
            detectChanges = { originalData -> originalData.department != department },
            updateData = { originalData -> originalData.copy(department = department) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = null,
        )
    }

    /**
     * Update the contact's acquaintance level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setAcquaintanceLevelFromLocal(acquaintanceLevel: AcquaintanceLevel) {
        this.updateFields(
            methodName = "setAcquaintanceLevelFromLocal",
            detectChanges = { originalData -> originalData.acquaintanceLevel != acquaintanceLevel },
            updateData = { originalData -> originalData.copy(acquaintanceLevel = acquaintanceLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = { contactModelData ->
                deprecatedContactService?.invalidateCache(contactModelData.identity)
                when (acquaintanceLevel) {
                    AcquaintanceLevel.DIRECT -> notifyDeprecatedOnModifiedListeners(contactModelData)
                    AcquaintanceLevel.GROUP -> notifyDeprecatedOnRemovedListeners(contactModelData.identity)
                }
            },
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate(
                acquaintanceLevel,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's verification level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setVerificationLevelFromLocal(verificationLevel: VerificationLevel) {
        this.updateFields(
            methodName = "setVerificationLevelFromLocal",
            detectChanges = { originalData -> originalData.verificationLevel != verificationLevel },
            updateData = { originalData -> originalData.copy(verificationLevel = verificationLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate(
                verificationLevel,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's work verification level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setWorkVerificationLevelFromLocal(workVerificationLevel: WorkVerificationLevel) {
        this.updateFields(
            methodName = "setWorkVerificationLevelFromLocal",
            detectChanges = { originalData -> originalData.workVerificationLevel != workVerificationLevel },
            updateData = { originalData -> originalData.copy(workVerificationLevel = workVerificationLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate(
                workVerificationLevel,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's identity type.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setIdentityTypeFromLocal(identityType: IdentityType) {
        this.updateFields(
            methodName = "setIdentityTypeFromLocal",
            detectChanges = { originalData -> originalData.identityType != identityType },
            updateData = { originalData -> originalData.copy(identityType = identityType) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate(
                identityType,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    fun setFeatureMaskFromLocal(
        featureMask: Long,
    ) {
        // Warn the user in case there is no forward security support anymore (indicated by a
        // feature mask change).
        data?.let {
            val previousFSSupport = ThreemaFeature.canForwardSecurity(it.featureMaskLong())
            val newFSSupport = ThreemaFeature.canForwardSecurity(featureMask)
            if (previousFSSupport && !newFSSupport) {
                ContactUtil.onForwardSecurityNotSupportedAnymore(this)
            }
        }

        this.updateFields(
            methodName = "setFeatureMaskFromLocal",
            detectChanges = { originalData -> originalData.featureMask != featureMask.toULong() },
            updateData = { originalData -> originalData.copy(featureMask = featureMask.toULong()) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate(
                featureMask,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's first name.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setFirstNameFromSync(firstName: String) {
        this.updateFields(
            methodName = "setFirstNameFromSync",
            detectChanges = { originalData -> originalData.firstName != firstName },
            updateData = { originalData -> originalData.copy(firstName = firstName) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's last name.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setLastNameFromSync(lastName: String) {
        this.updateFields(
            methodName = "setLastNameFromSync",
            detectChanges = { originalData -> originalData.lastName != lastName },
            updateData = { originalData -> originalData.copy(lastName = lastName) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's public nickname.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setNicknameFromSync(nickname: String?) {
        this.updateFields(
            methodName = "setNicknameFromSync",
            detectChanges = { originalData -> originalData.nickname != nickname },
            updateData = { originalData -> originalData.copy(nickname = nickname) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    suspend fun setNicknameFromRemote(nickname: String, handle: ActiveTaskCodec) {
        logger.debug("Updating nickname of {} to {}", identity, nickname)

        // We check whether the nickname is different before trying to reflect it.
        val data = ensureNotDeleted("setNicknameFromRemote")
        if (data.nickname == nickname) {
            return
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            ReflectContactSyncUpdateImmediateTask.ReflectContactNickname(
                contactIdentity = identity,
                newNickname = nickname,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ).reflect(handle)
        }

        this.updateFields(
            methodName = "setNicknameFromRemote",
            detectChanges = { originalData -> originalData.nickname != nickname },
            updateData = { originalData -> originalData.copy(nickname = nickname) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's verification level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setVerificationLevelFromSync(verificationLevel: VerificationLevel) {
        this.updateFields(
            methodName = "setVerificationLevelFromSync",
            detectChanges = { it.verificationLevel != verificationLevel },
            updateData = { it.copy(verificationLevel = verificationLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's work verification level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setWorkVerificationLevelFromSync(workVerificationLevel: WorkVerificationLevel) {
        this.updateFields(
            methodName = "setWorkVerificationLevelFromSync",
            detectChanges = { it.workVerificationLevel != workVerificationLevel },
            updateData = { it.copy(workVerificationLevel = workVerificationLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's identity type.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setIdentityTypeFromSync(identityType: IdentityType) {
        this.updateFields(
            methodName = "setIdentityTypeFromSync",
            detectChanges = { it.identityType != identityType },
            updateData = { it.copy(identityType = identityType) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's acquaintance level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setAcquaintanceLevelFromSync(acquaintanceLevel: AcquaintanceLevel) {
        this.updateFields(
            methodName = "setAcquaintanceLevelFromSync",
            detectChanges = { it.acquaintanceLevel != acquaintanceLevel },
            updateData = { it.copy(acquaintanceLevel = acquaintanceLevel) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's activity state.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setActivityStateFromSync(activityState: IdentityState) {
        this.updateFields(
            methodName = "setActivityStateFromSync",
            detectChanges = { it.activityState != activityState },
            updateData = { it.copy(activityState = activityState) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's activity state.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setActivityStateFromLocal(activityState: IdentityState) {
        this.updateFields(
            methodName = "setActivityStateFromLocal",
            detectChanges = { it.activityState != activityState },
            updateData = { it.copy(activityState = activityState) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectActivityStateUpdate(
                activityState,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's feature mask.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setFeatureMaskFromSync(featureMask: ULong) {
        this.updateFields(
            methodName = "setFeatureMaskFromSync",
            detectChanges = { it.featureMask != featureMask },
            updateData = { it.copy(featureMask = featureMask) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's sync state.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setSyncStateFromSync(syncState: ContactSyncState) {
        this.updateFields(
            methodName = "setSyncStateFromSync",
            detectChanges = { it.syncState != syncState },
            updateData = { it.copy(syncState = syncState) },
            updateDatabase = ::updateDatabase,
            onUpdated = { updatedData ->
                // No need to notify listeners, this isn't something that will result in a UI change.
                // But keep old-service cache correct:
                deprecatedContactService?.invalidateCache(updatedData.identity)
            },
        )
    }

    /**
     * Update the contact's read receipt policy.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setReadReceiptPolicyFromSync(readReceiptPolicy: ReadReceiptPolicy) {
        this.updateFields(
            methodName = "setReadReceiptPolicyFromSync",
            detectChanges = { it.readReceiptPolicy != readReceiptPolicy },
            updateData = { it.copy(readReceiptPolicy = readReceiptPolicy) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's read receipt policy.
     *
     * @throws ModelDeletedException if model is deleted
     */
    fun setReadReceiptPolicyFromLocal(readReceiptPolicy: ReadReceiptPolicy) {
        this.updateFields(
            methodName = "setReadReceiptPolicyFromLocal",
            detectChanges = { it.readReceiptPolicy != readReceiptPolicy },
            updateData = { it.copy(readReceiptPolicy = readReceiptPolicy) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate(
                readReceiptPolicy,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update the contact's typing indicator policy.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setTypingIndicatorPolicyFromSync(typingIndicatorPolicy: TypingIndicatorPolicy) {
        this.updateFields(
            methodName = "setTypingIndicatorPolicyFromSync",
            detectChanges = { it.typingIndicatorPolicy != typingIndicatorPolicy },
            updateData = { it.copy(typingIndicatorPolicy = typingIndicatorPolicy) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's typing indicator policy.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setTypingIndicatorPolicyFromLocal(typingIndicatorPolicy: TypingIndicatorPolicy) {
        this.updateFields(
            methodName = "setTypingIndicatorPolicyFromLocal",
            detectChanges = { it.typingIndicatorPolicy != typingIndicatorPolicy },
            updateData = { it.copy(typingIndicatorPolicy = typingIndicatorPolicy) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate(
                typingIndicatorPolicy,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Update or remove the contact's Android contact lookup key.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setAndroidContactLookupKey(lookupKey: AndroidContactLookupInfo) {
        this.updateFields(
            methodName = "setAndroidLookupKey",
            detectChanges = { originalData -> originalData.androidContactLookupInfo != lookupKey },
            updateData = { originalData -> originalData.copy(androidContactLookupInfo = lookupKey) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Unlink the contact from the android contact. This sets the android lookup key to null and
     * downgrades the verification level if it is [VerificationLevel.SERVER_VERIFIED]. Note that
     * the verification level change is reflected if MD is active.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun removeAndroidContactLink() {
        // Remove the android lookup info
        this.updateFields(
            methodName = "unlinkAndroidContact",
            detectChanges = { originalData -> originalData.androidContactLookupInfo != null },
            updateData = { originalData -> originalData.copy(androidContactLookupInfo = null) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )

        // Change verification level if it is server verified. Note that we do not use
        // setVerificationLevelFromLocal as this must only be done when the verification level is
        // server verified.
        this.updateFields(
            methodName = "unlinkAndroidContact",
            detectChanges = { originalData -> originalData.verificationLevel == VerificationLevel.SERVER_VERIFIED },
            updateData = { originalData -> originalData.copy(verificationLevel = VerificationLevel.UNVERIFIED) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate(
                VerificationLevel.UNVERIFIED,
                identity,
                contactModelRepository,
                multiDeviceManager,
                nonceFactory,
            ),
        )
    }

    /**
     * Set the id color to the given value. Note that normally it isn't necessary to set this manually.
     */
    fun setIdColor(idColor: IdColor) {
        this.updateFields(
            methodName = "setIdColor",
            detectChanges = { originalData -> originalData.idColor != idColor },
            updateData = { originalData -> originalData.copy(idColor = idColor) },
            updateDatabase = ::updateDatabase,
            onUpdated = null,
        )
    }

    /**
     * Update or remove the contact's local avatar expiration date.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setLocalAvatarExpires(expiresAt: Instant?) {
        setLocalAvatarExpires(expiresAt?.toDate())
    }

    private fun setLocalAvatarExpires(expiresAt: Date?) {
        this.updateFields(
            methodName = "setLocalAvatarExpires",
            detectChanges = { originalData -> originalData.localAvatarExpires != expiresAt },
            updateData = { originalData -> originalData.copy(localAvatarExpires = expiresAt) },
            updateDatabase = ::updateDatabase,
            onUpdated = { updatedData ->
                // No need to notify listeners, this isn't something that will result in a UI change.
                // But keep old-service cache correct:
                deprecatedContactService?.invalidateCache(updatedData.identity)
            },
        )
    }

    /**
     * Clear the "isRestored" flag on the contact.
     *
     * This should be called once the post-restore sync steps (e.g. profile picture request)
     * have been completed.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun clearIsRestored() {
        this.updateFields(
            methodName = "clearIsRestored",
            detectChanges = { originalData -> originalData.isRestored },
            updateData = { originalData -> originalData.copy(isRestored = false) },
            updateDatabase = ::updateDatabase,
            onUpdated = { updatedData ->
                // No need to notify listeners, this isn't something that will result in a UI change.
                // But keep old-service cache correct:
                deprecatedContactService?.invalidateCache(updatedData.identity)
            },
        )
    }

    /**
     * Set the BlobId of the latest profile picture that was sent to this contact.
     *
     * @param blobId The blobId of the latest profile picture sent to this contact, `null` if no
     *   profile-picture has been sent, or an empty array if a delete-profile-picture message has
     *   been sent.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setProfilePictureBlobId(blobId: ByteArray?) {
        this.updateFields(
            methodName = "setProfilePictureBlobId",
            detectChanges = { originalData ->
                !originalData.profilePictureBlobId.contentEquals(
                    blobId,
                )
            },
            updateData = { originalData -> originalData.copy(profilePictureBlobId = blobId) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Set whether the contact has been restored or not. After a restore of a backup, every contact
     * is marked as restored to track whether the profile picture must be requested from this
     * contact.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setIsRestored(isRestored: Boolean) {
        this.updateFields(
            methodName = "setIsRestored",
            detectChanges = { originalData -> originalData.isRestored != isRestored },
            updateData = { originalData -> originalData.copy(isRestored = isRestored) },
            updateDatabase = ::updateDatabase,
            onUpdated = { updatedData ->
                // No need to notify listeners, this isn't something that will result in a UI change.
                // But keep old-service cache correct:
                deprecatedContactService?.invalidateCache(updatedData.identity)
            },
        )
    }

    /**
     * Update the contact's notification-trigger-policy-override **without** reflecting the change.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setNotificationTriggerPolicyOverrideFromSync(notificationTriggerPolicyOverride: Long?) {
        this.updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromSync",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update the contact's notification-trigger-policy-override and reflecting the change.
     *
     * @throws [ModelDeletedException] if model is deleted.
     *
     * @see NotificationTriggerPolicyOverride
     */
    fun setNotificationTriggerPolicyOverrideFromLocal(notificationTriggerPolicyOverride: Long?) {
        if (notificationTriggerPolicyOverride == DEADLINE_INDEFINITE_EXCEPT_MENTIONS) {
            logger.error("Can not set notification-trigger-policy-override value of $notificationTriggerPolicyOverride for contact")
            return
        }
        this.updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromLocal",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate(
                newNotificationTriggerPolicyOverride = NotificationTriggerPolicyOverride.fromDbValueContact(
                    notificationTriggerPolicyOverride,
                ),
                contactIdentity = identity,
                contactModelRepository = contactModelRepository,
                multiDeviceManager = multiDeviceManager,
                nonceFactory = nonceFactory,
            ),
        )
    }

    /**
     * Archive or unarchive the contact.
     *
     * TODO(ANDR-3721): As long as it is possible to mark a contact as pinned outside of the contact model, this method must be used extremely
     *  carefully as a contact can never be archived *and* pinned.
     */
    fun setIsArchivedFromLocalOrRemote(isArchived: Boolean) {
        this.updateFields(
            methodName = "setIsArchiveFromLocalOrRemote",
            detectChanges = { originalData -> originalData.isArchived != isArchived },
            updateData = { originalData -> originalData.copy(isArchived = isArchived) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
            reflectUpdateTask = ReflectContactSyncUpdateTask.ReflectConversationVisibilityArchiveUpdate(
                isArchived = isArchived,
                contactIdentity = identity,
                contactModelRepository = contactModelRepository,
                multiDeviceManager = multiDeviceManager,
                nonceFactory = nonceFactory,
            ),
        )
    }

    /**
     * Archive or unarchive the contact.
     *
     * TODO(ANDR-3721): As long as it is possible to mark a contact as pinned outside of the contact model, this method must be used extremely
     *  carefully as a contact can never be archived *and* pinned.
     */
    fun setIsArchivedFromSync(isArchived: Boolean) {
        this.updateFields(
            methodName = "setIsArchivedFromSync",
            detectChanges = { originalData -> originalData.isArchived != isArchived },
            updateData = { originalData -> originalData.copy(isArchived = isArchived) },
            updateDatabase = ::updateDatabase,
            onUpdated = ::defaultOnUpdated,
        )
    }

    /**
     * Update all data from database.
     *
     * Note: This method may only be called by the repository, in code that bridges the old models
     * to the new models. All other code does not need to refresh the data, the model's state flow
     * should always be up to date.
     *
     * Note: If the model is marked as deleted, then this will have no effect.
     */
    internal fun refreshFromDb(@Suppress("UNUSED_PARAMETER") token: RepositoryToken) {
        logger.info("Refresh from database")
        synchronized(this) {
            if (mutableData.value == null) {
                logger.warn("Cannot refresh deleted ${this.modelName} from DB")
                return
            }
            val dbContact = databaseBackend.getContactByIdentity(identity) ?: return
            val newData = ContactModelDataFactory.toDataType(dbContact)
            runtimeAssert(
                newData.identity == identity,
                "Cannot update contact model with data for different identity: ${newData.identity} != $identity",
            )
            mutableData.value = newData
        }
    }

    private fun updateDatabase(updatedData: ContactModelData) {
        databaseBackend.updateContact(ContactModelDataFactory.toDbType(updatedData))
    }

    /**
     * Synchronously notify contact change listeners.
     */
    private fun notifyDeprecatedOnModifiedListeners(data: ContactModelData) {
        ListenerManager.contactListeners.handle { it.onModified(data.identity) }
    }

    /**
     * Synchronously notify contact change listeners.
     */
    private fun notifyDeprecatedOnRemovedListeners(identity: Identity) {
        ListenerManager.contactListeners.handle { it.onRemoved(identity) }
    }

    private fun defaultOnUpdated(updatedData: ContactModelData) {
        deprecatedContactService?.invalidateCache(updatedData.identity)
        notifyDeprecatedOnModifiedListeners(updatedData)
    }
}

internal object ContactModelDataFactory : ModelDataFactory<ContactModelData, DbContact> {
    override fun toDbType(value: ContactModelData): DbContact = DbContact(
        identity = value.identity,
        publicKey = value.publicKey,
        createdAt = value.createdAt,
        firstName = value.firstName,
        lastName = value.lastName,
        nickname = value.nickname,
        colorIndex = value.idColor.colorIndex,
        verificationLevel = value.verificationLevel,
        workVerificationLevel = value.workVerificationLevel,
        identityType = value.identityType,
        acquaintanceLevel = value.acquaintanceLevel,
        activityState = value.activityState,
        syncState = value.syncState,
        featureMask = value.featureMask,
        readReceiptPolicy = value.readReceiptPolicy,
        typingIndicatorPolicy = value.typingIndicatorPolicy,
        isArchived = value.isArchived,
        androidContactLookupKey = value.androidContactLookupInfo.toDatabaseString(),
        localAvatarExpires = value.localAvatarExpires,
        isRestored = value.isRestored,
        profilePictureBlobId = value.profilePictureBlobId,
        jobTitle = value.jobTitle,
        department = value.department,
        notificationTriggerPolicyOverride = value.notificationTriggerPolicyOverride,
    )

    override fun toDataType(value: DbContact): ContactModelData = ContactModelData(
        identity = value.identity,
        publicKey = value.publicKey,
        createdAt = value.createdAt,
        firstName = value.firstName,
        lastName = value.lastName,
        nickname = value.nickname,
        idColor = IdColor(value.colorIndex),
        verificationLevel = value.verificationLevel,
        workVerificationLevel = value.workVerificationLevel,
        identityType = value.identityType,
        acquaintanceLevel = value.acquaintanceLevel,
        activityState = value.activityState,
        syncState = value.syncState,
        featureMask = value.featureMask,
        readReceiptPolicy = value.readReceiptPolicy,
        typingIndicatorPolicy = value.typingIndicatorPolicy,
        isArchived = value.isArchived,
        androidContactLookupInfo = value.androidContactLookupKey.toAndroidContactLookupKey(),
        localAvatarExpires = value.localAvatarExpires,
        isRestored = value.isRestored,
        profilePictureBlobId = value.profilePictureBlobId,
        jobTitle = value.jobTitle,
        department = value.department,
        notificationTriggerPolicyOverride = value.notificationTriggerPolicyOverride,
    )

    private fun AndroidContactLookupInfo?.toDatabaseString(): String? = this?.run {
        // Note that we append '/null' on purpose if the contact id is null. This ensures that we parse the lookup key and contact id correctly if the
        // lookup key contains any slashes. When parsing the string, we rely on the last '/' for splitting the lookup key and the contact id.
        "$lookupKey/$contactId"
    }

    private fun String?.toAndroidContactLookupKey(): AndroidContactLookupInfo? = this?.let { androidContactLookupKeyString ->
        AndroidContactLookupInfo(
            lookupKey = androidContactLookupKeyString.substringBeforeLast(delimiter = "/"),
            contactId = androidContactLookupKeyString.substringAfterLast(delimiter = "/", missingDelimiterValue = "").toLongOrNull(),
        )
    }
}
