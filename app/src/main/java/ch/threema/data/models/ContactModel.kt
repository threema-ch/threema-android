/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.runtimeAssert
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.UnsignedHelper
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ContactModel.State
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigInteger
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("data.ContactModel")

/**
 * Immutable contact model data.
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
    val colorIndex: UByte,
    /** Verification level. */
    @JvmField val verificationLevel: VerificationLevel,
    /** Threema Work verification level. */
    @JvmField val workVerificationLevel: WorkVerificationLevel,
    /** Identity type (regular / work). */
    @JvmField val identityType: IdentityType,
    /** Acquaintance level (direct / group). */
    @JvmField val acquaintanceLevel: AcquaintanceLevel,
    /** Activity state (active / inactive / invalid). */
    @JvmField val activityState: State,
    /** Contact sync state. */
    @JvmField val syncState: ContactSyncState,
    /** Feature mask. */
    val featureMask: ULong,
    /** Read receipt policy. */
    @JvmField val readReceiptPolicy: ReadReceiptPolicy,
    /** Typing indicator policy. */
    @JvmField val typingIndicatorPolicy: TypingIndicatorPolicy,
    // TODO(ANDR-2998): Notification trigger policy override
    // TODO(ANDR-2998): Notification sound policy override
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
            activityState: State,
            featureMask: BigInteger,
            syncState: ContactSyncState,
            readReceiptPolicy: ReadReceiptPolicy,
            typingIndicatorPolicy: TypingIndicatorPolicy,
            androidContactLookupKey: String?,
            localAvatarExpires: Date?,
            isRestored: Boolean,
            profilePictureBlobId: ByteArray?,
        ): ContactModelData {
            if (colorIndex < 0 || colorIndex > 255) {
                throw IllegalArgumentException("colorIndex must be between 0 and 255")
            }
            if (featureMask.signum() < 0 || featureMask.bitLength() > 64) {
                throw IllegalArgumentException("featureMask must be between 0 and 2^64")
            }
            return ContactModelData(
                identity,
                publicKey,
                createdAt,
                firstName,
                lastName,
                nickname,
                colorIndex.toUByte(),
                verificationLevel,
                workVerificationLevel,
                identityType,
                acquaintanceLevel,
                activityState,
                syncState,
                featureMask.toLong().toULong(),
                readReceiptPolicy,
                typingIndicatorPolicy,
                androidContactLookupKey,
                localAvatarExpires,
                isRestored,
                profilePictureBlobId,
            )
        }
    }

    /**
     * Return the [colorIndex] as [Int].
     */
    fun colorIndexInt(): Int = colorIndex.toInt()

    /**
     * Return the [featureMask] as [BigInteger].
     */
    fun featureMaskBigInteger(): BigInteger = UnsignedHelper.unsignedLongToBigInteger(featureMask.toLong())

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
}

/**
 * A contact.
 */
