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

package ch.threema.app.logging

import android.content.Context
import android.os.Environment
import ch.threema.app.R
import ch.threema.app.stores.PreferenceStore
import ch.threema.logging.backend.DebugLogFileBackend
import java.io.File

class DebugLogHelper(
    private val appContext: Context,
    private val preferenceStore: PreferenceStore,
) {
    fun disableDebugLogFileIfNeeded() {
        if (!isDebugLogPreferenceEnabled() && !isDebugLogFileForceEnabled()) {
            DebugLogFileBackend.setEnabled(false)
        }
    }

    private fun isDebugLogPreferenceEnabled() =
        preferenceStore.getBoolean(appContext.getString(R.string.preferences__message_log_switch))

    private fun isDebugLogFileForceEnabled(): Boolean {
        val externalStorageDirectory = Environment.getExternalStorageDirectory()
        val forceDebugLogFile = File(externalStorageDirectory, "ENABLE_THREEMA_DEBUG_LOG")
        return forceDebugLogFile.exists()
    }
}
