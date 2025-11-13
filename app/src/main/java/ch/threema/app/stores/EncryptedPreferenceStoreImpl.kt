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

package ch.threema.app.stores

import android.content.Context
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.StringConversionUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.writeAtomically
import ch.threema.common.takeUnlessEmpty
import ch.threema.localcrypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.text.toByteArray
import org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONObject

private val logger = LoggingUtil.getThreemaLogger("EncryptedPreferenceStoreImpl")

class EncryptedPreferenceStoreImpl(
    private val context: Context,
    private val masterKey: MasterKey,
    private val onChanged: (key: String, value: Any?) -> Unit = { key, value ->
        ListenerManager.preferenceListeners.handle { listener: PreferenceListener ->
            listener.onChanged(key, value)
        }
    },
) : BasePreferenceStore(), EncryptedPreferenceStore {
    override fun remove(key: String) {
        val file = getEncryptedFile(key)
        if (file.exists()) {
            if (file.delete()) {
                logger.info("Deleted encrypted file for key {}", key)
            } else {
                logger.error("Failed to delete encrypted file for key {}", key)
            }
        }
    }

    override fun save(key: String, value: String?) {
        saveDataToEncryptedFile(StringConversionUtil.stringToByteArray(value), key)
        onChanged(key, value)
    }

    override fun save(key: String, value: Map<String, String?>) {
        val json = value.encodeToJSONArray()
        save(key, json)
    }

    override fun save(key: String, value: Array<String>) {
        saveQuietly(key, value)
        onChanged(key, value)
    }

    override fun saveQuietly(key: String, value: Array<String>) {
        saveDataToEncryptedFile(
            StringConversionUtil.stringToByteArray(value.encodeToString()),
            key,
        )
    }

    override fun save(key: String, value: ByteArray) {
        saveDataToEncryptedFile(value, key)
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONArray) {
        saveDataToEncryptedFile(value.toString().toByteArray(), key)
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONObject) {
        saveDataToEncryptedFile(value.toString().toByteArray(), key)
        onChanged(key, value)
    }

    override fun getString(key: String): String {
        val stringBytes: ByteArray = getDataFromEncryptedFile(key)
        return StringConversionUtil.byteArrayToString(stringBytes)
    }

    override fun getStringArray(key: String): Array<String>? {
        val bytes: ByteArray = getDataFromEncryptedFile(key)
        val value = StringConversionUtil.byteArrayToString(bytes)
        return value.takeUnlessEmpty()?.decodeToStringArray()
    }

    @Deprecated("only used in system update, use getMap instead")
    override fun getIntMap(key: String): HashMap<Int, String> =
        try {
            val jsonArray = JSONArray(String(getDataFromEncryptedFile(key)))
            HashMap(jsonArray.decodeToIntMap())
        } catch (e: Exception) {
            logger.error("Failed to decode stored int map", e)
            HashMap()
        }

    override fun getMap(key: String): Map<String, String?> {
        try {
            val bytes: ByteArray = getDataFromEncryptedFile(key)
            if (bytes.isNotEmpty()) {
                val jsonArray = JSONArray(String(bytes))
                return jsonArray.decodeToStringMap()
            }
        } catch (e: Exception) {
            logger.error("Failed to decode stored string map", e)
        }
        return emptyMap()
    }

    override fun getBytes(key: String): ByteArray =
        getDataFromEncryptedFile(key)

    override fun getJSONArray(key: String): JSONArray =
        try {
            val bytes = getDataFromEncryptedFile(key)
            if (bytes.isEmpty()) {
                JSONArray()
            } else {
                JSONArray(String(bytes))
            }
        } catch (e: Exception) {
            logger.error("Failed to decrypt or decode JSON Array", e)
            JSONArray()
        }

    override fun getJSONObject(key: String): JSONObject? =
        try {
            JSONObject(String(getDataFromEncryptedFile(key)))
        } catch (e: Exception) {
            logger.error("Failed to decrypt or decode JSON Object", e)
            null
        }

    override fun clear() {
        clear(context)
    }

    override fun containsKey(key: String): Boolean =
        getEncryptedFile(key).exists()

    private fun getEncryptedFile(key: String): File =
        File(context.filesDir, ENCRYPTED_FILE_PREFIX + key)

    private fun saveDataToEncryptedFile(data: ByteArray, key: String) {
        val file = getEncryptedFile(key)
        try {
            synchronized(this) {
                file.writeAtomically { fileOutputStream ->
                    masterKey.getCipherOutputStream(fileOutputStream).use { cipherOutputStream ->
                        cipherOutputStream.write(data)
                    }
                }
            }
        } catch (e: IOException) {
            // TODO(ANDR-4060): Throw a proper exception here, or don't catch the exception
            logger.error("Failed to store encrypted file with key {}", key, e)
        }
    }

    private fun getDataFromEncryptedFile(key: String): ByteArray {
        val file = getEncryptedFile(key)
        if (file.exists()) {
            try {
                FileInputStream(file).use { fileInputStream ->
                    masterKey.getCipherInputStream(fileInputStream).use { cipherInputStream ->
                        return IOUtils.toByteArray(cipherInputStream)
                    }
                }
            } catch (e: Exception) {
                // TODO(ANDR-4060): Throw a proper exception here
                logger.error("Failed to read data from encrypted file for key {}", key, e)
            }
        }
        // TODO(ANDR-4060): Use null instead of a "dummy" default value
        return ByteArray(0)
    }

    companion object {
        private const val ENCRYPTED_FILE_PREFIX = ".crs-"

        fun clear(context: Context) {
            try {
                context.filesDir
                    .listFiles { _, filename: String ->
                        filename.startsWith(ENCRYPTED_FILE_PREFIX)
                    }
                    ?.forEach { file ->
                        if (file.delete()) {
                            logger.info("Deleted encrypted file {}", file.name)
                        } else {
                            logger.error("Failed to delete encrypted file {}", file.name)
                        }
                    }
            } catch (e: Exception) {
                // TODO(ANDR-4060): Throw a proper exception here
                logger.error("Failed to clear encrypted store", e)
            }
        }
    }
}
