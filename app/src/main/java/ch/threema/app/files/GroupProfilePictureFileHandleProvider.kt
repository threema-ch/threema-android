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
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.localcrypto.MasterKeyProvider

class GroupProfilePictureFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun get(groupDatabaseId: GroupDatabaseId): FileHandle {
        return appDirectoryProvider.userFilesDirectory.fileHandle(
            directory = GROUP_PROFILE_PICTURES_DIRECTORY,
            name = ".gpp-$groupDatabaseId",
        )
            .withFallback(
                appDirectoryProvider.legacyUserFilesDirectory.fileHandle(
                    directory = LEGACY_GROUP_PROFILE_PICTURES_DIRECTORY,
                    name = ".grp-avatar-$groupDatabaseId",
                ),
            )
            .withEncryption(masterKeyProvider)
    }

    companion object {
        private const val GROUP_PROFILE_PICTURES_DIRECTORY = ".group-profile-pictures"
        private const val LEGACY_GROUP_PROFILE_PICTURES_DIRECTORY = ".grp-avatar"
    }
}
