package ch.threema.app.files

import android.provider.MediaStore.MEDIA_IGNORE_FILENAME
import ch.threema.base.utils.Base32
import ch.threema.common.clearDirectoryRecursively
import ch.threema.common.files.FileHandle
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.sha256
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File
import java.io.IOException

class ProfilePictureFileHandleProvider(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun getUserDefinedProfilePicture(identity: IdentityString): FileHandle =
        getFileHandle(identity, prefix = ".c-")

    fun getContactDefinedProfilePicture(identity: IdentityString): FileHandle =
        getFileHandle(identity, prefix = ".p-")

    fun getAndroidDefinedProfilePicture(identity: IdentityString): FileHandle =
        getFileHandle(identity, prefix = ".a-")

    private fun getFileHandle(identity: IdentityString, prefix: String): FileHandle {
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
