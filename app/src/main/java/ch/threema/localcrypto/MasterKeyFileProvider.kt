package ch.threema.localcrypto

import java.io.File

class MasterKeyFileProvider(
    private val directory: File,
) {
    fun getVersion2MasterKeyFile(): File =
        File(directory, VERSION2_KEY_FILE_NAME)

    fun getVersion1MasterKeyFile(): File =
        File(directory, VERSION1_KEY_FILE_NAME)

    companion object {
        private const val VERSION2_KEY_FILE_NAME = "master_key.dat"
        private const val VERSION1_KEY_FILE_NAME = "key.dat"
    }
}
