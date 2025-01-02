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

package ch.threema.data.storage

import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel.UserState
import java.util.Date

// This file contains the types used in the database abstraction layer.

data class DbContact(
    /** The contact identity string. Must be 8 characters long. */
    val identity: String,
    /** The 32-byte public key of the contact. */
    val publicKey: ByteArray,
    /** Timestamp when this contact was added to the contact list. */
    val createdAt: Date,
    /** First name. */
    val firstName: String,
    /** Last name. */
    val lastName: String,
    /** Public nickname. */
    val nickname: String?,
    /** Color index (0-255). */
    val colorIndex: UByte,
    /** Verification level. */
    val verificationLevel: VerificationLevel,
    /** Threema Work verification level. */
    val workVerificationLevel: WorkVerificationLevel,
    /** Identity type (regular / work). */
    val identityType: IdentityType,
    /** Acquaintance level (direct / group). */
    val acquaintanceLevel: ContactModel.AcquaintanceLevel,
    /** Activity state (active / inactive / invalid). */
    val activityState: IdentityState,
    /** Contact sync state. */
    val syncState: ContactSyncState,
    /** Feature mask. */
    val featureMask: ULong,
    /** Read receipt policy. */
    val readReceiptPolicy: ReadReceiptPolicy,
    /** Typing indicator policy. */
    val typingIndicatorPolicy: TypingIndicatorPolicy,
    // TODO(ANDR-2998): Notification trigger policy override
    // TODO(ANDR-2998): Notification sound policy override
    /** Android contact lookup key. */
    val androidContactLookupKey: String?,
    /** Local avatar expiration date. */
    val localAvatarExpires: Date?,
    /** Whether this contact has been restored from backup. */
    val isRestored: Boolean,
    /** BlobId of the latest profile picture that was sent to this contact. */
    val profilePictureBlobId: ByteArray?,
    val jobTitle: String?,
    val department: String?
)

data class DbGroup(
    /** The group creator identity string. Must be 8 characters long. */
    val creatorIdentity: String,
    /**
     * The group id of the group. It is the hex string representation of the group id as little
     * endian byte array.
     */
    val groupId: String,
    /** The group name. */
    val name: String?,
    /** The creation date. */
    val createdAt: Date,
    /** Currently not used. Might be used for periodic group sync. TODO(SE-146) */
    val synchronizedAt: Date?,
    /** Last update flag. */
    val lastUpdate: Date?,
    /** Deleted flag. */
    val deleted: Boolean,
    /** Is archived flag. */
    val isArchived: Boolean,
    /** The color index. */
    val colorIndex: UByte,
    /** The group description. */
    val groupDescription: String?,
    /** The group description changed timestamp. */
    val groupDescriptionChangedAt: Date?,
    /** The group members' identities. */
    val members: Set<String>,
    /** The group user state. */
    val userState: UserState,
)

data class DbEditHistoryEntry(
    /** The unique id used as primary key. */
    val uid: Int = 0,
    /** The uid of the edited message referencing the [ch.threema.storage.models.AbstractMessageModel.COLUMN_UID] */
    val messageUid: String,
    /** The id of the edited message referencing the [ch.threema.storage.models.AbstractMessageModel.COLUMN_ID]
     *  Can be identical for different classes of messages that are stored in different tables
     *  This is only used as foreign key because uid is not actually constrained as unique
     */
    val messageId: Int,
    /** The former text of the edited message. */
    val text: String?,
    /** Timestamp when the message was edited and hence the entry created. */
    val editedAt: Date
) {
    companion object {
        const val COLUMN_UID = "uid"
        const val COLUMN_MESSAGE_UID = "messageUid"
        const val COLUMN_MESSAGE_ID = "messageId"
        const val COLUMN_TEXT = "text"
        const val COLUMN_EDITED_AT = "editedAt"
    }
}
