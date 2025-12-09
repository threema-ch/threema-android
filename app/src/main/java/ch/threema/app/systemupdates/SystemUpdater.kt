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

package ch.threema.app.systemupdates

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import ch.threema.app.systemupdates.updates.SystemUpdate
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import java.util.LinkedList
import java.util.Queue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("SystemUpdater")

class SystemUpdater(
    private val sharedPreferences: SharedPreferences,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) {
    private val _systemUpdateState = MutableStateFlow(SystemUpdateState.INIT)
    val systemUpdateState = _systemUpdateState.asStateFlow()

    private lateinit var updates: Queue<SystemUpdate>
    private var newVersion: Int = NO_VERSION

    /**
     * Must be called on the main thread, to ensure that [systemUpdateState] can already return [SystemUpdateState.READY]
     * in case there are no updates.
     */
    @MainThread
    fun checkForUpdates(
        systemUpdateProvider: SystemUpdateProvider,
        initialVersion: Int?,
    ): Boolean {
        val oldVersion = getOldVersion(initialVersion)
        newVersion = systemUpdateProvider.getVersion()
        if (oldVersion == NO_VERSION) {
            // fresh installation, no need to run any system updates
            storeVersionNumber(newVersion)
            _systemUpdateState.value = SystemUpdateState.READY
            return false
        }

        updates = LinkedList(systemUpdateProvider.getUpdates(oldVersion))
        if (updates.isEmpty()) {
            if (oldVersion != newVersion) {
                storeVersionNumber(newVersion)
            }
            _systemUpdateState.value = SystemUpdateState.READY
            return false
        }
        return true
    }

    @Throws(SystemUpdateException::class)
    suspend fun runUpdates(): Unit = withContext(dispatcherProvider.worker) {
        check(newVersion != NO_VERSION) // must call checkForUpdates first
        _systemUpdateState.value = SystemUpdateState.PREPARING
        processUpdates()
        storeVersionNumber(newVersion)
        _systemUpdateState.value = SystemUpdateState.READY
    }

    private fun processUpdates() {
        while (!updates.isEmpty()) {
            val update = updates.remove()
            logger.info("Running system update to {}", update.fullDescription)
            try {
                update.run()
            } catch (e: Exception) {
                logger.error("Failed to run system update", e)
                throw SystemUpdateException(update.getVersion())
            }
            logger.info("System update to {} successful", update.fullDescription)
            storeVersionNumber(update.getVersion())
        }
    }

    private fun getOldVersion(initialVersion: Int?): Int =
        sharedPreferences.getInt(PREF_SYSTEM_UPDATE_VERSION, initialVersion ?: NO_VERSION)

    private fun storeVersionNumber(versionNumber: Int) {
        sharedPreferences.edit {
            putInt(PREF_SYSTEM_UPDATE_VERSION, versionNumber)
        }
    }

    private val SystemUpdate.fullDescription: String
        get() = buildString {
            append("version ${getVersion()}")
            getDescription()?.let { description ->
                append(" ($description)")
            }
        }

    companion object {
        private const val PREF_SYSTEM_UPDATE_VERSION = "system_update_version_number"
        private const val NO_VERSION = -1
    }
}
