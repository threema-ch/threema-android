/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.file

import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.testhelpers.willThrow
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

open class FileMessageTest {
    private val bytesEncryptionKey: ByteArray = "3415ea32b2ca51e73c670454f9abfbe5".toByteArray()
    private val bytesBlobIdContent: ByteArray = "126e82deb67b783c".toByteArray()
    private val bytesBlobIdThumbnail: ByteArray = "2ea7f8501da14be9".toByteArray()

    private val fileData = FileData()
        .apply {
            setEncryptionKey(bytesEncryptionKey)
            setFileBlobId(bytesBlobIdContent)
            setMimeType("image/jpg")
            setThumbnailBlobId(bytesBlobIdThumbnail)
            setThumbnailMimeType("image/jpg")
            setFileName("group_file_message_text_picture.jpg")
            setFileSize(1_000L)
            setRenderingType(FileData.RENDERING_MEDIA)
            setCaption("Cool group image file!")
            setCorrelationId("1234567890")
            setMetaData(
                mapOf(
                    "lat" to "secret",
                    "lng" to "secret",
                    "hour" to 8,
                    "minute" to 30,
                ),
            )
        }

    private val bytesFileData = fileData.let { fileData ->
        ByteArrayOutputStream().also(fileData::write)
    }.toByteArray()

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            FileMessage.fromByteArray(
                data = bytesFileData,
                offset = 0,
                length = 0,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetBelowZero() {
        // arrange
        val testBlockLazy = {
            // act
            FileMessage.fromByteArray(
                data = bytesFileData,
                offset = -1,
                length = 64,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLength() {
        // arrange
        val testBlockLazy = {
            // act
            FileMessage.fromByteArray(
                data = bytesFileData,
                offset = 0,
                length = bytesFileData.size + 1,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLengthWithOffset() {
        // arrange
        val testBlockLazy = {
            // act
            FileMessage.fromByteArray(
                data = bytesFileData,
                offset = 1,
                length = bytesFileData.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultFileMessage = FileMessage.fromByteArray(
            data = bytesFileData,
            offset = 0,
            length = bytesFileData.size,
        )

        // assert
        assertFileDataEquals(fileData, resultFileMessage.fileData)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + bytesFileData

        // act
        val resultFileMessage = FileMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = bytesFileData.size,
        )

        // assert
        assertFileDataEquals(fileData, resultFileMessage.fileData)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + bytesFileData

        val testBlockLazy = {
            // act
            FileMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = bytesFileData.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    private fun assertFileDataEquals(expected: FileData, actual: FileData?) {
        if (actual == null) {
            assertEquals(expected, null as FileData?)
            return
        }
        assertContentEquals(fileData.encryptionKey, actual.encryptionKey)
        assertContentEquals(fileData.fileBlobId, actual.fileBlobId)
        assertEquals(fileData.mimeType, actual.mimeType)
        assertContentEquals(fileData.thumbnailBlobId, actual.thumbnailBlobId)
        assertEquals(fileData.thumbnailMimeType, actual.thumbnailMimeType)
        assertEquals(fileData.fileName, actual.fileName)
        assertEquals(fileData.fileSize, actual.fileSize)
        assertEquals(fileData.renderingType, actual.renderingType)
        assertEquals(fileData.caption, actual.caption)
        assertEquals(fileData.correlationId, actual.correlationId)
        assertEquals(fileData.metaData.size, actual.metaData.size)
        fileData.metaData.forEach { (key, value) ->
            assertEquals(value, actual.metaData[key])
        }
    }
}
