package ch.threema.app.files

import ch.threema.common.files.FallbackFileHandle
import ch.threema.common.files.SimpleFileHandle
import ch.threema.localcrypto.MasterKeyProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageFileHandleProviderTest {
    private lateinit var userDataDirectoryMock: File
    private lateinit var legacyUserDataDirectoryMock: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var masterKeyProviderMock: MasterKeyProvider
    private lateinit var messageFileHandleProvider: MessageFileHandleProvider

    @BeforeTest
    fun setUp() {
        userDataDirectoryMock = createTempDirectory("data")
        legacyUserDataDirectoryMock = createTempDirectory("legacy-data")
        appDirectoryProviderMock = mockk {
            every { userFilesDirectory } returns userDataDirectoryMock
            every { legacyUserFilesDirectory } returns legacyUserDataDirectoryMock
        }
        masterKeyProviderMock = mockk()
        messageFileHandleProvider = MessageFileHandleProvider(
            appDirectoryProvider = appDirectoryProviderMock,
            masterKeyProvider = masterKeyProviderMock,
        )
    }

    @AfterTest
    fun tearDown() {
        userDataDirectoryMock.deleteRecursively()
        legacyUserDataDirectoryMock.deleteRecursively()
    }

    @Test
    fun `get message file`() {
        val fileHandle = messageFileHandleProvider.get(messageUid = "de66a430-7c7d-4015-9082-5f17c256519a")

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".message-files"),
                        name = ".de66a4307c7d401590825f17c256519a",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = legacyUserDataDirectoryMock,
                        name = ".de66a4307c7d401590825f17c256519a",
                    ),
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `get message thumbnail file`() {
        val fileHandle = messageFileHandleProvider.getThumbnail(messageUid = "de66a430-7c7d-4015-9082-5f17c256519a")

        assertEquals(
            EncryptedFileHandle(
                masterKeyProvider = masterKeyProviderMock,
                file = FallbackFileHandle(
                    primaryFile = SimpleFileHandle(
                        directory = File(userDataDirectoryMock, ".message-files"),
                        name = ".de66a4307c7d401590825f17c256519a_T",
                    ),
                    fallbackFile = SimpleFileHandle(
                        directory = legacyUserDataDirectoryMock,
                        name = ".de66a4307c7d401590825f17c256519a_T",
                    ),
                ),
            ),
            fileHandle,
        )
    }
}
