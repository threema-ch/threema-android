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
import android.database.sqlite.SQLiteException
import androidx.annotation.IntRange
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.CursorHelper
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.GroupMemberModel
import ch.threema.storage.models.GroupModel
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.util.Collections
import java.util.Date

class DatabaseException internal constructor(message: String, cause: Throwable?) :
    RuntimeException(message, cause) {
    internal constructor(message: String) : this(message, null)
}

/**
 * Returns the value of the requested column as a Date, assuming that it contains
 * the unix timestamp in milliseconds as a numeric value.
 *
 * If that is not the case, an exception is thrown.
 */
fun Cursor.getDate(@IntRange(from = 0) columnIndex: Int): Date {
    val timestampMs = this.getLong(columnIndex)
    return Date(timestampMs)
}

/**
 * Returns the value of the requested column as a Date, assuming that it contains
 * the unix timestamp in milliseconds as a numeric value.
 *
 * If the column contains a null value, then null is returned.
 */
private fun Cursor.getDateOrNull(@IntRange(from = 0) columnIndex: Int): Date? {
    val timestampMs = this.getLongOrNull(columnIndex) ?: return null
    return Date(timestampMs)
}

/**
 * Returns the value of the requested column as a Date, assuming that it contains the string
 * representation of the date as defined in [CursorHelper.dateAsStringFormat].
 *
 * @throws NullPointerException if the column contains a null value
 */
private fun Cursor.getDateByString(@IntRange(from = 0) columnIndex: Int): Date {
    val dateString = this.getString(columnIndex)
    return CursorHelper.dateAsStringFormat.get()!!.parse(dateString)!!
}

/**
 * Returns the value of the requested column as a Date, assuming that it contains the string
 * representation of the date as defined in [CursorHelper.dateAsStringFormat].
 *
 * If the column contains a null value, then null is returned
 */
private fun Cursor.getDateByStringOrNull(@IntRange(from = 0) columnIndex: Int): Date? {
    val dateString = this.getStringOrNull(columnIndex) ?: return null
    return CursorHelper.dateAsStringFormat.get()?.parse(dateString)
}

/**
 * Returns the value of the requested column as a Boolean, assuming that it contains a numeric value
 * of 0 (false) or not 0 (true).
 */
private fun Cursor.getBoolean(@IntRange(from = 0) columnIndex: Int): Boolean {
    val numericBool = this.getInt(columnIndex)
    return when (numericBool) {
        0 -> false
        else -> true
    }
}

/**
 * Returns the value of the requested column as an UByte, assuming that it contains a numeric value
 * between 0 and [UByte.MAX_VALUE]. Otherwise, an [IllegalArgumentException] is thrown.
 */
private fun Cursor.getUByte(@IntRange(from = 0) columnIndex: Int): UByte {
    val numberAsInt = this.getInt(columnIndex)
    if (numberAsInt < 0 || numberAsInt > UByte.MAX_VALUE.toInt()) {
        throw IllegalArgumentException("Value '$numberAsInt' at index $columnIndex is not an ubyte")
    }
    return numberAsInt.toUByte()
}

/**
 * Returns the value of the requested column as a Boolean, assuming that it contains
 * a numeric 1 or 0 value.
 *
 * If the column contains another value than 0 or 1, then null is returned.
 */
private fun Cursor.getBooleanOrNull(@IntRange(from = 0) columnIndex: Int): Boolean? {
    val numericBool = this.getIntOrNull(columnIndex) ?: return null
    return when (numericBool) {
        0 -> false
        1 -> true
        else -> null
    }
}

/**
 * Returns the date as string. If conversion does not work, null is returned.
 */
private fun Date.toDateStringOrNull(): String? = CursorHelper.dateAsStringFormat.get()?.format(this)

private val logger = LoggingUtil.getThreemaLogger("data.SqliteDatabaseBackend")

/**
 * Return the column index for the specified [columName].
 *
 * If the column cannot be found in the [cursor], a [DatabaseException] is thrown.
 */
