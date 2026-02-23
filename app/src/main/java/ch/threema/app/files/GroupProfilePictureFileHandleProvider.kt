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
