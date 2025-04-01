/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.utils

import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

private val logger = LoggingUtil.getThreemaLogger("FileHandlingZipOutputStream")

class FileHandlingZipOutputStream(
    outputStream: OutputStream,
    password: CharArray?,
    charset: Charset?
) :
    ZipOutputStream(outputStream, password, charset) {

    constructor(outputStream: OutputStream) : this(outputStream, null, null)

    constructor(outputStream: OutputStream, password: CharArray) : this(
        outputStream,
        password,
        null
    )

    companion object {
        /**
         * Get a [FileHandlingZipOutputStream] that writes to a provided [OutputStream].
         * Note that the outputStream will be wrapped by a [BufferedOutputStream].
         * @param outputStream The [OutputStream] data should be written to
         * @param password Desired password or null if no encryption is desired
         * @throws IOException If the stream could not be created
         */
        @Throws(IOException::class)
        @JvmStatic
        fun initializeZipOutputStream(
            outputStream: OutputStream,
            password: String?
        ): FileHandlingZipOutputStream {
            val bufferedOutputStream = BufferedOutputStream(outputStream)
            return if (password != null) {
                FileHandlingZipOutputStream(bufferedOutputStream, password.toCharArray())
            } else {
                FileHandlingZipOutputStream(bufferedOutputStream)
            }
        }

        @Throws(IOException::class)
        @JvmStatic
        fun initializeZipOutputStream(
            zipFile: File,
            password: String?
        ): FileHandlingZipOutputStream {
            val fileOutputStream = FileOutputStream(zipFile)
            val bufferedOutputStream = BufferedOutputStream(fileOutputStream)
            return initializeZipOutputStream(bufferedOutputStream, password)
        }
    }

    /**
     * Write the contents of [inputStream] to this [FileHandlingZipOutputStream] and close [inputStream]
     * afterwards.
     * @param inputStream
     * @param filenameInZip
     * @param compress whether to compress the data (don't use for already compressed data like images)
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addFileFromInputStream(
        inputStream: InputStream?,
        filenameInZip: String,
        compress: Boolean
    ) {
        if (inputStream == null) {
            return
        }

        inputStream.use { dataInputStream ->
            putNextEntry(createZipParameter(filenameInZip, compress))
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (dataInputStream.read(buffer).also { bytesRead = it } > 0) {
                write(buffer, 0, bytesRead)
            }
            closeEntry()
        }
    }

    @Throws(ThreemaException::class)
    fun addFile(
        filenameInZip: String,
        compress: Boolean,
        consumer: ThrowingConsumer<OutputStream>
    ) {
        putNextEntry(createZipParameter(filenameInZip, compress))
        try {
            consumer.accept(object : OutputStream() {
                @Throws(IOException::class)
                override fun close() {
                    logger.debug("Ignore closing of output stream")
                }

                @Throws(IOException::class)
                override fun flush() {
                    this@FileHandlingZipOutputStream.flush()
                }

                @Throws(IOException::class)
                override fun write(b: Int) {
                    this@FileHandlingZipOutputStream.write(b)
                }

                @Throws(IOException::class)
                override fun write(b: ByteArray?) {
                    this@FileHandlingZipOutputStream.write(b)
                }

                @Throws(IOException::class)
                override fun write(b: ByteArray?, off: Int, len: Int) {
                    this@FileHandlingZipOutputStream.write(b, off, len)
                }
            })
        } catch (e: Exception) {
            throw ThreemaException("Exception while adding file", e)
        }
        closeEntry()
    }

    private fun createZipParameter(filenameInZip: String, compress: Boolean): ZipParameters {
        return ZipParameters().apply {
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = if (compress) {
                CompressionLevel.NORMAL
            } else {
                CompressionLevel.NO_COMPRESSION
            }
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            fileNameInZip = filenameInZip
        }
    }
}
