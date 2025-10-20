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

package ch.threema.base.crypto

import ch.threema.base.crypto.NaCl.Companion.BOX_OVERHEAD_BYTES
import ch.threema.libthreema.ChunkedXSalsa20Poly1305Decryptor
import ch.threema.libthreema.ChunkedXSalsa20Poly1305Encryptor
import ch.threema.libthreema.CryptoException
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.willThrow
import ch.threema.testhelpers.willThrowExactly
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlin.experimental.inv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class NaClTest {

    private companion object {

        val alicePublicKey = byteArrayOf(
            0x85.toByte(), 0x20.toByte(), 0xf0.toByte(), 0x09.toByte(), 0x89.toByte(), 0x30.toByte(), 0xa7.toByte(), 0x54.toByte(),
            0x74.toByte(), 0x8b.toByte(), 0x7d.toByte(), 0xdc.toByte(), 0xb4.toByte(), 0x3e.toByte(), 0xf7.toByte(), 0x5a.toByte(),
            0x0d.toByte(), 0xbf.toByte(), 0x3a.toByte(), 0x0d.toByte(), 0x26.toByte(), 0x38.toByte(), 0x1a.toByte(), 0xf4.toByte(),
            0xeb.toByte(), 0xa4.toByte(), 0xa9.toByte(), 0x8e.toByte(), 0xaa.toByte(), 0x9b.toByte(), 0x4e.toByte(), 0x6a.toByte(),
        )
        val alicePrivateKey = byteArrayOf(
            0x77.toByte(), 0x07.toByte(), 0x6d.toByte(), 0x0a.toByte(), 0x73.toByte(), 0x18.toByte(), 0xa5.toByte(), 0x7d.toByte(),
            0x3c.toByte(), 0x16.toByte(), 0xc1.toByte(), 0x72.toByte(), 0x51.toByte(), 0xb2.toByte(), 0x66.toByte(), 0x45.toByte(),
            0xdf.toByte(), 0x4c.toByte(), 0x2f.toByte(), 0x87.toByte(), 0xeb.toByte(), 0xc0.toByte(), 0x99.toByte(), 0x2a.toByte(),
            0xb1.toByte(), 0x77.toByte(), 0xfb.toByte(), 0xa5.toByte(), 0x1d.toByte(), 0xb9.toByte(), 0x2c.toByte(), 0x2a.toByte(),
        )
        val bobPublicKey = byteArrayOf(
            0xde.toByte(), 0x9e.toByte(), 0xdb.toByte(), 0x7d.toByte(), 0x7b.toByte(), 0x7d.toByte(), 0xc1.toByte(), 0xb4.toByte(),
            0xd3.toByte(), 0x5b.toByte(), 0x61.toByte(), 0xc2.toByte(), 0xec.toByte(), 0xe4.toByte(), 0x35.toByte(), 0x37.toByte(),
            0x3f.toByte(), 0x83.toByte(), 0x43.toByte(), 0xc8.toByte(), 0x5b.toByte(), 0x78.toByte(), 0x67.toByte(), 0x4d.toByte(),
            0xad.toByte(), 0xfc.toByte(), 0x7e.toByte(), 0x14.toByte(), 0x6f.toByte(), 0x88.toByte(), 0x2b.toByte(), 0x4f.toByte(),
        )
        val bobPrivateKey = byteArrayOf(
            0x5d.toByte(), 0xab.toByte(), 0x08.toByte(), 0x7e.toByte(), 0x62.toByte(), 0x4a.toByte(), 0x8a.toByte(), 0x4b.toByte(),
            0x79.toByte(), 0xe1.toByte(), 0x7f.toByte(), 0x8b.toByte(), 0x83.toByte(), 0x80.toByte(), 0x0e.toByte(), 0xe6.toByte(),
            0x6f.toByte(), 0x3b.toByte(), 0xb1.toByte(), 0x29.toByte(), 0x26.toByte(), 0x18.toByte(), 0xb6.toByte(), 0xfd.toByte(),
            0x1c.toByte(), 0x2f.toByte(), 0x8b.toByte(), 0x27.toByte(), 0xff.toByte(), 0x88.toByte(), 0xe0.toByte(), 0xeb.toByte(),
        )
        val symmetricKey = byteArrayOf(
            -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52,
            -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52, -52,
        )
        val nonce = byteArrayOf(
            -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35, -35,
            -35, -35, -35, -35, -35, -35, -35, -35,
        )

        val naclAlice = NaCl(
            privateKey = alicePrivateKey,
            publicKey = bobPublicKey,
        )

        val naclBob = NaCl(
            privateKey = bobPrivateKey,
            publicKey = alicePublicKey,
        )
    }

    @Test
    fun `encryption and decryption works`() {
        // test vectors from tests/box.* in nacl distribution
        val nonce = byteArrayOf(
            0x69.toByte(), 0x69.toByte(), 0x6e.toByte(), 0xe9.toByte(), 0x55.toByte(), 0xb6.toByte(), 0x2b.toByte(), 0x73.toByte(),
            0xcd.toByte(), 0x62.toByte(), 0xbd.toByte(), 0xa8.toByte(), 0x75.toByte(), 0xfc.toByte(), 0x73.toByte(), 0xd6.toByte(),
            0x82.toByte(), 0x19.toByte(), 0xe0.toByte(), 0x03.toByte(), 0x6b.toByte(), 0x7a.toByte(), 0x0b.toByte(), 0x37.toByte(),
        )
        val messagePlain = byteArrayOf(
            0xbe.toByte(), 0x07.toByte(), 0x5f.toByte(), 0xc5.toByte(), 0x3c.toByte(), 0x81.toByte(), 0xf2.toByte(), 0xd5.toByte(),
            0xcf.toByte(), 0x14.toByte(), 0x13.toByte(), 0x16.toByte(), 0xeb.toByte(), 0xeb.toByte(), 0x0c.toByte(), 0x7b.toByte(),
            0x52.toByte(), 0x28.toByte(), 0xc5.toByte(), 0x2a.toByte(), 0x4c.toByte(), 0x62.toByte(), 0xcb.toByte(), 0xd4.toByte(),
            0x4b.toByte(), 0x66.toByte(), 0x84.toByte(), 0x9b.toByte(), 0x64.toByte(), 0x24.toByte(), 0x4f.toByte(), 0xfc.toByte(),
            0xe5.toByte(), 0xec.toByte(), 0xba.toByte(), 0xaf.toByte(), 0x33.toByte(), 0xbd.toByte(), 0x75.toByte(), 0x1a.toByte(),
            0x1a.toByte(), 0xc7.toByte(), 0x28.toByte(), 0xd4.toByte(), 0x5e.toByte(), 0x6c.toByte(), 0x61.toByte(), 0x29.toByte(),
            0x6c.toByte(), 0xdc.toByte(), 0x3c.toByte(), 0x01.toByte(), 0x23.toByte(), 0x35.toByte(), 0x61.toByte(), 0xf4.toByte(),
            0x1d.toByte(), 0xb6.toByte(), 0x6c.toByte(), 0xce.toByte(), 0x31.toByte(), 0x4a.toByte(), 0xdb.toByte(), 0x31.toByte(),
            0x0e.toByte(), 0x3b.toByte(), 0xe8.toByte(), 0x25.toByte(), 0x0c.toByte(), 0x46.toByte(), 0xf0.toByte(), 0x6d.toByte(),
            0xce.toByte(), 0xea.toByte(), 0x3a.toByte(), 0x7f.toByte(), 0xa1.toByte(), 0x34.toByte(), 0x80.toByte(), 0x57.toByte(),
            0xe2.toByte(), 0xf6.toByte(), 0x55.toByte(), 0x6a.toByte(), 0xd6.toByte(), 0xb1.toByte(), 0x31.toByte(), 0x8a.toByte(),
            0x02.toByte(), 0x4a.toByte(), 0x83.toByte(), 0x8f.toByte(), 0x21.toByte(), 0xaf.toByte(), 0x1f.toByte(), 0xde.toByte(),
            0x04.toByte(), 0x89.toByte(), 0x77.toByte(), 0xeb.toByte(), 0x48.toByte(), 0xf5.toByte(), 0x9f.toByte(), 0xfd.toByte(),
            0x49.toByte(), 0x24.toByte(), 0xca.toByte(), 0x1c.toByte(), 0x60.toByte(), 0x90.toByte(), 0x2e.toByte(), 0x52.toByte(),
            0xf0.toByte(), 0xa0.toByte(), 0x89.toByte(), 0xbc.toByte(), 0x76.toByte(), 0x89.toByte(), 0x70.toByte(), 0x40.toByte(),
            0xe0.toByte(), 0x82.toByte(), 0xf9.toByte(), 0x37.toByte(), 0x76.toByte(), 0x38.toByte(), 0x48.toByte(), 0x64.toByte(),
            0x5e.toByte(), 0x07.toByte(), 0x05.toByte(),
        )
        val messageEncryptedExpected = byteArrayOf(
            0xf3.toByte(), 0xff.toByte(), 0xc7.toByte(), 0x70.toByte(), 0x3f.toByte(), 0x94.toByte(), 0x00.toByte(), 0xe5.toByte(),
            0x2a.toByte(), 0x7d.toByte(), 0xfb.toByte(), 0x4b.toByte(), 0x3d.toByte(), 0x33.toByte(), 0x05.toByte(), 0xd9.toByte(),
            0x8e.toByte(), 0x99.toByte(), 0x3b.toByte(), 0x9f.toByte(), 0x48.toByte(), 0x68.toByte(), 0x12.toByte(), 0x73.toByte(),
            0xc2.toByte(), 0x96.toByte(), 0x50.toByte(), 0xba.toByte(), 0x32.toByte(), 0xfc.toByte(), 0x76.toByte(), 0xce.toByte(),
            0x48.toByte(), 0x33.toByte(), 0x2e.toByte(), 0xa7.toByte(), 0x16.toByte(), 0x4d.toByte(), 0x96.toByte(), 0xa4.toByte(),
            0x47.toByte(), 0x6f.toByte(), 0xb8.toByte(), 0xc5.toByte(), 0x31.toByte(), 0xa1.toByte(), 0x18.toByte(), 0x6a.toByte(),
            0xc0.toByte(), 0xdf.toByte(), 0xc1.toByte(), 0x7c.toByte(), 0x98.toByte(), 0xdc.toByte(), 0xe8.toByte(), 0x7b.toByte(),
            0x4d.toByte(), 0xa7.toByte(), 0xf0.toByte(), 0x11.toByte(), 0xec.toByte(), 0x48.toByte(), 0xc9.toByte(), 0x72.toByte(),
            0x71.toByte(), 0xd2.toByte(), 0xc2.toByte(), 0x0f.toByte(), 0x9b.toByte(), 0x92.toByte(), 0x8f.toByte(), 0xe2.toByte(),
            0x27.toByte(), 0x0d.toByte(), 0x6f.toByte(), 0xb8.toByte(), 0x63.toByte(), 0xd5.toByte(), 0x17.toByte(), 0x38.toByte(),
            0xb4.toByte(), 0x8e.toByte(), 0xee.toByte(), 0xe3.toByte(), 0x14.toByte(), 0xa7.toByte(), 0xcc.toByte(), 0x8a.toByte(),
            0xb9.toByte(), 0x32.toByte(), 0x16.toByte(), 0x45.toByte(), 0x48.toByte(), 0xe5.toByte(), 0x26.toByte(), 0xae.toByte(),
            0x90.toByte(), 0x22.toByte(), 0x43.toByte(), 0x68.toByte(), 0x51.toByte(), 0x7a.toByte(), 0xcf.toByte(), 0xea.toByte(),
            0xbd.toByte(), 0x6b.toByte(), 0xb3.toByte(), 0x73.toByte(), 0x2b.toByte(), 0xc0.toByte(), 0xe9.toByte(), 0xda.toByte(),
            0x99.toByte(), 0x83.toByte(), 0x2b.toByte(), 0x61.toByte(), 0xca.toByte(), 0x01.toByte(), 0xb6.toByte(), 0xde.toByte(),
            0x56.toByte(), 0x24.toByte(), 0x4a.toByte(), 0x9e.toByte(), 0x88.toByte(), 0xd5.toByte(), 0xf9.toByte(), 0xb3.toByte(),
            0x79.toByte(), 0x73.toByte(), 0xf6.toByte(), 0x22.toByte(), 0xa4.toByte(), 0x3d.toByte(), 0x14.toByte(), 0xa6.toByte(),
            0x59.toByte(), 0x9b.toByte(), 0x1f.toByte(), 0x65.toByte(), 0x4c.toByte(), 0xb4.toByte(), 0x5a.toByte(), 0x74.toByte(),
            0xe3.toByte(), 0x55.toByte(), 0xa5.toByte(),
        )

        // encrypt data and compare with expected result
        val messageEncrypted = naclAlice.encrypt(
            data = messagePlain,
            nonce = nonce,
        )
        assertContentEquals(
            expected = messageEncryptedExpected,
            actual = messageEncrypted,
        )

        // decrypt data and compare with plaintext
        val messageDecrypted = naclBob.decrypt(
            data = messageEncrypted,
            nonce = nonce,
        )
        assertContentEquals(
            expected = messagePlain,
            actual = messageDecrypted,
        )
    }

    @Test
    fun `can encrypt and decrypt empty array`() {
        // arrange
        val inputBytes = ByteArray(0)

        // act
        val encryptedBytes = naclAlice.encrypt(
            data = inputBytes,
            nonce = nonce,
        )
        val decryptedBytes = naclBob.decrypt(
            data = encryptedBytes,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            expected = inputBytes,
            actual = decryptedBytes,
        )
    }

    @Test
    fun `can symmetric encrypt and decrypt empty array`() {
        // arrange
        val inputBytes = ByteArray(0)

        // act
        val encryptedBytes = NaCl.symmetricEncryptData(
            data = inputBytes,
            key = symmetricKey,
            nonce = nonce,
        )

        val decryptedBytes = NaCl.symmetricDecryptData(
            data = encryptedBytes,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            expected = inputBytes,
            actual = decryptedBytes,
        )
    }

    @Test
    fun `encrypt throws exception for invalid nonce`() {
        // arrange
        val inputBytes = "hi".toByteArray()
        val invalidNonces: List<ByteArray> = listOf(
            nonSecureRandomArray(NaCl.NONCE_BYTES - 1),
            nonSecureRandomArray(NaCl.NONCE_BYTES + 1),
            ByteArray(0),
        )

        // act
        val testCases: List<() -> ByteArray> = invalidNonces.map { invalidNonce ->
            {
                naclAlice.encrypt(
                    data = inputBytes,
                    nonce = invalidNonce,
                )
            }
        }

        // assert
        testCases.forEach { testCase ->
            testCase willThrow CryptoException::class
        }
    }

    @Test
    fun `decryption throws exception when nonce differs`() {
        // arrange
        val inputBytes = "hi".toByteArray()
        val differentNonce = nonSecureRandomArray(NaCl.NONCE_BYTES)
        val encryptedBytes = naclAlice.encrypt(
            data = inputBytes,
            nonce = nonce,
        )

        // act
        val testCase = {
            naclBob.decrypt(
                data = encryptedBytes,
                nonce = differentNonce,
            )
        }

        // assert
        testCase willThrow CryptoException::class
    }

    @Test
    fun `asymmetric encryption and decryption should never mutate input bytes`() {
        val originalInputBytes = "hi".toByteArray()

        // validate encryption does not mutate array contents
        val inputBytes = originalInputBytes.copyOf()
        val encryptedBytes = naclAlice.encrypt(
            data = inputBytes,
            nonce = nonce,
        )
        assertContentEquals(
            expected = originalInputBytes,
            actual = inputBytes,
        )

        // validate decryption does not mutate array contents
        val originalEncryptedBytes = encryptedBytes.copyOf()
        naclBob.decrypt(
            data = encryptedBytes,
            nonce = nonce,
        )
        assertContentEquals(
            expected = originalEncryptedBytes,
            actual = encryptedBytes,
        )
    }

    @Test
    fun `derive public-key works`() {
        // act
        val derivedPublicKeyBytes = NaCl.derivePublicKey(alicePrivateKey)

        // assert
        assertContentEquals(
            expected = alicePublicKey,
            actual = derivedPublicKeyBytes,
        )
    }

    @Test
    fun `derive public-key throws exception for invalid private-key`() {
        // arrange
        val invalidPrivateKeys: List<ByteArray> = listOf(
            alicePrivateKey.copyOf(newSize = alicePrivateKey.size - 1),
            alicePrivateKey.copyOf(newSize = alicePrivateKey.size + 1),
            ByteArray(0),
        )

        // act
        val testCases: List<() -> ByteArray> = invalidPrivateKeys.map { shortPrivateKey ->
            { NaCl.derivePublicKey(shortPrivateKey) }
        }

        // assert
        testCases.forEach { testCase ->
            testCase willThrow CryptoException::class
        }
    }

    @Test
    fun `symmetric encryption and decryption works`() {
        // arrange
        val inputBytes = "hi".toByteArray()

        // act
        val encryptedBytes = NaCl.symmetricEncryptData(
            data = inputBytes,
            key = symmetricKey,
            nonce = nonce,
        )
        val decryptedBytes = NaCl.symmetricDecryptData(
            data = encryptedBytes,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            expected = inputBytes,
            actual = decryptedBytes,
        )
    }

    @Test
    fun `symmetric encrypt throws exception for invalid nonce`() {
        // arrange
        val inputBytes = "hi".toByteArray()
        val invalidNonces: List<ByteArray> = listOf(
            nonSecureRandomArray(NaCl.NONCE_BYTES - 1),
            nonSecureRandomArray(NaCl.NONCE_BYTES + 1),
            ByteArray(0),
        )

        // act
        val testCases: List<() -> ByteArray> = invalidNonces.map { invalidNonce ->
            {
                NaCl.symmetricEncryptData(
                    data = inputBytes,
                    key = symmetricKey,
                    nonce = invalidNonce,
                )
            }
        }

        // assert
        testCases.forEach { testCase ->
            testCase willThrow Exception::class
        }
    }

    @Test
    fun `symmetric decryption throws exception when nonce differs`() {
        // arrange
        val inputBytes = "hi".toByteArray()
        val differentNonce = nonSecureRandomArray(NaCl.NONCE_BYTES)
        val encryptedBytes = NaCl.symmetricEncryptData(
            data = inputBytes,
            key = symmetricKey,
            nonce = nonce,
        )

        // act
        val testCase = {
            NaCl.symmetricDecryptData(
                data = encryptedBytes,
                key = symmetricKey,
                nonce = differentNonce,
            )
        }

        // assert
        testCase willThrow CryptoException::class
    }

    @Test
    fun `symmetric encryption and decryption should never mutate input bytes`() {
        val originalInputBytes = "hi".toByteArray()

        // validate encryption does not mutate array contents
        val inputBytes = originalInputBytes.copyOf()
        val encryptedBytes = NaCl.symmetricEncryptData(
            data = inputBytes,
            key = symmetricKey,
            nonce = nonce,
        )
        assertContentEquals(
            expected = originalInputBytes,
            actual = inputBytes,
        )

        // validate decryption does not mutate array contents
        val originalEncryptedBytes = encryptedBytes.copyOf()
        NaCl.symmetricDecryptData(
            data = encryptedBytes,
            key = symmetricKey,
            nonce = nonce,
        )
        assertContentEquals(
            expected = originalEncryptedBytes,
            actual = encryptedBytes,
        )
    }

    @Test
    fun `symmetricEncryptDataInPlace works with smallest chunk size`() {
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 2)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with small chunk size`() {
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 8)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with big chunk size`() {
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 128)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with chunk size same as content size`() {
        // content bytes = 64 - tag bytes (16)
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 48)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with chunk size same as data size`() {
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 64)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with default chunk size 1 MiB`() {
        testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize = 1024 * 1024)
    }

    /**
     *  Defines a static input data of 16 zero bytes (tag) followed by 48 randomly chosen content bytes.
     *
     *  Encrypts these bytes with the given [chunkSize] and compares the encrypted result bytes to the bytes
     *  created using the old Java NaCl implementation for this exact input.
     */
    private fun testSymmetricEncryptDataInPlaceData64ChunkSize(chunkSize: Int) {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            37, -84, 112, -7, 0, 63, -122, 88, -45, 26, -110, 119, 73, -31, 5, -67,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
            chunkSize = chunkSize,
        )

        // assert
        assertContentEquals(
            // expected bytes were created using the old Java implementation of NaCl
            byteArrayOf(
                69, -126, -19, -57, 99, 19, -80, 47, -18, 126, 13, -106, 10, 94, -112, 0,
                32, -27, 108, -104, -52, -25, 69, -109, 90, 55, -94, 29, -53, -59, -20, 20,
                108, 32, -82, 125, 10, -13, -93, 127, -109, -34, -46, 59, -123, 67, -68, -22,
                26, 89, -34, -84, -25, 127, -58, -108, -53, 126, -85, 58, 51, 7, -42, -54,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricEncryptDataInPlace does not work with empty content`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES)
        val dataMutable = dataOriginal.copyOf()

        // act
        val testBlockLazy = {
            NaCl.symmetricEncryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrow IllegalArgumentException::class
        assertContentEquals(dataOriginal, dataMutable)
    }

    @Test
    fun `symmetricEncryptDataInPlace works with one byte content`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(33)
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            byteArrayOf(-13, -106, 67, -128, 119, -92, -52, -26, 75, 103, -33, 82, -63, -85, -59, -89, -100),
            dataMutable,
        )
    }

    @Test
    fun `symmetricEncryptDataInPlace works with partial last chunk`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            12,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
            chunkSize = 16,
        )

        // assert
        // expected bytes were created using the old Java implementation of NaCl
        assertContentEquals(
            byteArrayOf(
                -27, 89, -4, 68, -12, -32, -28, 77, 32, 82, 45, 91, -55, -8, 75, 49,
                32, -27, 108, -104, -52, -25, 69, -109, 90, 55, -94, 29, -53, -59, -20, 20,
                108, 32, -82, 125, 10, -13, -93, 127, -109, -34, -46, 59, -123, 67, -68, -22,
                51,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricEncryptDataInPlace works with uneven content byte count`() {
        // arrange
        // input bytes of length 49
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            12,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            // expected bytes were created using the old Java implementation of NaCl
            byteArrayOf(
                -27, 89, -4, 68, -12, -32, -28, 77, 32, 82, 45, 91, -55, -8, 75, 49,
                32, -27, 108, -104, -52, -25, 69, -109, 90, 55, -94, 29, -53, -59, -20, 20,
                108, 32, -82, 125, 10, -13, -93, 127, -109, -34, -46, 59, -123, 67, -68, -22,
                51,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace works with smallest chunk size`() {
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 2)
    }

    @Test
    fun `symmetricDecryptDataInPlace works with small chunk size`() {
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 8)
    }

    @Test
    fun `symmetricDecryptDataInPlace works with big chunk size`() {
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 128)
    }

    @Test
    fun `symmetricDecryptDataInPlace works with chunk size same as content size`() {
        // content bytes = 64 - tag bytes (16)
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 48)
    }

    @Test
    fun `symmetricDecryptDataInPlace works with chunk size same as data size`() {
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 64)
    }

    @Test
    fun `symmetricDecryptDataInPlace works with default chunk size 1 MiB`() {
        testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize = 1024 * 1024)
    }

    /**
     *  Defines a static input data of 16 zero bytes (tag) followed by 48 randomly chosen content bytes.
     *
     *  Encrypts these bytes with the default chunk size and decrypts them with the given [chunkSize].
     */
    private fun testSymmetricDecryptDataInPlaceData64ChunkSize(chunkSize: Int) {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            37, -84, 112, -7, 0, 63, -122, 88, -45, 26, -110, 119, 73, -31, 5, -67,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )
        NaCl.symmetricDecryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
            chunkSize = chunkSize,
        )

        // assert
        assertContentEquals(
            byteArrayOf(
                -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
                47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
                37, -84, 112, -7, 0, 63, -122, 88, -45, 26, -110, 119, 73, -31, 5, -67,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace works with one byte content`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(33)
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )
        NaCl.symmetricDecryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            byteArrayOf(33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            dataMutable,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace works with partial last chunk`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            103, 126, 33,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )
        NaCl.symmetricDecryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
            chunkSize = 16,
        )

        // assert
        assertContentEquals(
            byteArrayOf(
                -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
                47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
                103, 126, 33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace works with uneven content byte count`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
            103, 126, 33,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )
        NaCl.symmetricDecryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        assertContentEquals(
            byteArrayOf(
                -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
                47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
                103, 126, 33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0,
            ),
            dataMutable,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace does not work with empty content`() {
        // arrange
        val dataOriginal = Random.nextBytes(BOX_OVERHEAD_BYTES)
        val dataMutable = dataOriginal.copyOf()

        // act
        val testBlockLazy = {
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrow IllegalArgumentException::class
        assertContentEquals(dataOriginal, dataMutable)
    }

    @Test
    fun `symmetricDecryptDataInPlace removes the tag`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )
        NaCl.symmetricDecryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonce,
        )

        // assert
        val tagAfterDecryption = dataMutable.copyOfRange(
            fromIndex = dataMutable.size - BOX_OVERHEAD_BYTES,
            toIndex = dataMutable.size,
        )
        assertContentEquals(
            ByteArray(BOX_OVERHEAD_BYTES),
            tagAfterDecryption,
        )
    }

    @Test
    fun `symmetricDecryptDataInPlace fails for wrong symmetric key`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()
        val symmetricKeyEncrypt = symmetricKey.copyOf()
        val symmetricKeyDecrypt = symmetricKey.copyOf().also { key ->
            key[0] = key[0].inv()
        }

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKeyEncrypt,
            nonce = nonce,
        )
        val testBlockLazy = {
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKeyDecrypt,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrow CryptoException::class
    }

    @Test
    fun `symmetricDecryptDataInPlace fails for wrong nonce`() {
        // arrange
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()
        val nonceEncrypt = nonce.copyOf()
        val nonceDecrypt = nonce.copyOf().also {
            it[0] = it[0].inv()
        }

        // act
        NaCl.symmetricEncryptDataInPlace(
            data = dataMutable,
            key = symmetricKey,
            nonce = nonceEncrypt,
        )
        val testBlockLazy = {
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonceDecrypt,
            )
        }

        // assert
        testBlockLazy willThrow CryptoException::class
    }

    @Test
    fun `symmetricEncryptDataInPlace throws exception on internal libthreema encrypt failure`() {
        // arrange
        val mockedException = CryptoException.CipherFailed("Encryption failed internally")
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()

        // Mock that the encrypt call of the first chunk of bytes fails internally
        mockkConstructor(ChunkedXSalsa20Poly1305Encryptor::class)
        every {
            constructedWith<ChunkedXSalsa20Poly1305Encryptor>(
                EqMatcher(symmetricKey),
                EqMatcher(nonce),
            ).encrypt(any())
        } throws mockedException

        // act
        val testBlockLazy = {
            NaCl.symmetricEncryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrowExactly mockedException

        // cleanup
        unmockkConstructor(ChunkedXSalsa20Poly1305Encryptor::class)
    }

    @Test
    fun `symmetricDecryptDataInPlace throws exception on internal libthreema decrypt failure`() {
        // arrange
        val mockedException = CryptoException.CipherFailed("Decryption failed internally")
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()

        // Mock that the decrypt call of the first chunk of bytes fails internally
        mockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
        every {
            constructedWith<ChunkedXSalsa20Poly1305Decryptor>(
                EqMatcher(symmetricKey),
                EqMatcher(nonce),
            ).decrypt(any())
        } throws mockedException

        // act
        val testBlockLazy = {
            NaCl.symmetricEncryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrowExactly mockedException

        // cleanup
        unmockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
    }

    @Test
    fun `symmetricDecryptDataInPlace throws exception on internal libthreema tag verification failure`() {
        // arrange
        val mockedException = CryptoException.CipherFailed("Tag verification failed internally")
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()

        // Mock that the call to verify the tag fails internally
        mockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
        every {
            constructedWith<ChunkedXSalsa20Poly1305Decryptor>(
                EqMatcher(symmetricKey),
                EqMatcher(nonce),
            ).finalizeVerify(any())
        } throws mockedException

        // act
        val testBlockLazy = {
            NaCl.symmetricEncryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrowExactly mockedException

        // cleanup
        unmockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
    }

    @Test
    fun `symmetricDecryptDataInPlace throws exception if tag verification throws InvalidParameter`() {
        // arrange
        val mockedException = CryptoException.InvalidParameter("Tag verification failed")
        val dataOriginal = ByteArray(BOX_OVERHEAD_BYTES) + byteArrayOf(
            -99, 40, -125, 100, 38, -40, 19, 5, -47, 49, -12, -120, 32, 111, -115, 43,
            47, -82, 126, -15, 63, -99, 0, 88, -44, 12, 73, -128, 5, -67, 104, 29,
        )
        val dataMutable = dataOriginal.copyOf()

        // Mock that the call to verify the tag fails internally
        mockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
        every {
            constructedWith<ChunkedXSalsa20Poly1305Decryptor>(
                EqMatcher(symmetricKey),
                EqMatcher(nonce),
            ).finalizeVerify(any())
        } throws mockedException

        // act
        val testBlockLazy = {
            NaCl.symmetricEncryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
            NaCl.symmetricDecryptDataInPlace(
                data = dataMutable,
                key = symmetricKey,
                nonce = nonce,
            )
        }

        // assert
        testBlockLazy willThrowExactly mockedException

        // cleanup
        unmockkConstructor(ChunkedXSalsa20Poly1305Decryptor::class)
    }
}
