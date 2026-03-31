package ch.threema.domain.protocol.csp.coders

import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.file.FileData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LegacyMessageTransformerTest {
    @Test
    fun `transform audio message`() {
        val messageBody = byteArrayOf(
            // 2 bytes of audio length (little endian, in seconds)
            5, 1,
            // 16 bytes of blob id
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16,
            // 4 bytes of audio size (little endian, in bytes)
            8, 0, 1, 0,
            // 32 bytes of encryption key
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        )

        val message = LegacyMessageTransformer.transformAudioMessage(messageBody)

        assertNull(message.fromIdentity)
        val fileData = message.fileData
        assertNotNull(fileData)
        assertEquals(
            "audio/aac",
            fileData.mimeType,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16),
            fileData.fileBlobId,
        )
        assertEquals(
            0x10008,
            fileData.fileSize,
        )
        assertNull(fileData.thumbnailBlobId)
        assertNull(fileData.thumbnailMimeType)
        assertEquals(
            FileData.RENDERING_MEDIA,
            fileData.renderingType,
        )
        assertEquals(
            mapOf("d" to 0x105),
            fileData.metaData,
        )
        assertContentEquals(
            byteArrayOf(
                32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
            ),
            fileData.encryptionKey,
        )
    }

    @Test
    fun `transform video message`() {
        val messageBody = byteArrayOf(
            // 2 bytes of video length (little endian, in seconds)
            5, 1,
            // 16 bytes of blob id
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16,
            // 4 bytes of video size (little endian, in bytes)
            8, 0, 1, 0,
            // 16 bytes of thumbnail blob id
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1,
            // 4 bytes of thumbnail size (little endian, ignored)
            0, 0, 0, 0,
            // 32 bytes of encryption key
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        )

        val message = LegacyMessageTransformer.transformVideoMessage(messageBody)

        assertNull(message.fromIdentity)
        val fileData = message.fileData
        assertNotNull(fileData)
        assertEquals(
            "video/mpeg",
            fileData.mimeType,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16),
            fileData.fileBlobId,
        )
        assertEquals(
            0x10008,
            fileData.fileSize,
        )
        assertContentEquals(
            byteArrayOf(16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1),
            fileData.thumbnailBlobId,
        )
        assertEquals(
            "image/jpeg",
            fileData.thumbnailMimeType,
        )
        assertEquals(
            FileData.RENDERING_MEDIA,
            fileData.renderingType,
        )
        assertEquals(
            mapOf("d" to 0x105),
            fileData.metaData,
        )
        assertContentEquals(
            byteArrayOf(
                32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
            ),
            fileData.encryptionKey,
        )
    }

    @Test
    fun `transform group audio message`() {
        val messageBody = byteArrayOf(
            // 8 bytes of group creator identity
            65, 66, 67, 68, 69, 70, 71, 72,
            // 8 bytes of group id
            10, 11, 12, 13, 14, 15, 16, 17,
            // 2 bytes of audio length (little endian, in seconds)
            5, 1,
            // 16 bytes of blob id
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16,
            // 4 bytes of audio size (little endian, in bytes)
            8, 0, 1, 0,
            // 32 bytes of encryption key
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        )

        val message = LegacyMessageTransformer.transformGroupAudioMessage(messageBody)

        assertNull(message.fromIdentity)
        assertEquals(
            "ABCDEFGH",
            message.groupCreator,
        )
        assertEquals(
            GroupId("0a0b0c0d0e0f1011"),
            message.apiGroupId,
        )
        val fileData = message.fileData
        assertNotNull(fileData)
        assertEquals(
            "audio/aac",
            fileData.mimeType,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16),
            fileData.fileBlobId,
        )
        assertEquals(
            0x10008,
            fileData.fileSize,
        )
        assertNull(fileData.thumbnailBlobId)
        assertNull(fileData.thumbnailMimeType)
        assertEquals(
            FileData.RENDERING_MEDIA,
            fileData.renderingType,
        )
        assertEquals(
            mapOf("d" to 0x105),
            fileData.metaData,
        )
        assertContentEquals(
            byteArrayOf(
                32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
            ),
            fileData.encryptionKey,
        )
    }

    @Test
    fun `transform group video message`() {
        val messageBody = byteArrayOf(
            // 8 bytes of group creator identity
            65, 66, 67, 68, 69, 70, 71, 72,
            // 8 bytes of group id
            10, 11, 12, 13, 14, 15, 16, 17,
            // 2 bytes of video length (little endian, in seconds)
            5, 1,
            // 16 bytes of blob id
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16,
            // 4 bytes of video size (little endian, in bytes)
            8, 0, 1, 0,
            // 16 bytes of thumbnail blob id
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1,
            // 4 bytes of thumbnail size (little endian, ignored)
            0, 0, 0, 0,
            // 32 bytes of encryption key
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        )

        val message = LegacyMessageTransformer.transformGroupVideoMessage(messageBody)

        assertNull(message.fromIdentity)
        assertEquals(
            "ABCDEFGH",
            message.groupCreator,
        )
        assertEquals(
            GroupId("0a0b0c0d0e0f1011"),
            message.apiGroupId,
        )
        val fileData = message.fileData
        assertNotNull(fileData)
        assertEquals(
            "video/mpeg",
            fileData.mimeType,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 15, 16),
            fileData.fileBlobId,
        )
        assertEquals(
            0x10008,
            fileData.fileSize,
        )
        assertContentEquals(
            byteArrayOf(16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1),
            fileData.thumbnailBlobId,
        )
        assertEquals(
            "image/jpeg",
            fileData.thumbnailMimeType,
        )
        assertEquals(
            FileData.RENDERING_MEDIA,
            fileData.renderingType,
        )
        assertEquals(
            mapOf("d" to 0x105),
            fileData.metaData,
        )
        assertContentEquals(
            byteArrayOf(
                32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
            ),
            fileData.encryptionKey,
        )
    }
}
