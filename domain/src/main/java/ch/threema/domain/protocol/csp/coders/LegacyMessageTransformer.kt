package ch.threema.domain.protocol.csp.coders

import ch.threema.common.readByteArray
import ch.threema.common.readLittleEndianInt
import ch.threema.common.readLittleEndianShort
import ch.threema.common.readUtf8String
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import java.io.ByteArrayInputStream
import java.io.IOException

object LegacyMessageTransformer {
    private const val AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
    private const val AUDIO_SIZE_INT_BYTE_LENGTH = 4
    private const val VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
    private const val VIDEO_SIZE_INT_BYTE_LENGTH = 4
    private const val THUMBNAIL_SIZE_INT_BYTE_LENGTH = 4

    private const val AUDIO_FILE_DATA_LENGTH = AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + AUDIO_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_KEY_LEN
    private const val VIDEO_FILE_DATA_LENGTH = VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + VIDEO_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + THUMBNAIL_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_KEY_LEN

    /**
     * Parses the [data] of a audio message and transforms it into a [FileMessage].
     *
     * The [data] byte array consists of:
     *  - audio-duration short (length 4)
     *  - audio-blob-id bytes (length 16)
     *  - audio-size int (length 4)
     *  - encryption-key bytes (length 32)
     *
     * @param data the data that represents the audio message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformAudioMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): FileMessage {
        if (length < AUDIO_FILE_DATA_LENGTH) {
            throw BadMessageException("Bad length ($length) for audio message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        val bis = ByteArrayInputStream(data, offset, length)
        try {
            val fileData = bis.readAudioFileData()
            return FileMessage().apply {
                this.fileData = fileData
            }
        } catch (e: IOException) {
            throw BadMessageException("Message body contents failed to parse", e)
        }
    }

    private fun ByteArrayInputStream.readAudioFileData(): FileData {
        val durationInSeconds = readLittleEndianShort()
        val audioBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val audioSizeInBytes = readLittleEndianInt()
        val encryptionKey = readByteArray(ProtocolDefines.BLOB_KEY_LEN)
        return FileData().apply {
            setMimeType("audio/aac")
            setFileBlobId(audioBlobId)
            setFileSize(audioSizeInBytes.toLong())
            setRenderingType(FileData.RENDERING_MEDIA)
            setMetaData(mapOf("d" to durationInSeconds.toInt()))
            setEncryptionKey(encryptionKey)
        }
    }

    /**
     * Parses the [data] of a video message and transforms it into a [FileMessage].
     *
     * The [data] byte array consists of:
     *  - video-length short (length 2)
     *  - video-blob-id (length 16)
     *  - video-size int (length 4)
     *  - thumbnail-blob-id (length 16)
     *  - thumbnail-size int (length 4)
     *  - encryption-key (length 32)
     *
     * @param data the data that represents the video message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformVideoMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): FileMessage {
        if (length < VIDEO_FILE_DATA_LENGTH) {
            throw BadMessageException("Bad length ($length) for video message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        val bis = ByteArrayInputStream(data, offset, length)
        try {
            val fileData = bis.readVideoFileData()
            return FileMessage().apply {
                this.fileData = fileData
            }
        } catch (e: IOException) {
            throw BadMessageException("Message body contents failed to parse", e)
        }
    }

    private fun ByteArrayInputStream.readVideoFileData(): FileData {
        val durationInSeconds = readLittleEndianShort()
        val videoBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val videoSizeInBytes = readLittleEndianInt()
        val thumbnailBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        // Skip the thumbnail size
        readLittleEndianInt()
        val encryptionKey = readByteArray(ProtocolDefines.BLOB_KEY_LEN)
        return FileData().apply {
            setMimeType("video/mpeg")
            setFileBlobId(videoBlobId)
            setFileSize(videoSizeInBytes.toLong())
            setThumbnailBlobId(thumbnailBlobId)
            setThumbnailMimeType("image/jpeg")
            setRenderingType(FileData.RENDERING_MEDIA)
            setMetaData(mapOf("d" to durationInSeconds.toInt()))
            setEncryptionKey(encryptionKey)
        }
    }

    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformGroupAudioMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): GroupFileMessage {
        val minLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + AUDIO_FILE_DATA_LENGTH
        if (length < minLength) {
            throw BadMessageException("Bad length ($length) for group audio message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        val bis = ByteArrayInputStream(data, offset, length)
        try {
            val groupCreator = bis.readUtf8String(ProtocolDefines.IDENTITY_LEN)
            val groupId = GroupId(bis.readByteArray(ProtocolDefines.GROUP_ID_LEN))
            val fileData = bis.readAudioFileData()
            return GroupFileMessage().apply {
                this.groupCreator = groupCreator
                this.apiGroupId = groupId
                this.fileData = fileData
            }
        } catch (e: IOException) {
            throw BadMessageException("Message body contents failed to parse", e)
        }
    }

    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformGroupVideoMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): GroupFileMessage {
        val minLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + VIDEO_FILE_DATA_LENGTH
        if (length < minLength) {
            throw BadMessageException("Bad length ($length) for group video message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        val bis = ByteArrayInputStream(data, offset, length)
        try {
            val groupCreator = bis.readUtf8String(ProtocolDefines.IDENTITY_LEN)
            val groupId = GroupId(bis.readByteArray(ProtocolDefines.GROUP_ID_LEN))
            val fileData = bis.readVideoFileData()
            return GroupFileMessage().apply {
                this.groupCreator = groupCreator
                this.apiGroupId = groupId
                this.fileData = fileData
            }
        } catch (e: IOException) {
            throw BadMessageException("Message body contents failed to parse", e)
        }
    }
}