@IntRange(from = 0)
fun getColumnIndexOrThrow(cursor: Cursor, columName: String): Int {
    val index = cursor.getColumnIndex(columName)
    if (index < 0) {
        throw DatabaseException("Cannot find column with name $columName")
    }
    return index
}

class SqliteDatabaseBackend(private val sqlite: SupportSQLiteOpenHelper) : DatabaseBackend {

    override fun createContact(contact: DbContact) {
        val contentValues = ContentValues()
        contentValues.put(ContactModel.COLUMN_IDENTITY, contact.identity)
        contentValues.put(ContactModel.COLUMN_PUBLIC_KEY, contact.publicKey)
        contentValues.put(ContactModel.COLUMN_CREATED_AT, contact.createdAt.time)
        contentValues.update(contact)

        sqlite.writableDatabase.insert(
            ContactModel.TABLE,
            SQLiteDatabase.CONFLICT_ROLLBACK,
            contentValues
        )
    }

    override fun getContactByIdentity(identity: String): DbContact? {
        val cursor = sqlite.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(ContactModel.TABLE)
                .columns(
                    arrayOf(
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
                        ContactModel.COLUMN_JOB_TITLE,
                        ContactModel.COLUMN_DEPARTMENT,
                    )
                )
                .selection("${ContactModel.COLUMN_IDENTITY} = ?", arrayOf(identity))
                .create()
        )
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        val publicKey = cursor.getBlob(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_PUBLIC_KEY))
        val createdAt = cursor.getDate(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_CREATED_AT))
        val firstName = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_FIRST_NAME))
            ?: ""
        val lastName = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_LAST_NAME))
            ?: ""
        val nickname = cursor.getStringOrNull(
            getColumnIndexOrThrow(
                cursor,
                ContactModel.COLUMN_PUBLIC_NICK_NAME
            )
        )
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
        val androidContactLookupKey = cursor.getStringOrNull(
            getColumnIndexOrThrow(
                cursor,
                ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY
            )
        )
        val localAvatarExpires = cursor.getDateOrNull(
            getColumnIndexOrThrow(
                cursor,
                ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES
            )
        )
        val isRestored = cursor.getBooleanOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_IS_RESTORED))
            ?: false
        val profilePictureBlobId = cursor.getBlobOrNull(
            getColumnIndexOrThrow(
                cursor,
                ContactModel.COLUMN_PROFILE_PIC_BLOB_ID
            )
        )
        val jobTitle = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_JOB_TITLE))
        val department = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, ContactModel.COLUMN_DEPARTMENT))

        cursor.close()

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
                logger.warn(
                    "verificationLevel value out of range: {}. Falling back to UNVERIFIED.",
                    verificationLevelRaw
                )
                VerificationLevel.UNVERIFIED
            }
        }
        val workVerificationLevel = when (isWorkVerifiedRaw) {
            0 -> WorkVerificationLevel.NONE
            1 -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            else -> {
                logger.warn(
                    "workVerificationLevel value out of range: {}. Falling back to NONE.",
                    isWorkVerifiedRaw
                )
                WorkVerificationLevel.NONE
            }
        }
        val identityType = when (identityTypeRaw) {
            0 -> IdentityType.NORMAL
            1 -> IdentityType.WORK
            else -> {
                logger.warn(
                    "identityType value out of range: {}. Falling back to NORMAL.",
                    identityTypeRaw
                )
                IdentityType.NORMAL
            }
        }
        val acquaintanceLevel = when (acquaintanceLevelRaw) {
            0 -> AcquaintanceLevel.DIRECT
            1 -> AcquaintanceLevel.GROUP
            else -> {
                logger.warn(
                    "acquaintanceLevel value out of range: {}. Falling back to DIRECT.",
                    acquaintanceLevelRaw
                )
                AcquaintanceLevel.DIRECT
            }
        }
        val activityState = when (activityStateRaw) {
            "INACTIVE" -> ContactModel.State.INACTIVE
            "INVALID" -> ContactModel.State.INVALID
            "ACTIVE" -> ContactModel.State.ACTIVE
            "TEMPORARY" -> ContactModel.State.ACTIVE // Legacy state, see !276
            else -> {
                logger.warn(
                    "activityState value out of range: {}. Falling back to ACTIVE.",
                    activityStateRaw
                )
                ContactModel.State.ACTIVE
            }
        }
        val syncState = when (syncStateRaw) {
            0 -> ContactSyncState.INITIAL
            1 -> ContactSyncState.IMPORTED
            2 -> ContactSyncState.CUSTOM
            else -> {
                logger.warn(
                    "syncState value out of range: {}. Falling back to INITIAL.",
                    syncStateRaw
                )
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
                logger.warn(
                    "readReceipts value out of range: {}. Falling back to DEFAULT.",
                    typingIndicators
                )
                ReadReceiptPolicy.DEFAULT
            }
        }
        val typingIndicatorPolicy = when (typingIndicators) {
            0 -> TypingIndicatorPolicy.DEFAULT
            1 -> TypingIndicatorPolicy.SEND
            2 -> TypingIndicatorPolicy.DONT_SEND
            else -> {
                logger.warn(
                    "typingIndicators value out of range: {}. Falling back to DEFAULT.",
                    typingIndicators
                )
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
            jobTitle,
            department
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
        put(
            ContactModel.COLUMN_IS_WORK, when (contact.workVerificationLevel) {
                WorkVerificationLevel.NONE -> 0
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> 1
            }
        )
        put(
            ContactModel.COLUMN_TYPE, when (contact.identityType) {
                IdentityType.NORMAL -> 0
                IdentityType.WORK -> 1
            }
        )
        put(
            ContactModel.COLUMN_ACQUAINTANCE_LEVEL, when (contact.acquaintanceLevel) {
                AcquaintanceLevel.DIRECT -> 0
                AcquaintanceLevel.GROUP -> 1
            }
        )
        put(
            ContactModel.COLUMN_STATE, when (contact.activityState) {
                ContactModel.State.ACTIVE -> "ACTIVE"
                ContactModel.State.INACTIVE -> "INACTIVE"
                ContactModel.State.INVALID -> "INVALID"
            }
        )
        put(
            ContactModel.COLUMN_SYNC_STATE, when (contact.syncState) {
                ContactSyncState.INITIAL -> 0
                ContactSyncState.IMPORTED -> 1
                ContactSyncState.CUSTOM -> 2
            }
        )
        put(ContactModel.COLUMN_FEATURE_MASK, contact.featureMask.toLong())
        put(
            ContactModel.COLUMN_READ_RECEIPTS, when (contact.readReceiptPolicy) {
                ReadReceiptPolicy.DEFAULT -> 0
                ReadReceiptPolicy.SEND -> 1
                ReadReceiptPolicy.DONT_SEND -> 2
            }
        )
        put(
            ContactModel.COLUMN_TYPING_INDICATORS, when (contact.typingIndicatorPolicy) {
                TypingIndicatorPolicy.DEFAULT -> 0
                TypingIndicatorPolicy.SEND -> 1
                TypingIndicatorPolicy.DONT_SEND -> 2
            }
        )
        put(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY, contact.androidContactLookupKey)
        put(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES, contact.localAvatarExpires?.time)
        put(ContactModel.COLUMN_IS_RESTORED, contact.isRestored)
        put(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID, contact.profilePictureBlobId)
        put(ContactModel.COLUMN_JOB_TITLE, contact.jobTitle)
        put(ContactModel.COLUMN_DEPARTMENT, contact.department)
    }

    override fun deleteContactByIdentity(identity: String): Boolean {
        return sqlite.writableDatabase.delete(
            table = ContactModel.TABLE,
            whereClause = "${ContactModel.COLUMN_IDENTITY} = ?",
            whereArgs = arrayOf(identity),
        ) > 0
    }

    /**
     * Create a group.
     *
     * @throws DatabaseException if the constraints fail while inserting the group
     */
    override fun createGroup(group: DbGroup) {
        val contentValues = ContentValues()
        contentValues.put(GroupModel.COLUMN_CREATOR_IDENTITY, group.creatorIdentity)
        contentValues.put(GroupModel.COLUMN_API_GROUP_ID, group.groupId)
        contentValues.put(GroupModel.COLUMN_CREATED_AT, group.createdAt.toDateStringOrNull())
        contentValues.update(group)

        val rowId = try {
            sqlite.writableDatabase.insert(
                GroupModel.TABLE,
                SQLiteDatabase.CONFLICT_ROLLBACK,
                contentValues
            )
        } catch (e: SQLiteException) {
            throw DatabaseException("Could not insert group", e)
        }

        if (rowId < 0) {
            throw DatabaseException("Could not insert group")
        }

        updateGroupMembers(rowId, group.members)
    }

    override fun getGroupByLocalGroupDbId(localDbId: Long): DbGroup? {
        return getGroup {
            it.selection("${GroupModel.COLUMN_ID} = ?", arrayOf(localDbId))
        }
    }

    override fun getGroupByGroupIdentity(groupIdentity: GroupIdentity): DbGroup? {
        val creatorIdentitySelection = "${GroupModel.COLUMN_CREATOR_IDENTITY} = ?"
        val groupIdSelection = "${GroupModel.COLUMN_API_GROUP_ID} = ?"
        val creatorIdentitySelectionArg = groupIdentity.creatorIdentity
        val groupIdSelectionArg = groupIdentity.groupIdHexString
        val selection = "$creatorIdentitySelection AND $groupIdSelection"
        val selectionArgs = arrayOf(creatorIdentitySelectionArg, groupIdSelectionArg)

        return getGroup {
            it.selection(selection, selectionArgs)
        }
    }

    private fun getGroup(addSelection: (SupportSQLiteQueryBuilder) -> Unit): DbGroup? {
        sqlite.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupModel.TABLE)
                .columns(
                    arrayOf(
                        GroupModel.COLUMN_ID,
                        GroupModel.COLUMN_API_GROUP_ID,
                        GroupModel.COLUMN_NAME,
                        GroupModel.COLUMN_CREATOR_IDENTITY,
                        GroupModel.COLUMN_CREATED_AT,
                        GroupModel.COLUMN_SYNCHRONIZED_AT,
                        GroupModel.COLUMN_LAST_UPDATE,
                        GroupModel.COLUMN_DELETED,
                        GroupModel.COLUMN_IS_ARCHIVED,
                        GroupModel.COLUMN_COLOR_INDEX,
                        GroupModel.COLUMN_GROUP_DESC,
                        GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP,
                    )
                )
                .apply(addSelection)
                .create()
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val localDbId = cursor.getLong(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_ID))
            val creatorIdentity = cursor.getString(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_CREATOR_IDENTITY))
            val groupId = cursor.getString(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_API_GROUP_ID))
            val name = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_NAME))
            val createdAt = cursor.getDateByString(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_CREATED_AT))
            val synchronizedAt = cursor.getDateOrNull(
                getColumnIndexOrThrow(
                    cursor,
                    GroupModel.COLUMN_SYNCHRONIZED_AT
                )
            )
            val lastUpdate = cursor.getDateOrNull(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_LAST_UPDATE))
            val deleted = cursor.getBoolean(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_DELETED))
            val isArchived = cursor.getBoolean(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_IS_ARCHIVED))
            val colorIndex = cursor.getUByte(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_COLOR_INDEX))
            val groupDesc = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, GroupModel.COLUMN_GROUP_DESC))
            val groupDescChangedAt = cursor.getDateByStringOrNull(
                getColumnIndexOrThrow(
                    cursor,
                    GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP
                )
            )
            val members = getGroupMembers(localDbId)

            return DbGroup(
                creatorIdentity = creatorIdentity,
                groupId = groupId,
                name = name,
                createdAt = createdAt,
                synchronizedAt = synchronizedAt,
                lastUpdate = lastUpdate,
                deleted = deleted,
                isArchived = isArchived,
                colorIndex = colorIndex,
                groupDescription = groupDesc,
                groupDescriptionChangedAt = groupDescChangedAt,
                members = members,
            )
        }
    }

    override fun updateGroup(group: DbGroup) {
        val localGroupDbId = getLocalGroupDbId(group)

        // First update general group information
        val contentValues = ContentValues().apply { update(group) }

        sqlite.writableDatabase.update(
            table = GroupModel.TABLE,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
            whereClause = "${GroupModel.COLUMN_ID} = ?",
            whereArgs = arrayOf(localGroupDbId)
        )

        // Then update group members
        updateGroupMembers(localGroupDbId, group.members)
    }

    private fun ContentValues.update(group: DbGroup) {
        // Note: creator identity, group id, and created at cannot be updated
        put(GroupModel.COLUMN_NAME, group.name)
        put(GroupModel.COLUMN_LAST_UPDATE, group.lastUpdate?.time)
        put(GroupModel.COLUMN_SYNCHRONIZED_AT, group.synchronizedAt?.time)
        put(GroupModel.COLUMN_DELETED, group.deleted)
        put(GroupModel.COLUMN_IS_ARCHIVED, group.isArchived)
        put(GroupModel.COLUMN_COLOR_INDEX, group.colorIndex.toInt())
        put(GroupModel.COLUMN_GROUP_DESC, group.groupDescription)
        put(
            GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP,
            group.groupDescriptionChangedAt?.toDateStringOrNull()
        )
    }

    private fun getLocalGroupDbId(group: DbGroup): Long {
        val creatorIdentitySelection = "${GroupModel.COLUMN_CREATOR_IDENTITY} = ?"
        val groupIdSelection = "${GroupModel.COLUMN_API_GROUP_ID} = ?"
        val creatorIdentitySelectionArg = group.creatorIdentity
        val groupIdSelectionArg = group.groupId
        val selection = "$creatorIdentitySelection AND $groupIdSelection"
        val selectionArgs = arrayOf(creatorIdentitySelectionArg, groupIdSelectionArg)

        sqlite.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupModel.TABLE)
                .columns(arrayOf(GroupModel.COLUMN_ID))
                .selection(selection, selectionArgs)
                .create()
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw DatabaseException("Could not find a group with creator ${group.creatorIdentity} and id ${group.groupId}")
            }

            return cursor.getLong(cursor.getColumnIndexOrThrow(GroupModel.COLUMN_ID))
        }
    }

    private fun getGroupMembers(localDbId: Long): Set<String> {
        sqlite.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupMemberModel.TABLE)
                .columns(arrayOf(GroupMemberModel.COLUMN_IDENTITY))
                .selection("${GroupMemberModel.COLUMN_GROUP_ID} = ?", arrayOf(localDbId))
                .create()
        ).use { cursor ->
            val members = mutableSetOf<String>()

            while (cursor.moveToNext()) {
                members.add(
                    cursor.getString(
                        getColumnIndexOrThrow(
                            cursor,
                            GroupMemberModel.COLUMN_IDENTITY
                        )
                    )
                )
            }

            return Collections.unmodifiableSet(members)
        }
    }

    private fun updateGroupMembers(localDbId: Long, members: Set<String>) {
        // First remove all members that are not part of the group anymore
        val whereGroupId = "${GroupMemberModel.COLUMN_GROUP_ID} = ?"
        val whereNotMember = "${GroupMemberModel.COLUMN_IDENTITY} NOT IN ( ${
            members.joinToString(separator = " , ") { "?" }
        } )"

        sqlite.writableDatabase.delete(
            GroupMemberModel.TABLE,
            "$whereGroupId AND $whereNotMember",
            (listOf(localDbId) + members).toTypedArray(),
        )

        // Add all members (if not already exists)
        val contentValuesList = members.map { memberIdentity ->
            ContentValues().apply {
                put(GroupMemberModel.COLUMN_IDENTITY, memberIdentity)
                put(GroupMemberModel.COLUMN_GROUP_ID, localDbId)
            }
        }

        contentValuesList.forEach { contentValues ->
            sqlite.writableDatabase.insert(
                GroupMemberModel.TABLE,
                SQLiteDatabase.CONFLICT_FAIL,
                contentValues,
            )
        }
    }
}
