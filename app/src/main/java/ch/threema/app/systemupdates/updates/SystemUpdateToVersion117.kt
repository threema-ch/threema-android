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

package ch.threema.app.systemupdates.updates

import ch.threema.base.utils.getThreemaLogger
import java.security.KeyStore

private val logger = getThreemaLogger("SystemUpdateToVersion117")

class SystemUpdateToVersion117 : SystemUpdate {

    override fun run() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_NAME)
        } catch (e: Exception) {
            logger.warn("Failed to delete key", e)
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "delete obsolete pinlock key from keystore"

    companion object {
        const val VERSION = 117

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_NAME = "threema_pinlock_key"
    }
}