class ContactModel(
    val identity: String,
    data: ContactModelData,
    private val databaseBackend: DatabaseBackend,
) : BaseModel<ContactModelData>(MutableStateFlow(data), "ContactModel") {

    init {
        runtimeAssert(identity == data.identity, "Contact model identity mismatch")
    }

    /**
     * Get a [LiveData] for the internal data state flow.
     */
    fun liveData(): LiveData<ContactModelData?> = data.asLiveData()

    /**
     * Update the contact's first and last name.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setNameFromLocal(firstName: String, lastName: String) {
        this.updateFields(
            "setNameFromLocal",
            { originalData -> originalData.firstName != firstName || originalData.lastName != lastName },
            { originalData -> originalData.copy(firstName = firstName, lastName = lastName) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
        )
    }

    /**
     * Update the contact's acquaintance level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setAcquaintanceLevelFromLocal(acquaintanceLevel: AcquaintanceLevel) {
        this.updateFields(
            "setAcquaintanceLevelFromLocal",
            { originalData -> originalData.acquaintanceLevel != acquaintanceLevel },
            { originalData -> originalData.copy(acquaintanceLevel = acquaintanceLevel) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
        )
    }

    /**
     * Update the contact's verification level.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setVerificationLevelFromLocal(verificationLevel: VerificationLevel) {
        this.updateFields(
            "setVerificationLevelFromLocal",
            { originalData -> originalData.verificationLevel != verificationLevel },
            { originalData -> originalData.copy(verificationLevel = verificationLevel) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
        )
    }

    /**
     * Update the contact's public nickname.
     *
     * @throws [ModelDeletedException] if model is deleted.
     */
    fun setNicknameFromSync(nickname: String?) {
        this.updateFields(
            "setNicknameFromSync",
            { originalData -> originalData.nickname != nickname },
            { originalData -> originalData.copy(nickname = nickname) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
        )
    }

    /**
     * Update or remove the contact's Android contact lookup key.
     */
    fun setAndroidLookupKey(lookupKey: String?) {
        this.updateFields(
            "setAndroidLookupKey",
            { originalData -> originalData.androidContactLookupKey != lookupKey },
            { originalData -> originalData.copy(androidContactLookupKey = lookupKey) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
        )
    }

    /**
     * Update or remove the contact's local avatar expiration date.
     */
    fun setLocalAvatarExpires(expiresAt: Date?) {
        this.updateFields(
            "setLocalAvatarExpires",
            { originalData -> originalData.localAvatarExpires != expiresAt },
            { originalData -> originalData.copy(localAvatarExpires = expiresAt) },
            ::updateDatabase,
            null, // No need to notify listeners, this isn't something that will result in a UI change.
        )
    }

    /**
     * Clear the "isRestored" flag on the contact.
     *
     * This should be called once the post-restore sync steps (e.g. profile picture request)
     * have been completed.
     */
    fun clearIsRestored() {
        this.updateFields(
            "clearIsRestored",
            { originalData -> originalData.isRestored },
            { originalData -> originalData.copy(isRestored = false) },
            ::updateDatabase,
            null, // No need to notify listeners, this isn't something that will result in a UI change.
        )
    }

    /**
     * Set the BlobId of the latest profile picture that was sent to this contact.
     *
     * @param blobId The blobId of the latest profile picture sent to this contact, `null` if no
     *   profile-picture has been sent, or an empty array if a delete-profile-picture message has
     *   been sent.
     */
    fun setProfilePictureBlobId(blobId: ByteArray?) {
        this.updateFields(
            "setProfilePictureBlobId",
            { originalData -> !originalData.profilePictureBlobId.contentEquals(blobId) },
            { originalData -> originalData.copy(profilePictureBlobId = blobId) },
            ::updateDatabase,
            ::notifyDeprecatedOnModifiedListeners
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
    internal fun refreshFromDb(token: RepositoryToken) {
        logger.info("Refresh from database")
        synchronized(this) {
            if (mutableData.value == null) {
                logger.warn("Cannot refresh deleted ${this.modelName} from DB")
                return
            }
            val dbContact = databaseBackend.getContactByIdentity(identity) ?: return
            val newData = ContactModelDataFactory().toDataType(dbContact)
            runtimeAssert(
                newData.identity == identity,
                "Cannot update contact model with data for different identity: ${newData.identity} != $identity"
            )
            mutableData.value = newData
        }
    }

    /**
     * Mark this model as deleted. If [fromDatabase] is true, then the entry will be deleted
     * from the database as well.
     *
     * Note: This method may only be called by the repository! To delete a contact model, call the
     * appropriate method on the [ContactModelRepository].
     *
     * @throws [ModelDeletedException] if model was already marked as deleted.
     */
    internal fun delete(token: RepositoryToken, fromDatabase: Boolean) {
        logger.info("Delete")
        synchronized(this) {
            ensureNotDeleted(mutableData.value, "delete")
            if (fromDatabase) {
                databaseBackend.deleteContactByIdentity(identity)
            }
            mutableData.value = null
        }
        if (fromDatabase) {
            ListenerManager.contactListeners.handle { it.onRemoved(identity) }
        }
    }

    private fun updateDatabase(updatedData: ContactModelData) {
        databaseBackend.updateContact(ContactModelDataFactory().toDbType(updatedData))
    }

    /**
     * Synchronously notify contact change listeners.
     */
    private fun notifyDeprecatedOnModifiedListeners(data: ContactModelData) {
        ListenerManager.contactListeners.handle { it.onModified(data.identity) }
    }
}

class ContactModelDataFactory : ModelDataFactory<ContactModelData, DbContact> {
    override fun toDbType(value: ContactModelData): DbContact = DbContact(
        value.identity,
        value.publicKey,
        value.createdAt,
        value.firstName,
        value.lastName,
        value.nickname,
        value.colorIndex,
        value.verificationLevel,
        value.workVerificationLevel,
        value.identityType,
        value.acquaintanceLevel,
        value.activityState,
        value.syncState,
        value.featureMask,
        value.readReceiptPolicy,
        value.typingIndicatorPolicy,
        value.androidContactLookupKey,
        value.localAvatarExpires,
        value.isRestored,
        value.profilePictureBlobId,
    )

    override fun toDataType(value: DbContact): ContactModelData = ContactModelData(
        value.identity,
        value.publicKey,
        value.createdAt,
        value.firstName,
        value.lastName,
        value.nickname,
        value.colorIndex,
        value.verificationLevel,
        value.workVerificationLevel,
        value.identityType,
        value.acquaintanceLevel,
        value.activityState,
        value.syncState,
        value.featureMask,
        value.readReceiptPolicy,
        value.typingIndicatorPolicy,
        value.androidContactLookupKey,
        value.localAvatarExpires,
        value.isRestored,
        value.profilePictureBlobId,
    )
}
