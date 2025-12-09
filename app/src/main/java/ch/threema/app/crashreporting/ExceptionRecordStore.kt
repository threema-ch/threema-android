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

package ch.threema.app.crashreporting

import android.content.Context
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.common.clearDirectoryRecursively
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
class ExceptionRecordStore(
    private val recordsDirectory: File,
    private val timeProvider: TimeProvider,
    private val uuidGenerator: UUIDGenerator,
) {
    fun storeException(e: Throwable) {
        val id = uuidGenerator.generate()
        val exceptionRecord = ExceptionRecord(
            id = id,
            stackTrace = e.stackTraceToString(),
            createdAt = timeProvider.get(),
        )

        recordsDirectory.mkdir()
        val recordFile = File(recordsDirectory, id + FILE_SUFFIX)

        recordFile.outputStream().use { outputStream ->
            Json.encodeToStream(exceptionRecord, outputStream)
        }
    }

    fun hasRecords(): Boolean =
        recordsDirectory.listFiles { file ->
            file.name.endsWith(FILE_SUFFIX)
        }
            ?.isNotEmpty() == true

    fun deleteRecords() {
        recordsDirectory.clearDirectoryRecursively()
    }

    companion object {
        fun getRecordsDirectory(context: Context): File =
            File(context.filesDir, "exceptions")

        private const val FILE_SUFFIX = "_v1.json"
    }
}
