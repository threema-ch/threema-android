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

open class GroupFileMessageTest {
    /**
     *  A group file message in the raw form consists of bytes in the following order:
     *
     *  (creator identity bytes (8)) + (api groupId bytes (8)) + ([FileData] bytes (*))
     */

    private val bytesCreatorIdentity: ByteArray = "9e979235".toByteArray()
    private val bytesApiGroupId: ByteArray = "fdc34493".toByteArray()

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

    private val groupFileMessageBytes = bytesCreatorIdentity + bytesApiGroupId + bytesFileData

    /**
     *  creator identity length = *8*, api groupId length = *8*
     */
    @Test
    fun shouldThrowBadMessageExceptionWhenLengthBelowIdentityAndGroupIdLength() {
        // arrange
        val testBlockLazy = {
            // act
            GroupFileMessage.fromByteArray(
                data = groupFileMessageBytes,
                offset = 0,
                length = 10,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    /**
     *  creator identity length = *8*, api groupId length = *8*
     */
    @Test
    fun shouldThrowBadMessageExceptionWhenLengthEqualsIdentityAndGroupIdLength() {
        // arrange
        val testBlockLazy = {
            // act
            GroupFileMessage.fromByteArray(
                data = groupFileMessageBytes,
                offset = 0,
                length = 16,
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
            GroupFileMessage.fromByteArray(
                data = groupFileMessageBytes,
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
            GroupFileMessage.fromByteArray(
                data = groupFileMessageBytes,
                offset = 0,
                length = groupFileMessageBytes.size + 1,
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
            GroupFileMessage.fromByteArray(
                data = groupFileMessageBytes,
                offset = 1,
                length = groupFileMessageBytes.size,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultGroupFileMessage = GroupFileMessage.fromByteArray(
            data = groupFileMessageBytes,
            offset = 0,
            length = groupFileMessageBytes.size,
        )

        // assert
        assertEquals(
            bytesCreatorIdentity.toString(Charsets.UTF_8),
            resultGroupFileMessage.groupCreator,
        )
        assertContentEquals(bytesApiGroupId, resultGroupFileMessage.apiGroupId.groupId)
        assertFileDataEquals(fileData, resultGroupFileMessage.fileData)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupFileMessageBytes

        // act
        val resultGroupFileMessage = GroupFileMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = groupFileMessageBytes.size,
        )

        // assert
        assertEquals(
            bytesCreatorIdentity.toString(Charsets.UTF_8),
            resultGroupFileMessage.groupCreator,
        )
        assertContentEquals(bytesApiGroupId, resultGroupFileMessage.apiGroupId.groupId)
        assertFileDataEquals(fileData, resultGroupFileMessage.fileData)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupFileMessageBytes

        val testBlockLazy = {
            // act
            GroupFileMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = groupFileMessageBytes.size,
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
