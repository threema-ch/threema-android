package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.json.JSONObject

class OnPremConfigStoreTest {

    private lateinit var baseDirectory: File

    @BeforeTest
    fun setUp() {
        baseDirectory = createTempDirectory("oppf_base_dir")
    }

    @AfterTest
    fun tearDown() {
        baseDirectory.deleteRecursively()
    }

    @Test
    fun `load from existing store file`() {
        val storeFile = File(baseDirectory, "onprem_config")
        storeFile.writeText("{}\n1760356256000")
        val onPremConfigMock = mockk<OnPremConfig>()
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(match { it.toString() == "{}" }, createdAt = any()) } returns onPremConfigMock
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify("{}") } returns JSONObject()
        }
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertEquals(onPremConfigMock, onPremConfig)
        verify { onPremConfigParserMock.parse(any(), createdAt = Instant.ofEpochMilli(1_760_356_256_000L)) }
    }

    // TODO(ANDR-4431): Remove this test case
    @Test
    fun `load from existing legacy store file when verifying fails`() {
        val storeFile = File(baseDirectory, "onprem_config")
        storeFile.writeText("{}\n1760356256000")
        val onPremConfigMock = mockk<OnPremConfig>()
        val onPremConfigParserMock = mockk<OnPremConfigParser> {
            every { parse(match { it.toString() == "{}" }, createdAt = any()) } returns onPremConfigMock
        }
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify("{}") } answers { throw ThreemaException("Failed to verify") }
        }
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = onPremConfigParserMock,
            onPremConfigVerifier = onPremConfigVerifierMock,
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertEquals(onPremConfigMock, onPremConfig)
        verify { onPremConfigParserMock.parse(any(), createdAt = Instant.ofEpochMilli(1_760_356_256_000L)) }
    }

    @Test
    fun `load from non-existing store file`() {
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            onPremConfigVerifier = mockk(),
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertNull(onPremConfig)
    }

    @Test
    fun `load from corrupted store file`() {
        val onPremConfigVerifierMock = mockk<OnPremConfigVerifier> {
            every { verify(any()) } answers { throw ThreemaException("Failed to verify") }
        }
        val storeFile = File(baseDirectory, "onprem_config")
        storeFile.writeText("{not a valid JSON}\nSOME_SIGNATURE\n1760356256000")
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            onPremConfigVerifier = onPremConfigVerifierMock,
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertNull(onPremConfig)
    }

    @Test
    fun `write store file`() {
        val timeProvider = TestTimeProvider(initialTimestamp = 1_760_356_256_000L)
        val oppfMockString = """{"hello":"world"}
            |SIGNATURE_GOES_HERE
        """.trimMargin()
        val storeFile = File(baseDirectory, "onprem_config")
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            onPremConfigVerifier = mockk(),
            timeProvider = timeProvider,
            baseDirectory = baseDirectory,
        )

        onPremConfigStore.store(oppfMockString)

        assertEquals("$oppfMockString\n1760356256000", storeFile.readText())
    }
}
