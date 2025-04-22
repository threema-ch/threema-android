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
import ch.threema.app.utils.ColorUtil
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.runtimeAssert
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.UnsignedHelper
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbContact
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow

private val logger = LoggingUtil.getThreemaLogger("data.ContactModel")

/**
 * Immutable contact model data.
 *
 * TODO(ANDR-2998): Notification sound policy override
 */
data class ContactModelData(
    /** The contact identity string. Must be 8 characters long. */
    @JvmField val identity: String,
    /** The 32-byte public key of the contact. */
    @JvmField val publicKey: ByteArray,
    /** Timestamp when this contact was added to the contact list. */
    @JvmField val createdAt: Date,
    /** First name. */
    @JvmField val firstName: String,
    /** Last name. */
    @JvmField val lastName: String,
    /** Public nickname. */
    @JvmField val nickname: String?,
    /** Color index (0-255). */
    val colorIndex: UByte = getIdColorIndex(identity),
    /** Verification level. */
    @JvmField val verificationLevel: VerificationLevel,
    /** Threema Work verification level. */
    @JvmField val workVerificationLevel: WorkVerificationLevel,
    /** Identity type (regular / work). */
    @JvmField val identityType: IdentityType,
    /** Acquaintance level (direct / group). */
    @JvmField val acquaintanceLevel: AcquaintanceLevel,
    /** Activity state (active / inactive / invalid). */
    @JvmField val activityState: IdentityState,
    /** Contact sync state. */
    @JvmField val syncState: ContactSyncState,
    /** Feature mask. */
    val featureMask: ULong,
    /** Read receipt policy. */
    @JvmField val readReceiptPolicy: ReadReceiptPolicy,
    /** Typing indicator policy. */
    @JvmField val typingIndicatorPolicy: TypingIndicatorPolicy,
    /**
     * Whether the conversation with the contact is archived or not. Note that this information belongs to the 'conversation visibility' and should
     * probably be moved to the new conversation model TODO(ANDR-3010).
     */
    @JvmField val isArchived: Boolean,
    /** Android contact lookup key. */
    @JvmField val androidContactLookupKey: String?,
    /**
     * Local avatar expiration date.
     *
     * For gateway contacts, this is used to determine when to refresh the avatar from the server.
     *
     * For contacts linked to an Android system contact, this is used to determine when to refresh
     * the avatar from the system address book.
     *
     * For other contacts, this is always set to null.
     */
    @JvmField val localAvatarExpires: Date?,
    /**
     * Whether this contact has been restored from backup.
     */
    @JvmField val isRestored: Boolean,
    /**
     * BlobId of the latest profile picture that was sent to this contact.
     */
    @JvmField val profilePictureBlobId: ByteArray?,
    @JvmField val jobTitle: String?,
    @JvmField val department: String?,
    /**
     *  Encapsulates all logic of `Contact.NotificationTriggerPolicyOverride.Policy` into a single `Long?` value.
     *  See [NotificationTriggerPolicyOverride] for possible values and their meanings.
     */
    @JvmField val notificationTriggerPolicyOverride: Long?,
) {
    companion object {
        /**
         * Factory function using only Java-compatible types.
         */
        @JvmStatic
        fun javaCreate(
            identity: String,
            publicKey: ByteArray,
            createdAt: Date,
            firstName: String,
            lastName: String,
            nickname: String?,
            colorIndex: Int,
            verificationLevel: VerificationLevel,
            workVerificationLevel: WorkVerificationLevel,
            identityType: IdentityType,
            acquaintanceLevel: AcquaintanceLevel,
            activityState: IdentityState,
            featureMask: BigInteger,
            syncState: ContactSyncState,
            readReceiptPolicy: ReadReceiptPolicy,
            typingIndicatorPolicy: TypingIndicatorPolicy,
            isArchived: Boolean,
            androidContactLookupKey: String?,
            localAvatarExpires: Date?,
            isRestored: Boolean,
            profilePictureBlobId: ByteArray?,
            jobTitle: String?,
            department: String?,
            notificationTriggerPolicyOverride: Long?,
        ): ContactModelData {
            if (colorIndex < 0 || colorIndex > 255) {
                throw IllegalArgumentException("colorIndex must be between 0 and 255")
            }
            if (featureMask.signum() < 0 || featureMask.bitLength() > 64) {
                throw IllegalArgumentException("featureMask must be between 0 and 2^64")
            }
            return ContactModelData(
                identity = identity,
                publicKey = publicKey,
                createdAt = createdAt,
                firstName = firstName,
                lastName = lastName,
                nickname = nickname,
                colorIndex = colorIndex.toUByte(),
                verificationLevel = verificationLevel,
                workVerificationLevel = workVerificationLevel,
                identityType = identityType,
                acquaintanceLevel = acquaintanceLevel,
                activityState = activityState,
                syncState = syncState,
                featureMask = featureMask.toLong().toULong(),
                readReceiptPolicy = readReceiptPolicy,
                typingIndicatorPolicy = typingIndicatorPolicy,
                isArchived = isArchived,
                androidContactLookupKey = androidContactLookupKey,
                localAvatarExpires = localAvatarExpires,
                isRestored = isRestored,
                profilePictureBlobId = profilePictureBlobId,
                jobTitle = jobTitle,
                department = department,
                notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
            )
        }

        /**
         * Compute the id color index based on the identity.
         */
        fun getIdColorIndex(identity: String): UByte = try {
            val firstByte = MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(StandardCharsets.UTF_8)).first()
            ColorUtil.getInstance().getIDColorIndex(firstByte).toUByte()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Could not find hashing algorithm for id color", e)
        }

        /**
         * Compute the id color index based on the identity.
         */
        @JvmStatic
        fun getIdColorIndexInt(identity: String): Int = getIdColorIndex(identity).toInt()
    }

    /**
     * Return the [colorIndex] as [Int].
     */
    fun colorIndexInt(): Int = colorIndex.toInt()

    /**
     * Return the [featureMask] as [BigInteger].
     */
    fun featureMaskBigInteger(): BigInteger =
        UnsignedHelper.unsignedLongToBigInteger(featureMask.toLong())

    /**
     * Return the [featureMask] as positive [Long].
     *
     * Throws [IllegalArgumentException] if value does not fit in a [Long].
     */
    fun featureMaskLong(): Long {
        val long = featureMask.toLong()
        if (long < 0) {
            throw IllegalArgumentException("Feature mask does not fit in a signed long")
        }
        return long
    }

    /**
     * Return the display name for this contact.
     *
     * - Use first and/or last name if set
     * - Use nickname if set
     * - Fall back to identity
     */
    fun getDisplayName(): String {
        val hasFirstName = this.firstName.isNotBlank()
        val hasLastName = this.lastName.isNotBlank()
        val hasNickname = !this.nickname.isNullOrBlank() && this.nickname != this.identity

        if (hasFirstName && hasLastName) {
            return "${this.firstName} ${this.lastName}"
        }
        if (hasFirstName) {
            return this.firstName
        }
        if (hasLastName) {
            return this.lastName
        }
        if (hasNickname) {
            return "~${this.nickname}"
        }
        return this.identity
    }

    /**
     * Return whether or not this contact is linked to an Android contact.
     */
    fun isLinkedToAndroidContact(): Boolean = this.androidContactLookupKey != null

    /**
     * Check if the avatar is expired. If no [localAvatarExpires] is set, the avatar is also
     * considered as expired.
     */
    fun isAvatarExpired(): Boolean = localAvatarExpires?.before(Date()) ?: true

    /**
     * Check if the contact is a gateway contact.
     */
    fun isGatewayContact(): Boolean = ContactUtil.isGatewayContact(identity)

    /**
     * Get the contact model data as basic contact.
     */
    fun toBasicContact(): BasicContact = BasicContact(
        identity,
        publicKey,
        featureMask,
        activityState,
        identityType,
    )

    val currentNotificationTriggerPolicyOverride
        get() = NotificationTriggerPolicyOverride.fromDbValueContact(notificationTriggerPolicyOverride)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactModelData

        if (identity != other.identity) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (createdAt != other.createdAt) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (nickname != other.nickname) return false
        if (colorIndex != other.colorIndex) return false
        if (verificationLevel != other.verificationLevel) return false
        if (workVerificationLevel != other.workVerificationLevel) return false
        if (identityType != other.identityType) return false
        if (acquaintanceLevel != other.acquaintanceLevel) return false
        if (activityState != other.activityState) return false
        if (syncState != other.syncState) return false
        if (featureMask != other.featureMask) return false
        if (readReceiptPolicy != other.readReceiptPolicy) return false
        if (typingIndicatorPolicy != other.typingIndicatorPolicy) return false
        if (isArchived != other.isArchived) return false
        if (androidContactLookupKey != other.androidContactLookupKey) return false
        if (localAvatarExpires != other.localAvatarExpires) return false
        if (isRestored != other.isRestored) return false
        if (profilePictureBlobId != null) {
            if (other.profilePictureBlobId == null) return false
            if (!profilePictureBlobId.contentEquals(other.profilePictureBlobId)) return false
        } else if (other.profilePictureBlobId != null) {
            return false
        }
        if (jobTitle != other.jobTitle) return false
        if (department != other.department) return false
        if (notificationTriggerPolicyOverride != other.notificationTriggerPolicyOverride) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + (nickname?.hashCode() ?: 0)
        result = 31 * result + colorIndex.hashCode()
        result = 31 * result + verificationLevel.hashCode()
        result = 31 * result + workVerificationLevel.hashCode()
        result = 31 * result + identityType.hashCode()
        result = 31 * result + acquaintanceLevel.hashCode()
        result = 31 * result + activityState.hashCode()
        result = 31 * result + syncState.hashCode()
        result = 31 * result + featureMask.hashCode()
        result = 31 * result + readReceiptPolicy.hashCode()
        result = 31 * result + typingIndicatorPolicy.hashCode()
        result = 31 * result + (androidContactLookupKey?.hashCode() ?: 0)
        result = 31 * result + (localAvatarExpires?.hashCode() ?: 0)
        result = 31 * result + isRestored.hashCode()
        result = 31 * result + (profilePictureBlobId?.contentHashCode() ?: 0)
        result = 31 * result + notificationTriggerPolicyOverride.hashCode()
        return result
    }
}

