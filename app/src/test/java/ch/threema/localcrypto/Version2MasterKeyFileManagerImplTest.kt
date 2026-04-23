package ch.threema.localcrypto

import ch.threema.common.emptyByteArray
import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.AUTH_TOKEN
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET_HASH
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.cryptographicByteArrayOf
import ch.threema.testhelpers.loadResourceAsBytes
import com.google.protobuf.kotlin.toByteString
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Version2MasterKeyFileManagerImplTest {

    private val tempDirectory = createTempDirectory()
    private lateinit var keyFile: File
    private lateinit var unencryptedKeyFile: File
    private lateinit var keyStoreCryptoMock: KeyStoreCrypto
    private lateinit var masterKeyFileManager: Version2MasterKeyFileManager

    @BeforeTest
    fun setUp() {
        keyFile = File.createTempFile("key", ".dat")
        unencryptedKeyFile = File.createTempFile("unencrypted_key", ".dat")
        unencryptedKeyFile.delete()
        keyStoreCryptoMock = mockk {
            every { encryptWithNewSecretKey(any(), any()) } answers { firstArg() }
            every { decryptWithExistingSecretKey(any()) } answers { firstArg() }
            every { extractSecretKeyAlias(any()) } returns SecretKeyAlias.PRIMARY
            every { deleteSecretKey(SecretKeyAlias.PRIMARY) } just runs
        }
        masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )
    }

    @AfterTest
    fun tearDown() {
        keyFile.delete()
    }

    @Test
    fun `read unprotected key file`() {
        val bytes = loadResourceAsBytes("masterkey/v2/unprotected.dat")
        keyFile.writeBytes(bytes)

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
            storageData,
        )
        verify(exactly = 1) { keyStoreCryptoMock.decryptWithExistingSecretKey(match { it.contentEquals(bytes) }) }
    }

    @Test
    fun `read unprotected unencrypted key file and migrate it`() {
        keyFile.delete()
        unencryptedKeyFile.writeBytes(loadResourceAsBytes("masterkey/v2/unprotected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
            storageData,
        )
        assertTrue(keyFile.exists())
        assertFalse(unencryptedKeyFile.exists())
    }

    @Test
    fun `unencrypted key file is not deleted when migration fails`() {
        keyFile.delete()
        unencryptedKeyFile.createNewFile()

        masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = mockk {
                every { encodeMasterKeyStorageData(any()) } returns emptyByteArray().toByteString()
            },
            decoder = mockk {
                // Return different objects, to simulate that reading the unencrypted file and reading the new key file yield different results
                every { decodeOuterKeyStorage(any()) } returnsMany listOf(
                    // returned when reading the unencrypted key file to write the new key file
                    mockk("a"),
                    // returned when reading the (freshly migrated) key file
                    mockk("b"),
                )
            },
            keyStoreCrypto = keyStoreCryptoMock,
        )

        assertFailsWith<IllegalStateException> {
            masterKeyFileManager.readKeyFile()
        }

        assertTrue(unencryptedKeyFile.exists())
    }

    @Test
    fun `write unprotected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.Unprotected(
                        masterKeyData = MasterKeyData(MASTER_KEY),
                    ),
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/unprotected.dat"),
            keyFile.readBytes(),
        )
        verify(exactly = 1) { keyStoreCryptoMock.encryptWithNewSecretKey(any(), previousKeyAlias = SecretKeyAlias.PRIMARY) }
        verify(exactly = 1) {
            keyStoreCryptoMock.deleteSecretKey(SecretKeyAlias.PRIMARY)
        }
    }

    @Test
    fun `write and read unprotected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.Unprotected(
                    masterKeyData = MasterKeyData(MASTER_KEY),
                ),
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(writtenKey)
        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }

    @Test
    fun `read passphrase protected key file`() {
        keyFile.writeBytes(loadResourceAsBytes("masterkey/v2/passphrase-protected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = ByteArray(100) { it.toByte() }.toCryptographicByteArray(),
                    nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `write passphrase protected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = Argon2Version.VERSION_1_3,
                    encryptedData = ByteArray(100) { it.toByte() }.toCryptographicByteArray(),
                    nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                    memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                    salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                    iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                    parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/passphrase-protected.dat"),
            keyFile.readBytes(),
        )
    }

    @Test
    fun `write and read passphrase protected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = ByteArray(100) { it.toByte() }.toCryptographicByteArray(),
                nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(writtenKey)

        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }

    @Test
    fun `read remote secret protected key file`() {
        keyFile.writeBytes(loadResourceAsBytes("masterkey/v2/remote-secret-protected.dat"))

        val storageData = masterKeyFileManager.readKeyFile()

        assertEquals(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = RemoteSecretParameters(
                            authenticationToken = AUTH_TOKEN,
                            remoteSecretHash = REMOTE_SECRET_HASH,
                        ),
                        encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                    ),
                ),
            ),
            storageData,
        )
    }

    @Test
    fun `write remote secret protected key file`() {
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(
            MasterKeyStorageData.Version2(
                outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                    innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                        parameters = RemoteSecretParameters(
                            authenticationToken = AUTH_TOKEN,
                            remoteSecretHash = REMOTE_SECRET_HASH,
                        ),
                        encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                    ),
                ),
            ),
        )

        assertContentEquals(
            loadResourceAsBytes("masterkey/v2/remote-secret-protected.dat"),
            keyFile.readBytes(),
        )
    }

    @Test
    fun `write and read remote secret protected key file`() {
        val writtenKey = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                    parameters = RemoteSecretParameters(
                        authenticationToken = AUTH_TOKEN,
                        remoteSecretHash = REMOTE_SECRET_HASH,
                    ),
                    encryptedData = cryptographicByteArrayOf(1, 2, 3, 4),
                ),
            ),
        )
        val masterKeyFileManager = Version2MasterKeyFileManagerImpl(
            deletionDirectory = tempDirectory,
            keyFile = keyFile,
            unencryptedKeyFile = unencryptedKeyFile,
            encoder = Version2MasterKeyStorageEncoder(),
            decoder = Version2MasterKeyStorageDecoder(),
            keyStoreCrypto = keyStoreCryptoMock,
        )

        masterKeyFileManager.writeKeyFile(writtenKey)

        val readKey = masterKeyFileManager.readKeyFile()

        assertEquals(writtenKey, readKey)
    }
}
