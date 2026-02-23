package ch.threema.app.push

import android.content.Context
import android.content.pm.PackageManager
import ch.threema.base.utils.getThreemaLogger
import com.huawei.agconnect.AGConnectOptionsBuilder

private val logger = getThreemaLogger("HmsTokenUtil")

object HmsTokenUtil {
    const val TOKEN_SCOPE = "HCM"

    private const val APP_ID_CONFIG_FIELD = "client/app_id"
    private const val APP_ID_META_DATA_KEY = "com.huawei.hms.client.appid"

    /**
     * Obtain the app ID from the `agconnect-services.json` file.
     *
     * @return The app id from json config file, or hardcoded value if
     * it could not be obtained from file.
     */
    @JvmStatic
    fun getHmsAppId(context: Context): String? {
        val appId = try {
            AGConnectOptionsBuilder()
                .build(context)
                .getString(APP_ID_CONFIG_FIELD)
        } catch (e: Exception) {
            logger.error("Could not obtain HMS-App-ID from config file. Fallback to hardcoded ID.", e)
            null
        }
        return appId ?: readAppIdFromManifest(context)
    }

    /**
     * Prepend the provided hms app id to the push token delimited by "|" so the token can be used
     * by the chat server to send pushes.
     */
    @JvmStatic
    fun prependHmsAppId(appId: String?, token: String?): String? {
        return if (appId != null && token != null) {
            "$appId|$token"
        } else {
            null
        }
    }

    /**
     * Obtain the the hms app id and prepend it to the push token delimited by "|" so the token can
     * be used by the chat server to send pushes.
     *
     * @param context The application context
     * @param token The token that has to be formatted
     * @return The formatted token, or null if the token is null or the app id could not be obtained
     */
    @JvmStatic
    fun obtainAndPrependHmsAppId(context: Context, token: String?): String? {
        val appId = getHmsAppId(context)
        return prependHmsAppId(appId, token)
    }

    private fun readAppIdFromManifest(context: Context): String? {
        return try {
            logger.info("Read app id from manifest")
            val appMetaData = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
            return when (val appId = appMetaData.getInt(APP_ID_META_DATA_KEY)) {
                0 -> null
                else -> appId.toString()
            }
        } catch (exception: Throwable) {
            logger.error("Could not read app id from manifest", exception)
            null
        }
    }
}
