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

package ch.threema.domain.onprem

import ch.threema.common.TimeProvider
import ch.threema.common.withoutLastLine
import java.io.File
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the OnPrem configuration in a persistent way, which enables the following case:
 * - the config is used to read the work server base URL when the Remote Secrets feature is enabled.
 *   This is **critical** as it is not possible to fetch a new config from the server until the master key is unlocked.
 */
class OnPremConfigStore(
    baseDirectory: File,
    private val timeProvider: TimeProvider,
    private val onPremConfigParser: OnPremConfigParser = OnPremConfigParser(),
) {
    private val storeFile = File(baseDirectory, STORE_FILE)

    /**
     * Returns the stored on prem config, if one is available.
     * Note that the config may be expired, in which case a new one should be fetched using [OnPremConfigFetcher].
     */
    fun get(): OnPremConfig? = if (storeFile.exists()) {
        try {
            val data = storeFile.readText()

            // The last line contains the time at which the config was stored. We need to remove it to get the config JSON.
            val configJson = data.withoutLastLine()
            onPremConfigParser.parse(JSONObject(configJson))
        } catch (_: JSONException) {
            null
        } catch (_: IOException) {
            null
        }
    } else {
        null
    }

    @Throws(IOException::class)
    fun store(oppf: JSONObject) {
        storeFile.writeText(
            buildString {
                append(oppf.toString())
                append("\n")
                // We append the time at which the file was last updated. This information is currently not used.
                append(timeProvider.get().toEpochMilli())
            },
        )
    }

    /**
     * Clears the store.
     * This should only be called when deleting the identity, as it may not always be possible to fetch the config again.
     */
    fun reset() {
        storeFile.delete()
    }

    companion object {
        private const val STORE_FILE = "onprem_config"
    }
}
