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

package ch.threema.app.files

import ch.threema.common.files.FileHandle
import ch.threema.domain.types.MessageUid
import ch.threema.localcrypto.MasterKeyProvider

class MessageFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun get(messageUid: MessageUid): FileHandle =
        getFileHandle(
            fileName = getFileName(messageUid),
        )

    fun getThumbnail(messageUid: MessageUid): FileHandle =
        getFileHandle(
            fileName = getFileName(messageUid) + "_T",
        )

    private fun getFileHandle(fileName: String): FileHandle =
        appDirectoryProvider.userFilesDirectory.fileHandle(
            directory = CONVERSATION_MESSAGE_FILE_DIRECTORY,
            name = fileName,
        )
            .withFallback(
                appDirectoryProvider.legacyUserFilesDirectory.fileHandle(
                    name = fileName,
                ),
            )
            .withEncryption(masterKeyProvider)

    private fun getFileName(messageUid: MessageUid): String =
        "." + messageUid.replace(INVALID_CHARACTERS_REGEX, "")

    companion object {
        private const val CONVERSATION_MESSAGE_FILE_DIRECTORY = ".message-files"

        private val INVALID_CHARACTERS_REGEX = "[^a-zA-Z0-9\\\\s]".toRegex()
    }
}
