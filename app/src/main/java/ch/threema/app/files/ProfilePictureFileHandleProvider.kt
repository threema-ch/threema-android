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

import android.provider.MediaStore.MEDIA_IGNORE_FILENAME
import ch.threema.base.utils.Base32
import ch.threema.common.clearDirectoryRecursively
import ch.threema.common.files.FileHandle
import ch.threema.domain.types.Identity
import ch.threema.libthreema.sha256
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File
import java.io.IOException

class ProfilePictureFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun getUserDefinedProfilePicture(identity: Identity): FileHandle =
        getFileHandle(identity, prefix = ".c-")

    fun getContactDefinedProfilePicture(identity: Identity): FileHandle =
        getFileHandle(identity, prefix = ".p-")

    fun getAndroidDefinedProfilePicture(identity: Identity): FileHandle =
        getFileHandle(identity, prefix = ".a-")

    private fun getFileHandle(identity: Identity, prefix: String): FileHandle {
        val fileName = getFileName(prefix, identity)
        return appDirectoryProvider.userFilesDirectory.fileHandle(
            directory = PROFILE_PICTURES_DIRECTORY,
            name = fileName,
        )
            .withFallback(
                appDirectoryProvider.legacyUserFilesDirectory.fileHandle(
                    directory = LEGACY_PROFILE_PICTURES_DIRECTORY,
                    name = fileName + MEDIA_IGNORE_FILENAME,
                ),
            )
            .withEncryption(masterKeyProvider)
    }

    @Throws(IOException::class)
    fun deleteAll() {
        File(appDirectoryProvider.userFilesDirectory, PROFILE_PICTURES_DIRECTORY).clearDirectoryRecursively()
        File(appDirectoryProvider.legacyUserFilesDirectory, LEGACY_PROFILE_PICTURES_DIRECTORY).clearDirectoryRecursively()
    }

    companion object {
        private const val PROFILE_PICTURES_DIRECTORY = ".profile-pictures"
        private const val LEGACY_PROFILE_PICTURES_DIRECTORY = ".avatar"

        private fun getFileName(prefix: String, identity: String): String {
            val identityHash = sha256("c-$identity".toByteArray())
            return prefix + Base32.encode(identityHash)
        }
    }
}
