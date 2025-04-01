/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.push

import android.content.Context
import ch.threema.app.BuildFlavor
import ch.threema.base.utils.LoggingUtil
import com.huawei.agconnect.AGConnectOptionsBuilder

private val logger = LoggingUtil.getThreemaLogger("HmsTokenUtil")

object HmsTokenUtil {

    const val TOKEN_SCOPE = "HCM"

    private const val APP_ID_CONFIG_FIELD = "client/app_id"

    // TODO(ANDR-3192): Remove hardcoded app-ids when plugin can read them from json config again
    private val appIdHardcoded: String?
        get() = when (BuildFlavor.current) {
            is BuildFlavor.Hms -> "103713829"
            is BuildFlavor.HmsWork -> "103858571"
            else -> null
        }

    /**
     * Obtain the app ID from the `agconnect-services.json` file.
     *
     * @return The app id from json config file, or hardcoded value if
     * it could not be obtained from file.
     */
    @JvmStatic
    fun getHmsAppId(context: Context): String? {
        return try {
            AGConnectOptionsBuilder()
                .build(context)
                .getString(APP_ID_CONFIG_FIELD)
                ?: appIdHardcoded
        } catch (e: Exception) {
            logger.error(
                "Could not obtain HMS-App-ID from config file. Fallback to hardcoded ID.",
                e
            )
            appIdHardcoded
        }
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
}
