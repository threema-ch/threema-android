/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.domain.models

import ch.threema.base.utils.Utils
import ch.threema.common.toHexString
import ch.threema.domain.types.Identity
import java.util.Objects

const val CONTACT_NAME_MAX_LENGTH_BYTES = 256

/**
 * Base class for contacts.
 */
open class Contact(
    val identity: Identity,
    val publicKey: ByteArray,
    @JvmField var verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
) {
    var firstName: String? = null
        set(value) {
            field = Utils.truncateUTF8String(value, CONTACT_NAME_MAX_LENGTH_BYTES)
        }
    var lastName: String? = null
        set(value) {
            field = Utils.truncateUTF8String(value, CONTACT_NAME_MAX_LENGTH_BYTES)
        }

    val hasFirstOrLastName: Boolean
        get() = !firstName.isNullOrBlank() || !lastName.isNullOrBlank()

    override fun toString(): String = buildString {
        append(identity)
        append(" (")
        append(publicKey.toHexString())
        append(")")
        if (firstName != null || lastName != null) {
            append(": ")
            append(firstName)
            append(" ")
            append(lastName)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return identity == other.identity && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = Objects.hash(identity)
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

/**
 * This represents a contact with reduced properties. Note that this is mainly used for caching. A
 * basic contact may be a contact that is not present in the database. The existence of a
 * [BasicContact] does therefore not mean that it is a known contact.
 */
open class BasicContact(
    identity: Identity,
    publicKey: ByteArray,
    val featureMask: ULong,
    val identityState: IdentityState,
    val identityType: IdentityType,
) : Contact(identity, publicKey) {
    companion object {
        @JvmStatic
        fun javaCreate(
            identity: Identity,
            publicKey: ByteArray,
            featureMask: Long,
            identityState: IdentityState,
            identityType: IdentityType,
        ): BasicContact = BasicContact(
            identity,
            publicKey,
            featureMask.toULong(),
            identityState,
            identityType,
        )
    }
}

enum class IdentityType {
    /**
     * A normal Threema identity.
     */
    NORMAL,

    /**
     * An identity using Threema Work.
     */
    WORK,
}

/**
 * This represents the identity state. Note that the variants must not be renamed as they are stored
 * as string in the database.
 */
enum class IdentityState(val value: Int) {
    /**
     * Contact is active.
     */
    ACTIVE(0),

    /**
     * Contact is inactive.
     */
    INACTIVE(1),

    /**
     * Contact does not have a valid Threema-ID, or the ID was revoked.
     */
    INVALID(2),
}

enum class WorkVerificationLevel {
    NONE,

    /**
     * Contact is "work verified", i.e. has been added to the contact list in the management
     * cockpit. These contacts are symbolized by a blue verification level.
     */
    WORK_SUBSCRIPTION_VERIFIED,
}

enum class ContactSyncState {
    /**
     * The contact data has not been imported and has not been edited by the user either.
     */
    INITIAL,

    /**
     * The contact data has been imported (e.g. via a local address book and an identity link).
     * In this state, subsequent contact synchronisations must not alter the contact's data.
     */
    IMPORTED,

    /**
     * The contact data has been edited by the user.
     * In this state, subsequent contact synchronisations must not alter the contact's data.
     */
    CUSTOM,
}

enum class ReadReceiptPolicy {
    /**
     * Default behavior (as configured in the global settings).
     */
    DEFAULT,

    /**
     * Send read receipt when an unread message has been read.
     */
    SEND,

    /**
     * Don't send read receipts.
     */
    DONT_SEND,
}

enum class TypingIndicatorPolicy {
    /**
     * Default behavior (as configured in the global settings).
     */
    DEFAULT,

    /**
     * Send typing indicator when a message is being composed.
     */
    SEND,

    /**
     * Don't send typing indicators.
     */
    DONT_SEND,
}
