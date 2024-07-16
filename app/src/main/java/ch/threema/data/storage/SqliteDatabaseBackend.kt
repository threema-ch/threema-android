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

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.IntRange
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.util.Date

class DatabaseException(message: String) : RuntimeException(message)

/**
 * Returns the value of the requested column as a Date, assuming that it contains
 * the unix timestamp in milliseconds as a numeric value.
 *
 * If that is not the case, an exception is thrown.
 */
fun Cursor.getDate(@IntRange(from = 0) columIndex: Int): Date {
    val timestampMs = this.getLong(columIndex)
    return Date(timestampMs)
}

/**
 * Returns the value of the requested column as a Date, assuming that it contains
 * the unix timestamp in milliseconds as a numeric value.
 *
 * If the column contains a null value, then null is returned.
 */
fun Cursor.getDateOrNull(@IntRange(from = 0) columIndex: Int): Date? {
    val timestampMs = this.getLongOrNull(columIndex) ?: return null
    return Date(timestampMs)
}

/**
 * Returns the value of the requested column as a Boolean, assuming that it contains
 * a numeric 1 or 0 value.
 *
 * If the column contains another value than 0 or 1, then null is returned.
 */
fun Cursor.getBooleanOrNull(@IntRange(from = 0) columIndex: Int): Boolean? {
    val numericBool = this.getIntOrNull(columIndex) ?: return null
    return when (numericBool) {
        0 -> false
        1 -> true
        else -> null
    }
}

class SqliteDatabaseBackend(private val sqlite: SupportSQLiteOpenHelper) : DatabaseBackend {
    companion object {
        private val logger = LoggingUtil.getThreemaLogger("data.SqliteDatabaseBackend")
    }

    /**
     * Return the column index for the specified [columName].
     *
     * If the column cannot be found in the [cursor], a [DatabaseException] is thrown.
     */
    @IntRange(from = 0)
    private fun getColumnIndexOrThrow(cursor: Cursor, columName: String): Int {
        val index = cursor.getColumnIndex(columName)
        if (index < 0) {
            throw DatabaseException("Cannot find column with name $columName")
        }
        return index
    }

    override fun createContact(contact: DbContact) {
        val contentValues = ContentValues()
        contentValues.put(ContactModel.COLUMN_IDENTITY, contact.identity)
        contentValues.put(ContactModel.COLUMN_PUBLIC_KEY, contact.publicKey)
        contentValues.put(ContactModel.COLUMN_CREATED_AT, contact.createdAt.time)
        contentValues.update(contact)

        sqlite.writableDatabase.insert(ContactModel.TABLE, SQLiteDatabase.CONFLICT_ROLLBACK, contentValues)
    }

    override fun getContactByIdentity(identity: String): DbContact? {
        val cursor = sqlite.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(ContactModel.TABLE)
                .columns(arrayOf(
                    ContactModel.COLUMN_PUBLIC_KEY,
                    ContactModel.COLUMN_CREATED_AT,
                    ContactModel.COLUMN_FIRST_NAME,
                    ContactModel.COLUMN_LAST_NAME,
                    ContactModel.COLUMN_PUBLIC_NICK_NAME,
                    ContactModel.COLUMN_ID_COLOR_INDEX,
                    ContactModel.COLUMN_VERIFICATION_LEVEL,
                    ContactModel.COLUMN_IS_WORK,
                    ContactModel.COLUMN_TYPE,
                    ContactModel.COLUMN_ACQUAINTANCE_LEVEL,
                    ContactModel.COLUMN_STATE,
                    ContactModel.COLUMN_SYNC_STATE,
                    ContactModel.COLUMN_FEATURE_MASK,
                    ContactModel.COLUMN_READ_RECEIPTS,
                    ContactModel.COLUMN_TYPING_INDICATORS,
                    ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY,
                    ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES,
                    ContactModel.COLUMN_IS_RESTORED,
                    ContactModel.COLUMN_PROFILE_PIC_BLOB_ID,
                ))
                .selection("${ContactModel.COLUMN_IDENTITY} = ?", arrayOf(identity))
                .create()
        )
        if (!cursor.moveToFirst()) {
            return null
        }

