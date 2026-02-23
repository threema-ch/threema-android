package ch.threema.app.files

import ch.threema.common.files.FallbackFileHandle
import ch.threema.common.files.SimpleFileHandle
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLogoFileHandleProviderTest {

    private lateinit var appDataDirectoryMock: File
    private lateinit var legacyUserDataDirectoryMock: File
    private lateinit var appDirectoryProviderMock: AppDirectoryProvider
    private lateinit var appLogoFileHandleProvider: AppLogoFileHandleProvider

    @BeforeTest
    fun setUp() {
        appDataDirectoryMock = createTempDirectory("app-data")
        legacyUserDataDirectoryMock = createTempDirectory("legacy-data")
        appDirectoryProviderMock = mockk {
            every { appDataDirectory } returns appDataDirectoryMock
            every { legacyUserFilesDirectory } returns legacyUserDataDirectoryMock
        }
        appLogoFileHandleProvider = AppLogoFileHandleProvider(
            appDirectoryProvider = appDirectoryProviderMock,
        )
    }

    @AfterTest
    fun tearDown() {
        appDataDirectoryMock.deleteRecursively()
        legacyUserDataDirectoryMock.deleteRecursively()
    }

    @Test
    fun `get light theme app logo`() {
        val fileHandle = appLogoFileHandleProvider.get(AppLogoFileHandleProvider.Theme.LIGHT)

        assertEquals(
            FallbackFileHandle(
                primaryFile = SimpleFileHandle(
                    directory = appDataDirectoryMock,
                    name = "app_logo_light.png",
                ),
                fallbackFile = SimpleFileHandle(
                    directory = legacyUserDataDirectoryMock,
                    name = "appicon_light.png",
                ),
            ),
            fileHandle,
        )
    }

    @Test
    fun `get dark theme app logo`() {
        val fileHandle = appLogoFileHandleProvider.get(AppLogoFileHandleProvider.Theme.DARK)

        assertEquals(
            FallbackFileHandle(
                primaryFile = SimpleFileHandle(
                    directory = appDataDirectoryMock,
                    name = "app_logo_dark.png",
                ),
                fallbackFile = SimpleFileHandle(
                    directory = legacyUserDataDirectoryMock,
                    name = "appicon_dark.png",
                ),
            ),
            fileHandle,
        )
    }
}
