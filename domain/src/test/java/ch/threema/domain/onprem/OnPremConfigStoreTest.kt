package ch.threema.domain.onprem

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
import kotlin.test.assertFalse
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
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = onPremConfigParserMock,
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
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertNull(onPremConfig)
    }

    @Test
    fun `load from corrupted store file`() {
        val storeFile = File(baseDirectory, "onprem_config")
        storeFile.writeText("{not a valid JSON}\n1760356256000")
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        val onPremConfig = onPremConfigStore.get()

        assertNull(onPremConfig)
    }

    @Test
    fun `write store file`() {
        val timeProvider = TestTimeProvider(initialTimestamp = 1_760_356_256_000L)
        val onPremConfigMockString = """{"hello":"world"}
            |1760356256000
        """.trimMargin()
        val storeFile = File(baseDirectory, "onprem_config")
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            timeProvider = timeProvider,
            baseDirectory = baseDirectory,
        )

        onPremConfigStore.store(JSONObject(onPremConfigMockString))

        assertEquals(onPremConfigMockString, storeFile.readText())
    }

    @Test
    fun `reset deletes the store file`() {
        val storeFile = File(baseDirectory, "onprem_config")
        storeFile.writeText("{}\n1234")
        val onPremConfigStore = OnPremConfigStore(
            onPremConfigParser = mockk(),
            timeProvider = mockk(),
            baseDirectory = baseDirectory,
        )

        onPremConfigStore.reset()

        assertFalse(storeFile.exists())
        assertNull(onPremConfigStore.get())
    }
}
