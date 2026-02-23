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