/**
 * A contact.
 */
class ContactModel(
    val identity: String,
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
     *  TODO(ANDR-3037): Check if this is still necessary after this ticket
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
        data.value?.let {
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
    fun setAndroidLookupKey(lookupKey: String) {
        this.updateFields(
            methodName = "setAndroidLookupKey",
            detectChanges = { originalData -> originalData.androidContactLookupKey != lookupKey },
            updateData = { originalData -> originalData.copy(androidContactLookupKey = lookupKey) },
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
        // Remove the android lookup key
        this.updateFields(
            methodName = "unlinkAndroidContact",
            detectChanges = { originalData -> originalData.androidContactLookupKey != null },
            updateData = { originalData -> originalData.copy(androidContactLookupKey = null) },
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
     * Update or remove the contact's local avatar expiration date.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setLocalAvatarExpires(expiresAt: Date?) {
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
    private fun notifyDeprecatedOnRemovedListeners(identity: String) {
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
        colorIndex = value.colorIndex,
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
        androidContactLookupKey = value.androidContactLookupKey,
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
        colorIndex = value.colorIndex,
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
        androidContactLookupKey = value.androidContactLookupKey,
        localAvatarExpires = value.localAvatarExpires,
        isRestored = value.isRestored,
        profilePictureBlobId = value.profilePictureBlobId,
        jobTitle = value.jobTitle,
        department = value.department,
        notificationTriggerPolicyOverride = value.notificationTriggerPolicyOverride,
    )
}
