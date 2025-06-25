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

package ch.threema.app.stores

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import androidx.annotation.ArrayRes
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.StringConversionUtil
import ch.threema.app.utils.TestUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyLockedException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutput
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.Instant
import java.util.Date
import javax.crypto.CipherInputStream
import org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONObject

private val logger = LoggingUtil.getThreemaLogger("PreferenceStore")

class PreferenceStore(
    private val context: Context,
    private val masterKey: MasterKey?,
) : PreferenceStoreInterface {
    companion object {
        const val PREFS_IDENTITY: String = "identity"
        const val PREFS_SERVER_GROUP: String = "server_group"
        const val PREFS_PUBLIC_KEY: String = "public_key"
        const val PREFS_PRIVATE_KEY: String = "private_key"
        const val PREFS_PUBLIC_NICKNAME: String = "nickname"
        const val PREFS_LINKED_EMAIL: String = "linked_email"
        const val PREFS_LINKED_MOBILE: String = "linked_mobile"
        const val PREFS_LINKED_EMAIL_PENDING: String = "linked_mobile_pending" // typo
        const val PREFS_LINKED_MOBILE_PENDING: String = "linked_mobile_pending_since"
        const val PREFS_MOBILE_VERIFICATION_ID: String = "linked_mobile_verification_id"
        const val PREFS_LAST_REVOCATION_KEY_SET: String = "last_revocation_key_set"
        const val PREFS_REVOCATION_KEY_CHECKED: String = "revocation_key_checked"

        const val PREFS_MD_PROPERTIES: String = "md_properties"
        const val PREFS_MD_MEDIATOR_MAX_SLOTS: String = "md_mediator_max_slots"

        const val CRYPTED_FILE_PREFIX: String = ".crs-"
    }

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    override fun remove(key: String) {
        sharedPreferences.edit {
            remove(key)
        }
    }

    override fun remove(keys: List<String>) {
        sharedPreferences.edit {
            for (key in keys) {
                remove(key)
                // try to remove crypted file
                removeCryptedFile(key)
            }
        }
    }

    override fun remove(key: String, crypt: Boolean) {
        if (crypt) {
            removeCryptedFile(key)
        } else {
            remove(key)
        }
    }

    override fun save(key: String, thing: String?) {
        this.save(key, thing, crypt = false)
    }

    override fun save(key: String, thing: String?, crypt: Boolean) {
        if (crypt) {
            // save into a file
            this.saveDataToCryptedFile(StringConversionUtil.stringToByteArray(thing), key)
        } else {
            sharedPreferences.edit {
                putString(key, thing)
            }
        }
        this.fireOnChanged(key, thing)
    }

    override fun save(key: String, things: Array<String>) {
        this.save(key, things, crypt = false)
    }

    override fun save(key: String, things: HashMap<Int, String>) {
        this.save(key, things, crypt = false)
    }

    override fun save(key: String, things: HashMap<Int, String>, crypt: Boolean) {
        val json = JSONArray()
        for ((key1, value) in things) {
            val keyValueArray = JSONArray()
            keyValueArray.put(key1)
            keyValueArray.put(value)
            json.put(keyValueArray)
        }

        this.save(key, json, crypt)
    }

    override fun saveIntegerHashMap(key: String, things: HashMap<Int, Int>) {
        val json = JSONArray()
        for ((key1, value) in things) {
            val keyValueArray = JSONArray()
            keyValueArray.put(key1)
            keyValueArray.put(value)
            json.put(keyValueArray)
        }
        this.save(key, json)
    }

    override fun saveStringHashMap(key: String, things: HashMap<String, String>, crypt: Boolean) {
        val json = JSONArray()
        for ((key1, value) in things) {
            val keyValueArray = JSONArray()
            keyValueArray.put(key1)
            keyValueArray.put(value)
            json.put(keyValueArray)
        }
        this.save(key, json, crypt)
    }

    override fun save(key: String, things: Array<String>, crypt: Boolean) {
        saveQuietly(key, things, crypt)
        this.fireOnChanged(key, things)
    }

    /**
     * Save list preference quietly without fireing a UI listener event (for use in workers or other background processing)
     */
    override fun saveQuietly(key: String, things: Array<String>, crypt: Boolean) {
        val stringBuilder = StringBuilder()
        for (string in things) {
            if (stringBuilder.isNotEmpty()) {
                stringBuilder.append(';')
            }
            stringBuilder.append(string)
        }

        if (crypt) {
            // save into a file
            this.saveDataToCryptedFile(
                StringConversionUtil.stringToByteArray(stringBuilder.toString()),
                key,
            )
        } else {
            sharedPreferences.edit {
                putString(key, stringBuilder.toString())
            }
        }
    }

    override fun save(key: String, thing: Long) {
        this.save(key, thing, crypt = false)
    }

    override fun save(key: String, thing: Long, crypt: Boolean) {
        if (crypt) {
            // save into a file
            this.saveDataToCryptedFile(Utils.hexStringToByteArray(thing.toString()), key)
        } else {
            sharedPreferences.edit {
                putLong(key, thing)
            }
        }
        this.fireOnChanged(key, thing)
    }

    override fun save(key: String, thing: Int) {
        this.save(key, thing, crypt = false)
    }

    override fun save(key: String, thing: Int, crypt: Boolean) {
        if (crypt) {
            // save into a file
            this.saveDataToCryptedFile(Utils.hexStringToByteArray(thing.toString()), key)
        } else {
            sharedPreferences.edit {
                putInt(key, thing)
            }
        }
        this.fireOnChanged(key, thing)
    }

    override fun save(key: String, thing: Boolean) {
        sharedPreferences.edit {
            putBoolean(key, thing)
        }
        this.fireOnChanged(key, thing)
    }

    override fun save(key: String, thing: ByteArray) {
        this.save(key, thing, crypt = false)
    }

    override fun save(key: String, thing: ByteArray, crypt: Boolean) {
        if (crypt) {
            // save into a file
            this.saveDataToCryptedFile(thing, key)
        } else {
            sharedPreferences.edit {
                putString(key, Utils.byteArrayToHexString(thing))
            }
        }
        this.fireOnChanged(key, thing)
    }

    override fun save(key: String, date: Date?) {
        this.save(key, date, crypt = false)
    }

    override fun save(key: String, date: Date?, crypt: Boolean) {
        // save as long
        this.save(key, date?.time ?: 0, crypt)
    }

    override fun save(key: String, instant: Instant?) {
        save(key, instant?.toEpochMilli() ?: 0L)
    }

    override fun save(key: String, array: JSONArray) {
        save(key, array, crypt = false)
    }

    @Throws(IOException::class)
    override fun save(key: String, `object`: Serializable, crypt: Boolean) {
        // ignore close exception
        ByteArrayOutputStream().use { bos ->
            val out: ObjectOutput = ObjectOutputStream(bos)
            out.writeObject(`object`)
            out.flush()
            this.save(key, bos.toByteArray(), crypt)
        }
    }

    override fun save(key: String, array: JSONArray?, crypt: Boolean) {
        if (array != null) {
            if (crypt) {
                this.saveDataToCryptedFile(array.toString().toByteArray(), key)
            } else {
                sharedPreferences.edit {
                    putString(key, array.toString())
                }
            }
        }
        this.fireOnChanged(key, array)
    }

    override fun save(key: String, thing: Float) {
        sharedPreferences.edit {
            putFloat(key, thing)
        }
    }

    override fun save(key: String, `object`: JSONObject?, crypt: Boolean) {
        if (`object` != null) {
            if (crypt) {
                this.saveDataToCryptedFile(`object`.toString().toByteArray(), key)
            } else {
                sharedPreferences.edit {
                    putString(key, `object`.toString())
                }
            }
        }
        this.fireOnChanged(key, `object`)
    }

    override fun getString(key: String): String? = this.getString(key, crypt = false)

    override fun getString(key: String, crypt: Boolean): String? {
        if (crypt) {
            val stringBytes: ByteArray = this.getDataFromCryptedFile(key)
            return StringConversionUtil.byteArrayToString(stringBytes)
        } else {
            var value: String? = null
            try {
                value = sharedPreferences.getString(key, null)
            } catch (e: ClassCastException) {
                logger.error("Class cast exception", e)
            }
            return value
        }
    }

    override fun getStringCompat(key: String): String? {
        var value: String? = getString(key, crypt = true)
        if (TestUtil.isEmptyOrNull(value)) {
            value = getString(key, crypt = false)
            if (value != null) {
                save(key, value as String?, crypt = true)
                remove(key)
            }
        }
        return value
    }

    override fun getStringArray(key: String): Array<String>? =
        this.getStringArray(key, crypted = false)

    override fun getStringArray(key: String, crypted: Boolean): Array<String>? {
        val value = if (crypted) {
            val bytes: ByteArray = this.getDataFromCryptedFile(key)
            StringConversionUtil.byteArrayToString(bytes)
        } else {
            sharedPreferences.getString(key, null)
        }

        if (!value.isNullOrEmpty()) {
            return value.split(";").dropLastWhile(String::isEmpty).toTypedArray()
        }

        return null
    }

    override fun getHashMap(key: String, encrypted: Boolean): HashMap<Int, String> {
        val result = HashMap<Int, String>()

        try {
            val jsonArray = if (encrypted) {
                JSONArray(String(getDataFromCryptedFile(key)))
            } else {
                JSONArray(sharedPreferences.getString(key, "[]"))
            }

            for (n in 0 until jsonArray.length()) {
                val keyValuePair = jsonArray.getJSONArray(n)

                result[keyValuePair.getInt(0)] = keyValuePair.getString(1)
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
        return result
    }

    override fun getStringHashMap(key: String, encrypted: Boolean): HashMap<String, String> {
        val result = HashMap<String, String>()

        try {
            var jsonArray: JSONArray? = null
            if (encrypted) {
                val bytes: ByteArray = getDataFromCryptedFile(key)
                if (bytes.isNotEmpty()) {
                    jsonArray = JSONArray(String(bytes))
                }
            } else {
                jsonArray = JSONArray(sharedPreferences.getString(key, "[]"))
            }

            if (jsonArray != null) {
                for (n in 0 until jsonArray.length()) {
                    val keyValuePair = jsonArray.getJSONArray(n)

                    result[keyValuePair.getString(0)] = keyValuePair.getString(1)
                }
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
        return result
    }

    override fun getHashMap(key: String): HashMap<Int, Int> {
        val result = HashMap<Int, Int>()

        try {
            val jsonArray = JSONArray(sharedPreferences.getString(key, "[]"))

            for (n in 0 until jsonArray.length()) {
                val keyValuePair = jsonArray.getJSONArray(n)

                result[keyValuePair.getInt(0)] = keyValuePair.getInt(1)
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
        return result
    }

    /**
     *  for compatibility with old PIN storage format (used in release 1.2)
     *  can be removed in a few years :)
     */
    override fun getHexString(key: String, crypt: Boolean): String? {
        if (crypt) {
            val bytes = this.getDataFromCryptedFile(key)
            return Utils.byteArrayToHexString(bytes)
        } else {
            return sharedPreferences.getString(key, null)
        }
    }

    override fun getLong(key: String): Long = this.getLong(key, crypt = false) ?: 0L

    override fun getLong(key: String, crypt: Boolean): Long? {
        if (crypt) {
            val longBytes: ByteArray = this.getDataFromCryptedFile(key)
            return java.lang.Long.getLong(Utils.byteArrayToHexString(longBytes))
        } else {
            return sharedPreferences.getLong(key, 0L)
        }
    }

    override fun getDate(key: String): Date? = this.getDate(key, crypt = false)

    override fun getDate(key: String, crypt: Boolean): Date? {
        val longValue: Long? = this.getLong(key, crypt)
        if (longValue != null && longValue > 0L) {
            return Date(longValue)
        }
        return null
    }

    override fun getInstant(key: String): Instant? {
        val longValue = getLong(key)
        return if (longValue != 0L) {
            Instant.ofEpochMilli(longValue)
        } else {
            null
        }
    }

    override fun getDateAsLong(key: String): Long {
        val longValue: Long? = this.getLong(key, crypt = false)
        if (longValue != null && longValue > 0) {
            return longValue
        }
        return 0L
    }

    override fun getInt(key: String): Int? = this.getInt(key, crypt = false)

    override fun getInt(key: String, crypt: Boolean): Int? {
        if (crypt) {
            val intBytes = this.getDataFromCryptedFile(key)
            return Integer.getInteger(Utils.byteArrayToHexString(intBytes))
        } else {
            return sharedPreferences.getInt(key, 0)
        }
    }

    override fun getFloat(key: String, defValue: Float): Float =
        sharedPreferences.getFloat(key, defValue)

    override fun getBoolean(key: String): Boolean = sharedPreferences.getBoolean(key, false)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        sharedPreferences.getBoolean(key, defValue)

    override fun getBytes(key: String): ByteArray = this.getBytes(key, crypt = false)

    override fun getBytes(key: String, crypt: Boolean): ByteArray {
        if (crypt) {
            return this.getDataFromCryptedFile(key)
        } else {
            val hexString: String? = sharedPreferences.getString(key, null)
            if (hexString != null) {
                return Utils.hexStringToByteArray(hexString)
            }
        }
        return ByteArray(0)
    }

    override fun getJSONArray(key: String, crypt: Boolean): JSONArray {
        try {
            if (crypt) {
                val bytes = this.getDataFromCryptedFile(key)
                return if (bytes.isEmpty()) {
                    JSONArray()
                } else {
                    JSONArray(String(bytes))
                }
            } else {
                return JSONArray(sharedPreferences.getString(key, "[]"))
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
        return JSONArray()
    }

    override fun getJSONObject(key: String, crypt: Boolean): JSONObject? {
        try {
            if (crypt) {
                return JSONObject(String(this.getDataFromCryptedFile(key)))
            } else {
                val data = sharedPreferences.getString(key, "[]")
                return if (data != null) {
                    JSONObject(data)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
        return null
    }

    override fun clear() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()

        try {
            context.filesDir.listFiles { _, filename: String ->
                filename.startsWith(CRYPTED_FILE_PREFIX)
            }?.forEach { file ->
                FileUtil.deleteFileOrWarn(file, "clear", logger)
            }
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
    }

    override fun getAllNonCrypted(): Map<String, *> = sharedPreferences.all

    override fun getStringSet(key: String, @ArrayRes defaultRes: Int): Set<String> =
        if (sharedPreferences.contains(key)) {
            sharedPreferences.getStringSet(key, emptySet()) ?: emptySet()
        } else {
            HashSet(context.resources.getStringArray(defaultRes).toList())
        }

    override fun containsKey(key: String): Boolean = containsKey(key, crypt = false)

    override fun containsKey(key: String, crypt: Boolean): Boolean {
        return if (crypt) {
            File(context.filesDir, CRYPTED_FILE_PREFIX + key).exists()
        } else {
            sharedPreferences.contains(key)
        }
    }

    private fun fireOnChanged(key: String, value: Any?) {
        ListenerManager.preferenceListeners.handle { listener: PreferenceListener ->
            listener.onChanged(
                key,
                value,
            )
        }
    }

    @WorkerThread
    private fun removeCryptedFile(filename: String) {
        val file = File(context.filesDir, CRYPTED_FILE_PREFIX + filename)
        if (file.exists()) {
            FileUtil.deleteFileOrWarn(file, "removeCryptedFile", logger)
        }
    }

    @AnyThread
    private fun saveDataToCryptedFile(data: ByteArray, filename: String) {
        if (masterKey == null) {
            logger.error("Unable to store prefs for key $filename")
            return
        }
        val file = File(context.filesDir, CRYPTED_FILE_PREFIX + filename)
        if (!file.exists()) {
            try {
                FileUtil.createNewFileOrLog(file, logger)
            } catch (e: Exception) {
                logger.error("Exception", e)
            }
        }

        try {
            FileOutputStream(file).use { fileOutputStream ->
                masterKey.getCipherOutputStream(fileOutputStream).use { cipherOutputStream ->
                    cipherOutputStream.write(data)
                }
            }
        } catch (e: IOException) {
            logger.error("Unable to store prefs", e)
        } catch (e: MasterKeyLockedException) {
            logger.error("Unable to store prefs", e)
        }
    }

    @WorkerThread
    private fun getDataFromCryptedFile(filename: String): ByteArray {
        if (masterKey == null) {
            logger.error("Unable to read prefs for key $filename")
            return ByteArray(0)
        }
        val file = File(context.filesDir, CRYPTED_FILE_PREFIX + filename)
        if (file.exists()) {
            var cis: CipherInputStream? = null
            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(file)
                cis = masterKey.getCipherInputStream(fis)
                return IOUtils.toByteArray(cis)
            } catch (x: Exception) {
                // do nothing
                logger.error("getDataFromCryptedFile: $filename", x)
            } finally {
                if (cis != null) {
                    try {
                        cis.close()
                    } catch (e: IOException) {
                        /**/
                    }
                }
                if (fis != null) {
                    try {
                        fis.close()
                    } catch (e: IOException) {
                        /**/
                    }
                }
            }
        }

        return ByteArray(0)
    }
}