        val publicKey = cursor.getBlob(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_PUBLIC_KEY))
        val createdAt = cursor.getDate(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_CREATED_AT))
        val firstName = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_FIRST_NAME)) ?: ""
        val lastName = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_LAST_NAME)) ?: ""
        val nickname = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_PUBLIC_NICK_NAME))
        var colorIndex = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_ID_COLOR_INDEX))
        val verificationLevelRaw = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_VERIFICATION_LEVEL))
        val isWorkVerifiedRaw = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_IS_WORK))
        val identityTypeRaw = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_TYPE))
        val acquaintanceLevelRaw = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_ACQUAINTANCE_LEVEL))
        val activityStateRaw = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_STATE))
        val syncStateRaw = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_SYNC_STATE))
        var featureMask = cursor.getLong(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_FEATURE_MASK))
        val readReceipts = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_READ_RECEIPTS))
        val typingIndicators = cursor.getInt(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_TYPING_INDICATORS))
        val androidContactLookupKey = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY))
        val localAvatarExpires = cursor.getDateOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES))
        val isRestored = cursor.getBooleanOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_IS_RESTORED)) ?: false
        val profilePictureBlobId = cursor.getBlobOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_PROFILE_PIC_BLOB_ID))

        // Validation and mapping
        if (colorIndex < 0 || colorIndex > 255) {
            logger.warn("colorIndex value out of range: {}. Falling back to 0.", colorIndex)
            colorIndex = 0
        }
        val verificationLevel = when (verificationLevelRaw) {
            0 -> VerificationLevel.UNVERIFIED
            1 -> VerificationLevel.SERVER_VERIFIED
            2 -> VerificationLevel.FULLY_VERIFIED
            else -> {
                logger.warn("verificationLevel value out of range: {}. Falling back to UNVERIFIED.", verificationLevelRaw)
                VerificationLevel.UNVERIFIED
            }
        }
        val workVerificationLevel = when (isWorkVerifiedRaw) {
            0 -> WorkVerificationLevel.NONE
            1 -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            else -> {
                logger.warn("workVerificationLevel value out of range: {}. Falling back to NONE.", isWorkVerifiedRaw)
                WorkVerificationLevel.NONE
            }
        }
        val identityType = when (identityTypeRaw) {
            0 -> IdentityType.NORMAL
            1 -> IdentityType.WORK
            else -> {
                logger.warn("identityType value out of range: {}. Falling back to NORMAL.", identityTypeRaw)
                IdentityType.NORMAL
            }
        }
        val acquaintanceLevel = when (acquaintanceLevelRaw) {
            0 -> AcquaintanceLevel.DIRECT
            1 -> AcquaintanceLevel.GROUP
            else -> {
                logger.warn("acquaintanceLevel value out of range: {}. Falling back to DIRECT.", acquaintanceLevelRaw)
                AcquaintanceLevel.DIRECT
            }
        }
        val activityState = when (activityStateRaw) {
            "INACTIVE" -> ContactModel.State.INACTIVE
            "INVALID" -> ContactModel.State.INVALID
            "ACTIVE" -> ContactModel.State.ACTIVE
            "TEMPORARY" -> ContactModel.State.ACTIVE // Legacy state, see !276
            else -> {
                logger.warn("activityState value out of range: {}. Falling back to ACTIVE.", activityStateRaw)
                ContactModel.State.ACTIVE
            }
        }
        val syncState = when (syncStateRaw) {
            0 -> ContactSyncState.INITIAL
            1 -> ContactSyncState.IMPORTED
            2 -> ContactSyncState.CUSTOM
            else -> {
                logger.warn("syncState value out of range: {}. Falling back to INITIAL.", syncStateRaw)
                ContactSyncState.INITIAL
            }
        }
        if (featureMask < 0) {
            logger.warn("featureMask value out of range: {}. Falling back to 0.", featureMask)
            featureMask = 0
        }
        val readReceiptPolicy = when (readReceipts) {
            0 -> ReadReceiptPolicy.DEFAULT
            1 -> ReadReceiptPolicy.SEND
            2 -> ReadReceiptPolicy.DONT_SEND
            else -> {
                logger.warn("readReceipts value out of range: {}. Falling back to DEFAULT.", typingIndicators)
                ReadReceiptPolicy.DEFAULT
            }
        }
        val typingIndicatorPolicy = when (typingIndicators) {
            0 -> TypingIndicatorPolicy.DEFAULT
            1 -> TypingIndicatorPolicy.SEND
            2 -> TypingIndicatorPolicy.DONT_SEND
            else -> {
                logger.warn("typingIndicators value out of range: {}. Falling back to DEFAULT.", typingIndicators)
                TypingIndicatorPolicy.DEFAULT
            }
        }

        return DbContact(
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
            featureMask.toULong(),
            readReceiptPolicy,
            typingIndicatorPolicy,
            androidContactLookupKey,
            localAvatarExpires,
            isRestored,
            profilePictureBlobId,
        )
    }

    override fun updateContact(contact: DbContact) {
        val contentValues = ContentValues()
        contentValues.update(contact)

        sqlite.writableDatabase.update(
            table = ContactModel.TABLE,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
            whereClause = "${ContactModel.COLUMN_IDENTITY} = ?",
            whereArgs = arrayOf(contact.identity),
        )
    }

    private fun ContentValues.update(contact: DbContact) {
        // Note: Identity, public key and created at cannot be updated.
        put(ContactModel.COLUMN_FIRST_NAME, contact.firstName)
        put(ContactModel.COLUMN_LAST_NAME, contact.lastName)
        put(ContactModel.COLUMN_PUBLIC_NICK_NAME, contact.nickname)
        put(ContactModel.COLUMN_ID_COLOR_INDEX, contact.colorIndex.toInt())
        put(ContactModel.COLUMN_VERIFICATION_LEVEL, contact.verificationLevel.code)
        put(ContactModel.COLUMN_IS_WORK, when (contact.workVerificationLevel) {
            WorkVerificationLevel.NONE -> 0
            WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> 1
        })
        put(ContactModel.COLUMN_TYPE, when (contact.identityType) {
            IdentityType.NORMAL -> 0
            IdentityType.WORK -> 1
        })
        put(ContactModel.COLUMN_ACQUAINTANCE_LEVEL, when (contact.acquaintanceLevel) {
            AcquaintanceLevel.DIRECT -> 0
            AcquaintanceLevel.GROUP -> 1
        })
        put(ContactModel.COLUMN_STATE, when (contact.activityState) {
            ContactModel.State.ACTIVE -> "ACTIVE"
            ContactModel.State.INACTIVE -> "INACTIVE"
            ContactModel.State.INVALID -> "INVALID"
        })
        put(ContactModel.COLUMN_SYNC_STATE, when (contact.syncState) {
            ContactSyncState.INITIAL -> 0
            ContactSyncState.IMPORTED -> 1
            ContactSyncState.CUSTOM -> 2
        })
        put(ContactModel.COLUMN_FEATURE_MASK, contact.featureMask.toLong())
        put(ContactModel.COLUMN_READ_RECEIPTS, when (contact.readReceiptPolicy) {
            ReadReceiptPolicy.DEFAULT -> 0
            ReadReceiptPolicy.SEND -> 1
            ReadReceiptPolicy.DONT_SEND -> 2
        })
        put(ContactModel.COLUMN_TYPING_INDICATORS, when (contact.typingIndicatorPolicy) {
            TypingIndicatorPolicy.DEFAULT -> 0
            TypingIndicatorPolicy.SEND -> 1
            TypingIndicatorPolicy.DONT_SEND -> 2
        })
        put(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY, contact.androidContactLookupKey)
        put(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES, contact.localAvatarExpires?.time)
        put(ContactModel.COLUMN_IS_RESTORED, contact.isRestored)
        put(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID, contact.profilePictureBlobId)
    }

    override fun deleteContactByIdentity(identity: String): Boolean {
        return sqlite.writableDatabase.delete(
            table = ContactModel.TABLE,
            whereClause = "${ContactModel.COLUMN_IDENTITY} = ?",
            whereArgs = arrayOf(identity),
        ) > 0
    }
}
