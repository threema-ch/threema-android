package ch.threema.app.stores

import ch.threema.android.writeAtomically
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File
import java.io.FileInputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val logger = getThreemaLogger("EncryptedPreferenceStoreImpl")

class EncryptedPreferenceStoreImpl(
    private val directory: File,
    private val masterKeyProvider: MasterKeyProvider,
    private val onChanged: (key: String, value: Any?) -> Unit,
) : BasePreferenceStore(), EncryptedPreferenceStore {
    override fun remove(key: String) {
        synchronized(this) {
            val file = getEncryptedFile(key)
            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Deleted encrypted file for key {}", key)
                } else {
                    logger.error("Failed to delete encrypted file for key {}", key)
                }
            }
        }
    }

    override fun save(key: String, value: String?) {
        saveDataToEncryptedFile(key, value?.toByteArray())
        onChanged(key, value)
    }

    override fun save(key: String, value: Map<String, String?>?) {
        save(key, value?.encodeToJSONArray())
    }

    override fun save(key: String, value: Array<String>?) {
        saveQuietly(key, value)
        onChanged(key, value)
    }

    override fun saveQuietly(key: String, value: Array<String>?) {
        saveDataToEncryptedFile(key, value?.encodeToString()?.toByteArray())
    }

    override fun save(key: String, value: ByteArray?) {
        saveDataToEncryptedFile(key, value)
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONArray?) {
        saveDataToEncryptedFile(key, value?.toString()?.toByteArray())
        onChanged(key, value)
    }

    override fun save(key: String, value: JSONObject?) {
        saveDataToEncryptedFile(key, value?.toString()?.toByteArray())
        onChanged(key, value)
    }

    override fun getString(key: String): String? =
        getDataFromEncryptedFile(key)?.decodeToString()

    override fun getStringArray(key: String): Array<String>? {
        val bytes = getDataFromEncryptedFile(key)
        return bytes?.decodeToString()?.decodeToStringArray()
    }

    override fun getMap(key: String): Map<String, String?>? {
        try {
            val bytes = getDataFromEncryptedFile(key)
                ?: return null
            val jsonArray = JSONArray(String(bytes))
            return jsonArray.decodeToStringMap()
        } catch (e: JSONException) {
            logger.error("Failed to decode stored string map", e)
            return null
        }
    }

    override fun getBytes(key: String): ByteArray? =
        getDataFromEncryptedFile(key)

    override fun getJSONArray(key: String): JSONArray? {
        val bytes = getDataFromEncryptedFile(key)
            ?: return null
        return try {
            JSONArray(String(bytes))
        } catch (e: JSONException) {
            logger.error("Failed to decrypt or decode JSON Array", e)
            null
        }
    }

    override fun getJSONObject(key: String): JSONObject? =
        getDataFromEncryptedFile(key)
            ?.let { value ->
                try {
                    JSONObject(String(value))
                } catch (e: JSONException) {
                    logger.error("Failed to decode JSON Object", e)
                    null
                }
            }

    override fun clear() {
        synchronized(this) {
            directory
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
        }
    }

    override fun containsKey(key: String): Boolean =
        getEncryptedFile(key).exists()

    private fun getEncryptedFile(key: String): File =
        File(directory, ENCRYPTED_FILE_PREFIX + key)

    private fun saveDataToEncryptedFile(key: String, data: ByteArray?) {
        if (data == null) {
            remove(key)
            return
        }
        val masterKey = masterKeyProvider.getMasterKey()
        val file = getEncryptedFile(key)
        try {
            synchronized(this) {
                file.writeAtomically { fileOutputStream ->
                    masterKey.encrypt(fileOutputStream).use { cipherOutputStream ->
                        cipherOutputStream.write(data)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to store encrypted file with key {}", key, e)
            throw e
        }
    }

    private fun getDataFromEncryptedFile(key: String): ByteArray? {
        val file = getEncryptedFile(key)
        if (!file.exists()) {
            return null
        }
        val masterKey = masterKeyProvider.getMasterKey()
        try {
            return FileInputStream(file).use { fileInputStream ->
                masterKey.decrypt(fileInputStream).use { cipherInputStream ->
                    cipherInputStream.readBytes()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to read data from encrypted file for key {}", key, e)
            throw e
        }
    }

    companion object {
        private const val ENCRYPTED_FILE_PREFIX = ".crs-"
    }
}
