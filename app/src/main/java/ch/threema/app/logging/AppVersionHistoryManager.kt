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
import androidx.core.content.edit
import ch.threema.common.TimeProvider
import java.time.Instant

class AppVersionHistoryManager(
    appContext: Context,
    private val timeProvider: TimeProvider,
    private val currentVersionName: String,
    private val currentVersionCode: Int,
) {
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun check(): AppVersionCheckResult {
        val latestRecord = getLatestRecord()
            ?: return AppVersionCheckResult.NoPreviousVersion
        if (latestRecord.versionCode != currentVersionCode) {
            return AppVersionCheckResult.DifferentVersion(latestRecord)
        }
        return AppVersionCheckResult.SameVersion
    }

    private fun getLatestRecord(): AppVersionRecord? =
        preferences.getString(KEY_LATEST_RECORD, null)
            ?.let(::deserialize)

    fun record(): AppVersionRecord {
        val newRecord = AppVersionRecord(
            versionName = currentVersionName,
            versionCode = currentVersionCode,
            time = timeProvider.get(),
        )
        preferences.edit {
            val newRecordSerialized = newRecord.serialize()
            val newHistory = preferences.getString(KEY_HISTORY, null)
                ?.plus("\n$newRecordSerialized")
                ?.trim()
                ?: newRecordSerialized

            putString(KEY_LATEST_RECORD, newRecordSerialized)
            putString(KEY_HISTORY, newHistory)
        }
        return newRecord
    }

    fun getHistory(): List<AppVersionRecord> =
        preferences.getString(KEY_HISTORY, null)
            ?.lines()
            ?.mapNotNull(::deserialize)
            ?: emptyList()

    companion object {
        private const val PREF_NAME = "app_version_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_LATEST_RECORD = "latest_record"
        private const val SEPARATOR = ";;"

        private fun AppVersionRecord.serialize() =
            "$versionCode$SEPARATOR$versionName$SEPARATOR${time.toEpochMilli()}"

        private fun deserialize(line: String): AppVersionRecord? {
            val parts = line.split(SEPARATOR)
            if (parts.size < 3) {
                return null
            }
            return AppVersionRecord(
                versionCode = parts[0].toIntOrNull()
                    ?: return null,
                versionName = parts[1],
                time = parts[2].toLongOrNull()
                    ?.let(Instant::ofEpochMilli)
                    ?: return null,
            )
        }
    }
}
