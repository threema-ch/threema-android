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
import ch.threema.common.lastLine
import ch.threema.common.withoutLastLine
import java.io.File
import java.io.IOException
import java.time.Instant
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
    private val onPremConfigParser: OnPremConfigParser,
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
            obj = deserializeConfigJson(storedData)
                ?: return null,
            createdAt = deserializeCreatedAt(storedData)
                ?: return null,
        )
    }

    private fun deserializeConfigJson(data: String) =
        try {
            JSONObject(data.withoutLastLine())
        } catch (_: JSONException) {
            null
        }

    private fun deserializeCreatedAt(data: String) =
        data.lastLine()
            .toLongOrNull()
            ?.let(Instant::ofEpochMilli)

    @Throws(IOException::class)
    fun store(config: JSONObject) {
        storeFile.writeText(serialize(config, createdAt = timeProvider.get()))
    }

    private fun serialize(config: JSONObject, createdAt: Instant) =
        buildString {
            append(config.toString())
            append("\n")
            append(createdAt.toEpochMilli())
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
