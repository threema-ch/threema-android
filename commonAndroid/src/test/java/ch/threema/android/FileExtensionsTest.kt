package ch.threema.android

import ch.threema.testhelpers.createTempDirectory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class FileExtensionsTest {

    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `write file successfully`() {
        val file = File(directory, "my-file")
        assertFalse(file.exists())

        file.writeAtomically { outputStream ->
            outputStream.write(byteArrayOf(1, 2, 3))
            outputStream.write(byteArrayOf(4, 5, 6))
        }

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), file.readBytes())
    }

    @Test
    fun `write file unsuccessfully`() {
        val file = File(directory, "my-file")
        assertFalse(file.exists())

        assertFails {
            file.writeAtomically { outputStream ->
                outputStream.write(byteArrayOf(1, 2, 3))
                error("something went wrong")
            }
        }

        assertFalse(file.exists())
    }
}
