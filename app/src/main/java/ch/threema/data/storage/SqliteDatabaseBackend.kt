package ch.threema.data.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.annotation.IntRange
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.IdentityString
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.DatabaseUtil
import ch.threema.storage.DbAvailabilityStatus
import ch.threema.storage.buildContentValues
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.IncomingGroupSyncRequestLogModel
import ch.threema.storage.models.group.GroupMemberModel
import ch.threema.storage.models.group.GroupMessageModel
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.storage.runTransaction
import java.time.Instant
import java.util.Collections
import java.util.Date
import net.zetetic.database.sqlcipher.SQLiteDatabase

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

private val logger = getThreemaLogger("data.SqliteDatabaseBackend")

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

class SqliteDatabaseBackend(
    private val databaseProvider: DatabaseProvider,
    private val identityProvider: IdentityProvider,
) : DatabaseBackend {

    /**
     * @return All existing contacts from the database or an empty list in case of an error while reading.
     */
    override fun getAllContacts(): List<DbContact> {
        return try {
            val contactIdentity = "${ContactModel.TABLE}.${ContactModel.COLUMN_IDENTITY}"
            val availabilityStatusIdentity = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_IDENTITY}"
            val availabilityStatusCategory = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_CATEGORY}"
            val availabilityStatusDescription = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_DESCRIPTION}"
            /*
             * SELECT contacts.*, contact_availability_status.category, contact_availability_status.description
             * FROM contacts LEFT JOIN contact_availability_status ON contacts.identity = contact_availability_status.identity;
             */
            val query = """
                SELECT ${ContactModel.TABLE}.*, $availabilityStatusCategory, $availabilityStatusDescription
                FROM ${ContactModel.TABLE} LEFT JOIN ${DbAvailabilityStatus.TABLE} ON $contactIdentity = $availabilityStatusIdentity;
            """.trimIndent()
            val cursor = databaseProvider.readableDatabase.query(query)
            val dbContacts = mutableListOf<DbContact>()
            cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    dbContacts.add(cursor.mapToDbContact())
                }
            }
            dbContacts
        } catch (exception: Exception) {
            logger.error("Failed to read contacts.", exception)
            emptyList()
        }
    }

    override fun createContact(dbContact: DbContact) {
        require(dbContact.identity.length == ProtocolDefines.IDENTITY_LEN) {
            "Cannot create contact with invalid identity: ${dbContact.identity}"
        }
        require(dbContact.publicKey.size == NaCl.PUBLIC_KEY_BYTES) {
            "Cannot create contact (${dbContact.identity}) with public key of invalid length: ${dbContact.publicKey.size}"
        }
        require(dbContact.identity != identityProvider.getIdentityString()) {
            "Cannot create contact with the user's identity"
        }
        val contentValuesContact = buildContentValues {
            put(ContactModel.COLUMN_IDENTITY, dbContact.identity)
            put(ContactModel.COLUMN_PUBLIC_KEY, dbContact.publicKey)
            put(ContactModel.COLUMN_CREATED_AT, dbContact.createdAt.time)
            update(dbContact)
        }
        databaseProvider.writableDatabase.runTransaction {
            insert(
                table = ContactModel.TABLE,
                conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
                values = contentValuesContact,
            )
            if (ConfigUtils.supportsAvailabilityStatus()) {
                dbContact.availabilityStatusSet
                    ?.toDatabaseModel(dbContact.identity)
                    ?.let { dbAvailabilityStatusSet ->
                        insert(
                            table = DbAvailabilityStatus.TABLE,
                            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
                            values = dbAvailabilityStatusSet.toContentValues(),
                        )
                    }
            }
        }
    }

    override fun getContactByIdentity(identity: IdentityString): DbContact? {
        val contactIdentity = "${ContactModel.TABLE}.${ContactModel.COLUMN_IDENTITY}"
        val availabilityStatusIdentity = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_IDENTITY}"
        val availabilityStatusCategory = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_CATEGORY}"
        val availabilityStatusDescription = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_DESCRIPTION}"
        /*
         * SELECT contacts.*, contact_availability_status.category, contact_availability_status.description
         * FROM contacts LEFT JOIN contact_availability_status ON contacts.identity = contact_availability_status.identity
         * WHERE contacts.identity = ?;
         */
        val query = """
            SELECT ${ContactModel.TABLE}.*, $availabilityStatusCategory, $availabilityStatusDescription
            FROM ${ContactModel.TABLE} LEFT JOIN ${DbAvailabilityStatus.TABLE} ON $contactIdentity = $availabilityStatusIdentity
            WHERE $contactIdentity = ?;
        """.trimIndent()
        val cursor = databaseProvider.readableDatabase.query(
            /* query = */
            query,
            /* bindArgs = */
            arrayOf(identity),
        )
        return cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.mapToDbContact()
            } else {
                null
            }
        }
    }

    override fun getContactsByIdentities(identities: Set<IdentityString>): List<DbContact> {
        if (identities.isEmpty()) {
            return emptyList()
        }
        val placeholders = DatabaseUtil.makePlaceholders(identities.size)
        val contactIdentity = "${ContactModel.TABLE}.${ContactModel.COLUMN_IDENTITY}"
        val availabilityStatusIdentity = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_IDENTITY}"
        val availabilityStatusCategory = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_CATEGORY}"
        val availabilityStatusDescription = "${DbAvailabilityStatus.TABLE}.${DbAvailabilityStatus.COLUMN_DESCRIPTION}"
        /*
         * SELECT contacts.*, contact_availability_status.category, contact_availability_status.description
         * FROM contacts LEFT JOIN contact_availability_status ON contacts.identity = contact_availability_status.identity
         * WHERE contacts.identity IN (?, ...);
         */
        val query = """
            SELECT ${ContactModel.TABLE}.*, $availabilityStatusCategory, $availabilityStatusDescription
            FROM ${ContactModel.TABLE} LEFT JOIN ${DbAvailabilityStatus.TABLE} ON $contactIdentity = $availabilityStatusIdentity
            WHERE $contactIdentity IN ($placeholders);
        """.trimIndent()
        val cursor = databaseProvider.readableDatabase.query(
            /* query = */
            query,
            /* bindArgs = */
            identities.toTypedArray(),
        )
        return cursor.use { usedCursor ->
            val results = mutableListOf<DbContact>()
            while (usedCursor.moveToNext()) {
                results.add(usedCursor.mapToDbContact())
            }
            results
        }
    }

    /**
     * Maps this cursor at its current position to a [DbContact].
     *
     * @throws DatabaseException if the cursor does not contain all required columns.
     */
    private fun Cursor.mapToDbContact(): DbContact {
        val identity = getString(getColumnIndexOrThrow(this, ContactModel.COLUMN_IDENTITY))
        val publicKey = getBlob(getColumnIndexOrThrow(this, ContactModel.COLUMN_PUBLIC_KEY))
        val createdAt = getDate(getColumnIndexOrThrow(this, ContactModel.COLUMN_CREATED_AT))
        val firstName = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_FIRST_NAME)) ?: ""
        val lastName = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_LAST_NAME)) ?: ""
        val nickname = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_PUBLIC_NICK_NAME))
        var colorIndex = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_ID_COLOR_INDEX))
        val verificationLevelRaw = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_VERIFICATION_LEVEL))
        val isWorkVerifiedRaw = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_IS_WORK))
        val identityTypeRaw = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_TYPE))
        val acquaintanceLevelRaw = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_ACQUAINTANCE_LEVEL))
        val activityStateRaw = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_STATE))
        val syncStateRaw = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_SYNC_STATE))
        var featureMask = getLong(getColumnIndexOrThrow(this, ContactModel.COLUMN_FEATURE_MASK))
        val readReceipts = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_READ_RECEIPTS))
        val typingIndicators = getInt(getColumnIndexOrThrow(this, ContactModel.COLUMN_TYPING_INDICATORS))
        val isArchived = getBoolean(getColumnIndexOrThrow(this, ContactModel.COLUMN_IS_ARCHIVED))
        val androidContactLookupKey = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY))
        val localAvatarExpires = getDateOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES))
        val isRestored = getBooleanOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_IS_RESTORED)) ?: false
        val profilePictureBlobId = getBlobOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_PROFILE_PIC_BLOB_ID))
        val jobTitle = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_JOB_TITLE))
        val department = getStringOrNull(getColumnIndexOrThrow(this, ContactModel.COLUMN_DEPARTMENT))
        val notificationTriggerPolicyOverride = getLongOrNull(
            getColumnIndexOrThrow(this, ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE),
        )
        val availabilityStatusCategoryRaw = getIntOrNull(
            getColumnIndexOrThrow(this, DbAvailabilityStatus.COLUMN_CATEGORY),
        )
        val availabilityStatusDescription = getStringOrNull(
            getColumnIndexOrThrow(this, DbAvailabilityStatus.COLUMN_DESCRIPTION),
        )
        val workLastFullSyncAtRaw = getLongOrNull(
            getColumnIndexOrThrow(this, ContactModel.COLUMN_WORK_LAST_FULL_SYNC_AT),
        )

        // Validation and mapping
        if (colorIndex !in 0..255) {
            logger.warn("colorIndex value out of range: {}. Falling back to -1.", colorIndex)
            colorIndex = -1
        }
        val verificationLevel = when (verificationLevelRaw) {
            0 -> VerificationLevel.UNVERIFIED
            1 -> VerificationLevel.SERVER_VERIFIED
            2 -> VerificationLevel.FULLY_VERIFIED
            else -> {
                logger.warn(
                    "verificationLevel value out of range: {}. Falling back to UNVERIFIED.",
                    verificationLevelRaw,
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
                    isWorkVerifiedRaw,
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
                    identityTypeRaw,
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
                    acquaintanceLevelRaw,
                )
                AcquaintanceLevel.DIRECT
            }
        }
        val activityState = when (activityStateRaw) {
            "INACTIVE" -> IdentityState.INACTIVE
            "INVALID" -> IdentityState.INVALID
            "ACTIVE" -> IdentityState.ACTIVE
            "TEMPORARY" -> IdentityState.ACTIVE // Legacy state, see !276
            else -> {
                logger.warn(
                    "activityState value out of range: {}. Falling back to ACTIVE.",
                    activityStateRaw,
                )
                IdentityState.ACTIVE
            }
        }
        val syncState = when (syncStateRaw) {
            0 -> ContactSyncState.INITIAL
            1 -> ContactSyncState.IMPORTED
            2 -> ContactSyncState.CUSTOM
            else -> {
                logger.warn(
                    "syncState value out of range: {}. Falling back to INITIAL.",
                    syncStateRaw,
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
                    typingIndicators,
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
                    typingIndicators,
                )
                TypingIndicatorPolicy.DEFAULT
            }
        }

        // Since both these values are joined via LEFT JOIN, they actually can be null, although they are defined as NOT NULL in their dedicated table
        val availabilityStatus: AvailabilityStatus.Set? =
            if (
                ConfigUtils.supportsAvailabilityStatus() &&
                availabilityStatusCategoryRaw != null &&
                availabilityStatusDescription != null
            ) {
                AvailabilityStatus.fromDatabaseValues(
                    categorySerialized = availabilityStatusCategoryRaw,
                    description = availabilityStatusDescription,
                ) as? AvailabilityStatus.Set
            } else {
                null
            }

        val workLastFullSyncAt: Instant? = workLastFullSyncAtRaw?.let(Instant::ofEpochMilli)

        return DbContact(
            identity = identity,
            publicKey = publicKey,
            createdAt = createdAt,
            firstName = firstName,
            lastName = lastName,
            nickname = nickname,
            colorIndex = colorIndex,
            verificationLevel = verificationLevel,
            workVerificationLevel = workVerificationLevel,
            identityType = identityType,
            acquaintanceLevel = acquaintanceLevel,
            activityState = activityState,
            syncState = syncState,
            featureMask = featureMask.toULong(),
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
            availabilityStatusSet = availabilityStatus,
            workLastFullSyncAt = workLastFullSyncAt,
        )
    }

    override fun updateContact(dbContact: DbContact) {
        val contentValuesContact = buildContentValues {
            update(dbContact)
        }
        databaseProvider.writableDatabase.runTransaction {
            update(
                table = ContactModel.TABLE,
                conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
                values = contentValuesContact,
                whereClause = "${ContactModel.COLUMN_IDENTITY} = ?",
                whereArgs = arrayOf(dbContact.identity),
            )
            if (ConfigUtils.supportsAvailabilityStatus()) {
                updateAvailabilityStatus(
                    identity = dbContact.identity,
                    availabilityStatusSet = dbContact.availabilityStatusSet,
                )
            }
        }
    }

    /**
     *  Updating the availability status on database.
     *
     *  In case [availabilityStatusSet] is not null, a database row will be created or replaced for the given [identity]. If `null` was passed,
     *  any potential existing database entry for the given [identity] gets deleted.
     */
    private fun SupportSQLiteDatabase.updateAvailabilityStatus(
        identity: IdentityString,
        availabilityStatusSet: AvailabilityStatus.Set?,
    ) {
        if (availabilityStatusSet != null) {
            insert(
                table = DbAvailabilityStatus.TABLE,
                conflictAlgorithm = SQLiteDatabase.CONFLICT_REPLACE,
                values = availabilityStatusSet.toDatabaseModel(identity).toContentValues(),
            )
        } else {
            delete(
                table = DbAvailabilityStatus.TABLE,
                whereClause = "${DbAvailabilityStatus.COLUMN_IDENTITY} = ?",
                whereArgs = arrayOf(identity),
            )
        }
    }

    private fun ContentValues.update(contact: DbContact) {
        // Note: Identity, public key and created at cannot be updated.
        put(ContactModel.COLUMN_FIRST_NAME, contact.firstName)
        put(ContactModel.COLUMN_LAST_NAME, contact.lastName)
        put(ContactModel.COLUMN_PUBLIC_NICK_NAME, contact.nickname)
        put(ContactModel.COLUMN_ID_COLOR_INDEX, contact.colorIndex)
        put(ContactModel.COLUMN_VERIFICATION_LEVEL, contact.verificationLevel.code)
        put(
            ContactModel.COLUMN_IS_WORK,
            when (contact.workVerificationLevel) {
                WorkVerificationLevel.NONE -> 0
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> 1
            },
        )
        put(
            ContactModel.COLUMN_TYPE,
            when (contact.identityType) {
                IdentityType.NORMAL -> 0
                IdentityType.WORK -> 1
            },
        )
        put(
            ContactModel.COLUMN_ACQUAINTANCE_LEVEL,
            when (contact.acquaintanceLevel) {
                AcquaintanceLevel.DIRECT -> 0
                AcquaintanceLevel.GROUP -> 1
            },
        )
        put(
            ContactModel.COLUMN_STATE,
            when (contact.activityState) {
                IdentityState.ACTIVE -> "ACTIVE"
                IdentityState.INACTIVE -> "INACTIVE"
                IdentityState.INVALID -> "INVALID"
            },
        )
        put(
            ContactModel.COLUMN_SYNC_STATE,
            when (contact.syncState) {
                ContactSyncState.INITIAL -> 0
                ContactSyncState.IMPORTED -> 1
                ContactSyncState.CUSTOM -> 2
            },
        )
        put(ContactModel.COLUMN_FEATURE_MASK, contact.featureMask.toLong())
        put(
            ContactModel.COLUMN_READ_RECEIPTS,
            when (contact.readReceiptPolicy) {
                ReadReceiptPolicy.DEFAULT -> 0
                ReadReceiptPolicy.SEND -> 1
                ReadReceiptPolicy.DONT_SEND -> 2
            },
        )
        put(
            ContactModel.COLUMN_TYPING_INDICATORS,
            when (contact.typingIndicatorPolicy) {
                TypingIndicatorPolicy.DEFAULT -> 0
                TypingIndicatorPolicy.SEND -> 1
                TypingIndicatorPolicy.DONT_SEND -> 2
            },
        )
        put(ContactModel.COLUMN_IS_ARCHIVED, contact.isArchived)
        put(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY, contact.androidContactLookupKey)
        put(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES, contact.localAvatarExpires?.time)
        put(ContactModel.COLUMN_IS_RESTORED, contact.isRestored)
        put(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID, contact.profilePictureBlobId)
        put(ContactModel.COLUMN_JOB_TITLE, contact.jobTitle)
        put(ContactModel.COLUMN_DEPARTMENT, contact.department)
        put(ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE, contact.notificationTriggerPolicyOverride)
        put(ContactModel.COLUMN_WORK_LAST_FULL_SYNC_AT, contact.workLastFullSyncAt?.toEpochMilli())
    }

    private fun DbAvailabilityStatus.toContentValues() = buildContentValues {
        put(DbAvailabilityStatus.COLUMN_IDENTITY, identity)
        put(DbAvailabilityStatus.COLUMN_CATEGORY, category)
        put(DbAvailabilityStatus.COLUMN_DESCRIPTION, description)
    }

    /**
     *  Delete the contact from database by [identity].
     *
     *  The corresponding optional [AvailabilityStatus] in table [DbAvailabilityStatus.TABLE] will also be deleted (implicitly by the SQLite
     *  constraint).
     */
    override fun deleteContactByIdentity(identity: IdentityString): Boolean =
        databaseProvider.writableDatabase.delete(
            table = ContactModel.TABLE,
            whereClause = "${ContactModel.COLUMN_IDENTITY} = ?",
            whereArgs = arrayOf(identity),
        ) > 0

    override fun isContactInGroup(identity: IdentityString): Boolean {
        databaseProvider.readableDatabase.query(
            DatabaseUtil.IS_GROUP_MEMBER_QUERY,
            arrayOf(identity),
        ).use {
            return if (it.moveToFirst()) {
                it.getInt(0) == 1
            } else {
                logger.error("Could not execute query to check whether contact is group member")
                false
            }
        }
    }

    /**
     * Create a group.
     *
     * @throws DatabaseException if the constraints fail while inserting the group
     */
    override fun createGroup(group: DbGroup) {
        val contentValues = buildContentValues {
            put(GroupModelOld.COLUMN_CREATOR_IDENTITY, group.creatorIdentity)
            put(GroupModelOld.COLUMN_API_GROUP_ID, group.groupId)
            put(GroupModelOld.COLUMN_CREATED_AT, group.createdAt.time)
            update(group)
        }

        val rowId = try {
            databaseProvider.writableDatabase.insert(
                table = GroupModelOld.TABLE,
                conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
                values = contentValues,
            )
        } catch (e: SQLiteException) {
            throw DatabaseException("Could not insert group", e)
        }

        if (rowId < 0) {
            throw DatabaseException("Could not insert group")
        }

        updateGroupMembers(rowId, group.members)
    }

    override fun removeGroup(localDbId: Long) {
        // Remove messages
        databaseProvider.writableDatabase.delete(
            table = GroupMessageModel.TABLE,
            whereClause = "${GroupMessageModel.COLUMN_GROUP_ID} = ?",
            whereArgs = arrayOf(localDbId),
        )

        // Remove members
        databaseProvider.writableDatabase.delete(
            table = GroupMemberModel.TABLE,
            whereClause = "${GroupMemberModel.COLUMN_GROUP_ID} = ?",
            whereArgs = arrayOf(localDbId),
        )

        // Remove incoming group sync request log model. Note that outgoing group sync request logs
        // must not be removed as they need to be persisted to prevent sending sync requests too
        // often.
        databaseProvider.writableDatabase.delete(
            table = IncomingGroupSyncRequestLogModel.TABLE,
            whereClause = "${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID} = ?",
            whereArgs = arrayOf(localDbId),
        )

        // TODO(ANDR-3633): Remove group calls and polls here as they are also located in the
        //  database.

        // Remove the group itself
        databaseProvider.writableDatabase.delete(
            table = GroupModelOld.TABLE,
            whereClause = "${GroupModelOld.COLUMN_ID} = ?",
            whereArgs = arrayOf(localDbId),
        )
    }

    override fun getAllGroups(): Collection<DbGroup> {
        val query = SupportSQLiteQueryBuilder.builder(GroupModelOld.TABLE)
            .columns(null)
            .selection("TRUE", emptyArray())
            .create()

        databaseProvider.readableDatabase.query(query).use { cursor ->
            val groups = mutableListOf<DbGroup>()
            while (cursor.moveToNext()) {
                groups.add(cursor.getGroup())
            }
            return groups
        }
    }

    override fun getGroupByLocalGroupDbId(localDbId: Long): DbGroup? {
        return getGroup {
            it.selection("${GroupModelOld.COLUMN_ID} = ?", arrayOf(localDbId))
        }
    }

    override fun getGroupByGroupIdentity(groupIdentity: GroupIdentity): DbGroup? {
        val creatorIdentitySelection = "${GroupModelOld.COLUMN_CREATOR_IDENTITY} = ?"
        val groupIdSelection = "${GroupModelOld.COLUMN_API_GROUP_ID} = ?"
        val creatorIdentitySelectionArg = groupIdentity.creatorIdentity
        val groupIdSelectionArg = groupIdentity.groupIdHexString
        val selection = "$creatorIdentitySelection AND $groupIdSelection"
        val selectionArgs = arrayOf(creatorIdentitySelectionArg, groupIdSelectionArg)

        return getGroup {
            it.selection(selection, selectionArgs)
        }
    }

    override fun getGroupDatabaseId(groupIdentity: GroupIdentity): Long? {
        val query = SupportSQLiteQueryBuilder.builder(GroupModelOld.TABLE)
            .columns(
                arrayOf(GroupModelOld.COLUMN_ID),
            ).selection(
                GroupModelOld.COLUMN_API_GROUP_ID + "=? AND " + GroupModelOld.COLUMN_CREATOR_IDENTITY + "=?",
                arrayOf<String?>(groupIdentity.groupIdHexString, groupIdentity.creatorIdentity),
            ).create()

        return databaseProvider.readableDatabase.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(GroupModelOld.COLUMN_ID))
            } else {
                null
            }
        }
    }

    private fun getGroup(addSelection: (SupportSQLiteQueryBuilder) -> Unit): DbGroup? {
        return databaseProvider.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupModelOld.TABLE)
                .columns(
                    arrayOf(
                        GroupModelOld.COLUMN_ID,
                        GroupModelOld.COLUMN_API_GROUP_ID,
                        GroupModelOld.COLUMN_NAME,
                        GroupModelOld.COLUMN_CREATOR_IDENTITY,
                        GroupModelOld.COLUMN_CREATED_AT,
                        GroupModelOld.COLUMN_SYNCHRONIZED_AT,
                        GroupModelOld.COLUMN_LAST_UPDATE,
                        GroupModelOld.COLUMN_IS_ARCHIVED,
                        GroupModelOld.COLUMN_COLOR_INDEX,
                        GroupModelOld.COLUMN_GROUP_DESC,
                        GroupModelOld.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP,
                        GroupModelOld.COLUMN_USER_STATE,
                        GroupModelOld.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE,
                    ),
                )
                .apply(addSelection)
                .create(),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getGroup()
            } else {
                null
            }
        }
    }

    override fun updateGroup(group: DbGroup) {
        val localGroupDbId = getLocalGroupDbId(group)

        // First update general group information
        val contentValues = buildContentValues { update(group) }

        databaseProvider.writableDatabase.update(
            table = GroupModelOld.TABLE,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
            whereClause = "${GroupModelOld.COLUMN_ID} = ?",
            whereArgs = arrayOf(localGroupDbId),
        )

        // Then update group members
        updateGroupMembers(localGroupDbId, group.members)
    }

    private fun Cursor.getGroup(): DbGroup {
        val localDbId = getLong(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_ID))
        val creatorIdentity =
            getString(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_CREATOR_IDENTITY))
        val groupId = getString(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_API_GROUP_ID))
        val name = getStringOrNull(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_NAME))
        val createdAt = getDate(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_CREATED_AT))
        val synchronizedAt =
            getDateOrNull(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_SYNCHRONIZED_AT))
        val lastUpdate = getDateOrNull(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_LAST_UPDATE))
        val isArchived = getBoolean(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_IS_ARCHIVED))
        val colorIndex = getInt(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_COLOR_INDEX))
        val groupDesc = getStringOrNull(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_GROUP_DESC))
        val groupDescChangedAt = getDateOrNull(
            getColumnIndexOrThrow(
                this,
                GroupModelOld.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP,
            ),
        )
        val members = getGroupMembers(localDbId)
        val userStateValue = getInt(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_USER_STATE))
        val userState = UserState.getByValue(userStateValue) ?: run {
            logger.error("Invalid group user state: {}", userStateValue)
            // We use member as fallback to not accidentally remove the user from the group
            UserState.MEMBER
        }
        val notificationTriggerPolicyOverride = getLongOrNull(getColumnIndexOrThrow(this, GroupModelOld.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE))

        return DbGroup(
            creatorIdentity = creatorIdentity,
            groupId = groupId,
            name = name,
            createdAt = createdAt,
            synchronizedAt = synchronizedAt,
            lastUpdate = lastUpdate,
            isArchived = isArchived,
            colorIndex = colorIndex,
            groupDescription = groupDesc,
            groupDescriptionChangedAt = groupDescChangedAt,
            members = members,
            userState = userState,
            notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
        )
    }

    private fun ContentValues.update(group: DbGroup) {
        // Note: creator identity, group id, and created at cannot be updated
        put(GroupModelOld.COLUMN_NAME, group.name)
        put(GroupModelOld.COLUMN_LAST_UPDATE, group.lastUpdate?.time)
        put(GroupModelOld.COLUMN_SYNCHRONIZED_AT, group.synchronizedAt?.time)
        put(GroupModelOld.COLUMN_IS_ARCHIVED, group.isArchived)
        put(GroupModelOld.COLUMN_COLOR_INDEX, group.colorIndex)
        put(GroupModelOld.COLUMN_GROUP_DESC, group.groupDescription)
        put(
            GroupModelOld.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP,
            group.groupDescriptionChangedAt?.time,
        )
        put(GroupModelOld.COLUMN_USER_STATE, group.userState.value)
        put(GroupModelOld.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE, group.notificationTriggerPolicyOverride)
    }

    private fun getLocalGroupDbId(group: DbGroup): Long {
        val creatorIdentitySelection = "${GroupModelOld.COLUMN_CREATOR_IDENTITY} = ?"
        val groupIdSelection = "${GroupModelOld.COLUMN_API_GROUP_ID} = ?"
        val creatorIdentitySelectionArg = group.creatorIdentity
        val groupIdSelectionArg = group.groupId
        val selection = "$creatorIdentitySelection AND $groupIdSelection"
        val selectionArgs = arrayOf(creatorIdentitySelectionArg, groupIdSelectionArg)

        databaseProvider.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupModelOld.TABLE)
                .columns(arrayOf(GroupModelOld.COLUMN_ID))
                .selection(selection, selectionArgs)
                .create(),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw DatabaseException("Could not find a group with creator ${group.creatorIdentity} and id ${group.groupId}")
            }

            return cursor.getLong(cursor.getColumnIndexOrThrow(GroupModelOld.COLUMN_ID))
        }
    }

    private fun getGroupMembers(localDbId: Long): Set<String> {
        databaseProvider.readableDatabase.query(
            SupportSQLiteQueryBuilder.builder(GroupMemberModel.TABLE)
                .columns(arrayOf(GroupMemberModel.COLUMN_IDENTITY))
                .selection("${GroupMemberModel.COLUMN_GROUP_ID} = ?", arrayOf(localDbId))
                .create(),
        ).use { cursor ->
            val members = mutableSetOf<String>()

            while (cursor.moveToNext()) {
                members.add(
                    cursor.getString(
                        getColumnIndexOrThrow(
                            cursor,
                            GroupMemberModel.COLUMN_IDENTITY,
                        ),
                    ),
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

        databaseProvider.writableDatabase.delete(
            table = GroupMemberModel.TABLE,
            whereClause = "$whereGroupId AND $whereNotMember",
            whereArgs = (listOf(localDbId) + members).toTypedArray<Any>(),
        )

        // Add all members (if not already exists)
        val existingMembers = getGroupMembers(localDbId)
        val contentValuesList = (members - existingMembers).map { memberIdentity ->
            buildContentValues {
                put(GroupMemberModel.COLUMN_IDENTITY, memberIdentity)
                put(GroupMemberModel.COLUMN_GROUP_ID, localDbId)
            }
        }

        contentValuesList.forEach { contentValues ->
            databaseProvider.writableDatabase.insert(
                table = GroupMemberModel.TABLE,
                conflictAlgorithm = SQLiteDatabase.CONFLICT_FAIL,
                values = contentValues,
            )
        }
    }
}
