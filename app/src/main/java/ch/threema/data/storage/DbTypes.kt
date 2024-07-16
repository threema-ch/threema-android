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
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel
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
    val activityState: ContactModel.State,
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
)
