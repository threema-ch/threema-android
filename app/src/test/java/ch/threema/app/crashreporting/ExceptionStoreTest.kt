package ch.threema.app.crashreporting

import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.createTempDirectory
import java.io.File
import java.lang.RuntimeException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionStoreTest {

    private lateinit var recordsDirectory: File

    @BeforeTest
    fun setUp() {
        recordsDirectory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        recordsDirectory.deleteRecursively()
    }

    @Test
    fun `store exception`() {
        val timeProvider = TestTimeProvider(1_762_270_272_000L)
        val exceptionRecordStore = ExceptionRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = timeProvider,
            uuidGenerator = { UUID },
        )

        exceptionRecordStore.storeException(RuntimeException("test exception"))

        val recordFile = File(recordsDirectory, "${UUID}_v1.json")
        assertTrue(recordFile.exists())
        val content = recordFile.readText()
        assertTrue(content.startsWith("""{"id":"$UUID","stackTrace":"java.lang.RuntimeException: test exception\n"""))
        assertTrue(content.endsWith("""","createdAt":$TIMESTAMP}"""))
    }

    @Test
    fun `checking and deleting records`() {
        val exceptionRecordStore = ExceptionRecordStore(
            recordsDirectory = recordsDirectory,
            timeProvider = TestTimeProvider(),
            uuidGenerator = { UUID },
        )
        assertFalse(exceptionRecordStore.hasRecords())

        exceptionRecordStore.storeException(RuntimeException("test exception"))
        assertTrue(exceptionRecordStore.hasRecords())

        exceptionRecordStore.deleteRecords()
        assertFalse(exceptionRecordStore.hasRecords())
    }

    companion object {
        private const val TIMESTAMP = 1_762_270_272_000L
        private const val UUID = "d3bc6807-c4c7-47c2-af55-aa929d86fd09"
    }
}
