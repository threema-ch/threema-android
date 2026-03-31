package ch.threema.app.stores

import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

interface EncryptedPreferenceStore {

    fun remove(key: String)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: String?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: Map<String, String?>?)

    /**
     * Warning: strings in array must NOT contain ";" characters and must NOT be empty.
     *
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: Array<String>?)

    /**
     * Save list preference quietly without firing a UI listener event (for use in workers or other background processing)
     * Warning: strings in array must NOT contain ";" characters and must NOT be empty.
     *
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun saveQuietly(key: String, value: Array<String>?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: ByteArray?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: JSONArray?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: JSONObject?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored string, or null if no such value is stored
     */
    fun getString(key: String): String?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored bytes, or null if no such value is stored
     */
    fun getBytes(key: String): ByteArray?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     */
    fun getStringArray(key: String): Array<String>?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored map, or null if no such value is stored or if the value is not a valid map
     */
    fun getMap(key: String): Map<String, String?>?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @return The stored JSON array, or null if no such value is stored or if the value is not a valid JSON array
     */
    fun getJSONArray(key: String): JSONArray?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored JSON object, or null if no such value is stored or if the value is not a valid JSON object
     */
    fun getJSONObject(key: String): JSONObject?

    fun containsKey(key: String): Boolean

    fun clear()

    companion object {
        const val PREFS_PRIVATE_KEY = "private_key"
        const val PREFS_MD_PROPERTIES = "md_properties"
    }
}
