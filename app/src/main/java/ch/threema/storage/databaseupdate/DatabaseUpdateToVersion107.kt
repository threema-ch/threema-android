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

package ch.threema.storage.databaseupdate

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.base.utils.Base32
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.types.Identity
import ch.threema.storage.databaseupdate.DatabaseUpdateToVersion107.Companion.DEADLINE_INDEFINITE
import ch.threema.storage.databaseupdate.DatabaseUpdateToVersion107.Companion.DEADLINE_INDEFINITE_EXCEPT_MENTIONS
import ch.threema.storage.runTransaction
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException

private val logger = LoggingUtil.getThreemaLogger("DatabaseUpdateToVersion107")

internal class DatabaseUpdateToVersion107(
    private val sqLiteDatabase: SQLiteDatabase,
    private val context: Context,
) : DatabaseUpdate {
    override fun run() {
        if (!sqLiteDatabase.fieldExists("contacts", "notificationTriggerPolicyOverride")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` ADD COLUMN `notificationTriggerPolicyOverride` BIGINT DEFAULT NULL")
            logger.info("Added column `notificationTriggerPolicyOverride` to table `contacts`")
        }
        if (!sqLiteDatabase.fieldExists("m_group", "notificationTriggerPolicyOverride")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `m_group` ADD COLUMN `notificationTriggerPolicyOverride` BIGINT DEFAULT NULL")
            logger.info("Added column `notificationTriggerPolicyOverride` to table `m_group`")
        }
        migrateOldNotificationTriggerPolicyOverrideSettings()
    }

    private fun migrateOldNotificationTriggerPolicyOverrideSettings() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // 1. Get all existing policy settings for contact- and group-conversation
        val uniqueReceiverIdsWithPolicyOverrideValues: Map<String, Long> = readExistingMutedOverrideSettings(prefs)
        if (uniqueReceiverIdsWithPolicyOverrideValues.isEmpty()) {
            return
        }

        // 2. Read all existing contact identities from database into a local list
        val contactIdentitiesCursor: Cursor = this.sqLiteDatabase.query("SELECT `identity` FROM `contacts`;")
        val allExistingContactIdentities = mutableListOf<String>()
        try {
            contactIdentitiesCursor.use { cursor ->
                val identityColumnIndex: Int = contactIdentitiesCursor.getColumnIndexOrThrow("identity")
                while (cursor.moveToNext()) {
                    allExistingContactIdentities.add(cursor.getString(identityColumnIndex))
                }
            }
        } catch (exception: Exception) {
            logger.error("Failed while loading existing contact model identities. Continuing migration", exception)
        }
        logger.info("Found {} existing contacts in db", allExistingContactIdentities.size)

        // 3. Associate the correct notification-trigger-policy-override setting to contacts.
        // Result is a map of <Identity, PolicyOverride>
        val contactIdentitiesWithPolicyOverrideValues = HashMap<String, Long>()
        allExistingContactIdentities.forEach { identity ->
            val contactUniqueIdString: String = getContactUniqueIdString(identity)
            uniqueReceiverIdsWithPolicyOverrideValues[contactUniqueIdString]?.let { policyOverrideValue ->
                contactIdentitiesWithPolicyOverrideValues[identity] = policyOverrideValue
            }
        }

        // 4. Read all existing group-db-ids from database into a local list
        val groupDbIdsCursor: Cursor = this.sqLiteDatabase.query("SELECT `id` FROM `m_group`;")
        val allExistingGroupsDbIds = mutableListOf<Long>()
        try {
            groupDbIdsCursor.use { cursor ->
                val idColumnIndex: Int = groupDbIdsCursor.getColumnIndexOrThrow("id")
                while (cursor.moveToNext()) {
                    allExistingGroupsDbIds.add(cursor.getLong(idColumnIndex))
                }
            }
        } catch (exception: Exception) {
            logger.error("Failed while loading existing group model ids. Continuing migration", exception)
        }

        logger.info("Found {} existing groups in db", allExistingGroupsDbIds.size)

        // 5. Associate the correct notification-trigger-policy-override setting setting to groups.
        // Result is a map of <GroupDbId, PolicyOverride> for the groups
        val groupDbIdsWithPolicyOverrideValues = HashMap<Long, Long>()
        allExistingGroupsDbIds.forEach { groupDbId: Long ->
            val groupUniqueIdString: String = getGroupUniqueIdString(groupDbId)
            uniqueReceiverIdsWithPolicyOverrideValues[groupUniqueIdString]?.let { policyOverrideValue ->
                groupDbIdsWithPolicyOverrideValues[groupDbId] = policyOverrideValue
            }
        }

        // 6. Apply values to database
        writeExistingSettingsToDatabase(
            contactIdentitiesWithPolicyOverrideValues,
            groupDbIdsWithPolicyOverrideValues,
        )

        // 7. Delete old shared preferences data
        prefs.edit {
            remove(LEGACY_PREFS_KEY_LIST_MUTED_CHATS)
            remove(LEGACY_PREFS_KEY_LIST_MENTION_ONLY)
            logger.info("Cleared old legacy settings from shared preferences")
        }
    }

    /**
     *  In order to get all information, for contact- and group-conversation settings we read the existing settings
     *  from both existing deadline-lists `list_muted_chats` and `list_mention_only`.
     *
     *  **Special case for group settings:**
     *  - When user taps `On, unless I was mentioned` then this will add an entry with value of `-1` to `list_mention_only`.
     *  - When user taps `On` or activates the mute for a specific time, then the entry will result in `list_muted_chats`.
     *  - Only one entry for one group will exist at a time between these two managed lists
     *
     *  @return A map where the unique-receiver-id (getGroupUniqueIdString) maps to utc timestamps in milliseconds.
     *  The values can also be one of the special cases [DEADLINE_INDEFINITE] or [DEADLINE_INDEFINITE_EXCEPT_MENTIONS].
     */
    private fun readExistingMutedOverrideSettings(prefs: SharedPreferences): Map<String, Long> {
        val valuesFromListMutedChats = readValuesFromDeadlineList(prefs, LEGACY_PREFS_KEY_LIST_MUTED_CHATS)
        val valuesFromListListMentionOnly = readValuesFromDeadlineList(prefs, LEGACY_PREFS_KEY_LIST_MENTION_ONLY)
            .mapValues { mapEntry ->
                // If there was a long value of "-1" stored in this list, we have to map it to "-2"
                val mappedValue = if (mapEntry.value == DEADLINE_INDEFINITE) {
                    DEADLINE_INDEFINITE_EXCEPT_MENTIONS
                } else {
                    mapEntry.value
                }
                mappedValue
            }
        val combinedSettings = valuesFromListMutedChats + valuesFromListListMentionOnly
        logger.info("Found {} existing notification policy settings", combinedSettings.size)
        return combinedSettings
    }

    /**
     *  Reads the values from the shared preferences. These values were handle by the `DeadlineListService`.
     *
     *  Information is stored in a json array containing a json array. The first value of the inner json array represents
     *  a unique id of a contact identity or a group-db-id. These values are hashed, so we can not know just by this value
     *  if it is a setting made for a group or for a contact conversation.
     *
     *  The second value is a utc timestamp in milliseconds converted to a string. This value can also be `-1`. In this
     *  case the conversation is blocked indefinitely.
     *
     *  @return A map containing all settings that could be parsed. The keys stay the same. The value gets converted from
     *  `String` to `Long`. Entries where the timestamp is in the past are excluded from this list.
     */
    private fun readValuesFromDeadlineList(prefs: SharedPreferences, listName: String): HashMap<String, Long> {
        val uniqueReceiverIdsWithPolicyOverrideValues = HashMap<String, Long>()

        if (prefs.contains(listName)) {
            val jsonArray = try {
                JSONArray(prefs.getString(listName, "[]"))
            } catch (jsonException: JSONException) {
                logger.error("Failed to copy over existing conversation mute override settings", jsonException)
                return uniqueReceiverIdsWithPolicyOverrideValues
            }
            if (jsonArray.length() == 0) {
                return uniqueReceiverIdsWithPolicyOverrideValues
            }

            for (i in 0 until jsonArray.length()) {
                val keyValuePair: JSONArray = jsonArray.getJSONArray(i)

                val uniqueReceiverId: String = try {
                    keyValuePair.getString(0)
                } catch (jsonException: JSONException) {
                    logger.error("Failed to read a uniqueReceiverId value from existing conversation mute override settings", jsonException)
                    continue
                }
                val deadlineUtcMillisString: String = try {
                    keyValuePair.getString(1)
                } catch (jsonException: JSONException) {
                    logger.error("Failed to read a deadlineUtcMillisString value from existing conversation mute override settings", jsonException)
                    continue
                }

                val policyOverrideValue: Long = try {
                    deadlineUtcMillisString.toLong()
                } catch (numberFormatException: NumberFormatException) {
                    logger.error("Failed to convert saved string timestamp millis to type long", numberFormatException)
                    continue
                }

                if (policyOverrideValue != DEADLINE_INDEFINITE && policyOverrideValue < System.currentTimeMillis()) {
                    continue
                }

                uniqueReceiverIdsWithPolicyOverrideValues[uniqueReceiverId] = policyOverrideValue
            }
        }

        return uniqueReceiverIdsWithPolicyOverrideValues
    }

    private fun writeExistingSettingsToDatabase(
        contactIdentitiesWithPolicyOverrideValues: HashMap<String, Long>,
        groupDbIdsWithPolicyOverrideValues: HashMap<Long, Long>,
    ) {
        if (contactIdentitiesWithPolicyOverrideValues.isEmpty() && groupDbIdsWithPolicyOverrideValues.isEmpty()) {
            return
        }
        try {
            sqLiteDatabase.runTransaction {
                contactIdentitiesWithPolicyOverrideValues.forEach { (identity, policyOverrideValue) ->
                    rawExecSQL(
                        "UPDATE `contacts` SET notificationTriggerPolicyOverride = $policyOverrideValue WHERE identity = '$identity';",
                    )
                    logger.info("Stored notification-trigger-policy-override value of {} for contact with identity {}", policyOverrideValue, identity)
                }
                groupDbIdsWithPolicyOverrideValues.forEach { (groupDbId, policyOverrideValue) ->
                    rawExecSQL(
                        "UPDATE `m_group` SET notificationTriggerPolicyOverride = $policyOverrideValue WHERE id = $groupDbId;",
                    )
                    logger.info("Stored notification-trigger-policy-override value of {} for group with db-id {}", policyOverrideValue, groupDbId)
                }
            }
        } catch (exception: Exception) {
            logger.error("Failure while updating notificationTriggerPolicyOverride cells", exception)
        }
    }

    override fun getDescription() = "add column notificationTriggerPolicyOverride for contacts and groups and copy over old preferences data"

    override fun getVersion() = VERSION

    private fun getContactUniqueIdString(identity: Identity?): String =
        if (identity != null) {
            getUniqueIdString("c-$identity")
        } else {
            ""
        }

    private fun getGroupUniqueIdString(groupId: Long): String =
        getUniqueIdString("g-$groupId")

    private fun getUniqueIdString(value: String): String =
        try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(value.toByteArray())
            Base32.encode(messageDigest.digest())
        } catch (_: NoSuchAlgorithmException) {
            ""
        }

    companion object {
        const val VERSION = 107

        private const val LEGACY_PREFS_KEY_LIST_MUTED_CHATS = "list_muted_chats"
        private const val LEGACY_PREFS_KEY_LIST_MENTION_ONLY = "list_mention_only"

        const val DEADLINE_INDEFINITE: Long = -1
        const val DEADLINE_INDEFINITE_EXCEPT_MENTIONS: Long = -2
    }
}
