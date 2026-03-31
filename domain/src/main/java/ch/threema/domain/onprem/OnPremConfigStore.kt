package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.lastLine
import ch.threema.common.withoutLastLine
import java.io.File
import java.io.IOException
import java.time.Instant
import org.json.JSONException
import org.json.JSONObject

private val logger = getThreemaLogger("OnPremConfigStore")

/**
 * Stores the OnPrem configuration into a file (together with the time at which it was stored), such that
 * the OnPrem config is also available in situations where the OPPF can not be fetched from the server.
 * This enables the following case:
 * - the config is used to read the work server base URL when the Remote Secrets feature is enabled.
 *   This is **critical** as it is not possible to fetch a new config from the server until the master key is unlocked.
 * - the config is used to enforce on-prem specific certificate pinning
 */
class OnPremConfigStore(
    baseDirectory: File,
    private val timeProvider: TimeProvider,
    private val onPremConfigParser: OnPremConfigParser,
    private val onPremConfigVerifier: OnPremConfigVerifier,
) {
    private val storeFile = File(baseDirectory, STORE_FILE)

    /**
     * Returns the stored on prem config, if one is available.
     * Note that the config may be expired, in which case a new one should be fetched using [OnPremConfigFetcher].
     */
    fun get(): OnPremConfig? {
        if (!storeFile.exists()) {
            return null
        }
        val storedData = try {
            storeFile.readText()
        } catch (_: IOException) {
            return null
        }
        return onPremConfigParser.parse(
            obj = deserializeConfig(storedData.withoutLastLine())
                ?: return null,
            createdAt = deserializeCreatedAt(storedData.lastLine())
                ?: return null,
        )
    }

    private fun deserializeConfig(data: String): JSONObject? =
        try {
            onPremConfigVerifier.verify(data)
        } catch (e: ThreemaException) {
            logger.warn("Failed to read or validate stored on-prem config", e)

            // TODO(ANDR-4431): The file used to just contain the plain JSON of the OPPF without the signature. To deal with old files,
            //  we here fall back to trying to read the file's contents as JSON. This fallback should eventually be removed and null
            //  returned directly.
            try {
                JSONObject(data)
            } catch (_: JSONException) {
                null
            }
        }

    private fun deserializeCreatedAt(data: String) =
        data.toLongOrNull()?.let(Instant::ofEpochMilli)

    @Throws(IOException::class)
    fun store(oppfString: String) {
        storeFile.writeText(serialize(oppfString, createdAt = timeProvider.get()))
    }

    private fun serialize(oppfString: String, createdAt: Instant) =
        buildString {
            append(oppfString)
            append("\n")
            append(createdAt.toEpochMilli())
        }

    companion object {
        private const val STORE_FILE = "onprem_config"
    }
}
