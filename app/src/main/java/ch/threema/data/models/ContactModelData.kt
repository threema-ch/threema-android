/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

import ch.threema.app.utils.ContactUtil
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.UnsignedHelper
import ch.threema.common.isNotNullOrBlank
import ch.threema.common.plus
import ch.threema.common.toDate
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import java.math.BigInteger
import java.time.Instant
import java.util.Date
import kotlin.time.Duration

/**
 * Immutable contact model data.
 *
 * TODO(ANDR-2998): Notification sound policy override
 */
data class ContactModelData(
    /** The contact identity string. Must be 8 characters long. */
    @JvmField val identity: Identity,
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
    /** Id color. */
    val idColor: IdColor = IdColor.ofIdentity(identity),
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
    @JvmField val androidContactLookupInfo: AndroidContactLookupInfo?,
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
         *
         * @throws IllegalArgumentException the feature mask is negative or more than 64 bits,
         * or the public key is not [NaCl.PUBLIC_KEY_BYTES] long.
         */
        @JvmStatic
        fun javaCreate(
            identity: Identity,
            publicKey: ByteArray,
            createdAt: Date,
            firstName: String,
            lastName: String,
            nickname: String?,
            idColor: IdColor,
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
            androidContactLookupInfo: AndroidContactLookupInfo?,
            localAvatarExpires: Date?,
            isRestored: Boolean,
            profilePictureBlobId: ByteArray?,
            jobTitle: String?,
            department: String?,
            notificationTriggerPolicyOverride: Long?,
        ): ContactModelData {
            require(featureMask.signum() >= 0 && featureMask.bitLength() <= 64) { "featureMask must be between 0 and 2^64" }
            require(publicKey.size == NaCl.PUBLIC_KEY_BYTES) { "public key must be ${NaCl.PUBLIC_KEY_BYTES} long" }
            return ContactModelData(
                identity = identity,
                publicKey = publicKey,
                createdAt = createdAt,
                firstName = firstName,
                lastName = lastName,
                nickname = nickname,
                idColor = idColor,
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
                androidContactLookupInfo = androidContactLookupInfo,
                localAvatarExpires = localAvatarExpires,
                isRestored = isRestored,
                profilePictureBlobId = profilePictureBlobId,
                jobTitle = jobTitle,
                department = department,
                notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
            )
        }
    }

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

    fun getShortName(): String = when {
        firstName.isNotBlank() -> firstName
        lastName.isNotBlank() -> lastName
        nickname.isNotNullOrBlank() && nickname != identity -> "~$nickname"
        else -> identity
    }

    /**
     * Return whether or not this contact is linked to an Android contact.
     */
    fun isLinkedToAndroidContact(): Boolean = this.androidContactLookupInfo != null

    /**
     * Check if the avatar expires within the given [tolerance]. If no [localAvatarExpires] is set, the avatar is also
     * considered as expired.
     */
    @JvmOverloads
    fun isAvatarExpired(now: Instant = Instant.now(), tolerance: Duration = Duration.ZERO): Boolean =
        localAvatarExpires?.before((now + tolerance).toDate()) ?: true

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
        if (idColor != other.idColor) return false
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
        if (androidContactLookupInfo != other.androidContactLookupInfo) return false
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
        result = 31 * result + idColor.hashCode()
        result = 31 * result + verificationLevel.hashCode()
        result = 31 * result + workVerificationLevel.hashCode()
        result = 31 * result + identityType.hashCode()
        result = 31 * result + acquaintanceLevel.hashCode()
        result = 31 * result + activityState.hashCode()
        result = 31 * result + syncState.hashCode()
        result = 31 * result + featureMask.hashCode()
        result = 31 * result + readReceiptPolicy.hashCode()
        result = 31 * result + typingIndicatorPolicy.hashCode()
        result = 31 * result + (androidContactLookupInfo?.hashCode() ?: 0)
        result = 31 * result + (localAvatarExpires?.hashCode() ?: 0)
        result = 31 * result + isRestored.hashCode()
        result = 31 * result + (profilePictureBlobId?.contentHashCode() ?: 0)
        result = 31 * result + notificationTriggerPolicyOverride.hashCode()
        return result
    }
}
