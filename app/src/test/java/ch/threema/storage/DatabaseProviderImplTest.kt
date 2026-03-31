package ch.threema.storage

import ch.threema.localcrypto.MasterKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Test

class DatabaseProviderImplTest {

    private val masterKeyMock = mockk<MasterKey>()

    @Test
    fun `open and close database`() = runTest {
        val databaseStateMock = MutableStateFlow(DatabaseState.PREPARING)
        val readableDatabaseMock = mockk<SQLiteDatabase>()
        val writeableDatabaseMock = mockk<SQLiteDatabase>()
        val databaseOpenHelperMock = mockk<DatabaseOpenHelper> {
            every { databaseState } returns databaseStateMock
            every { oldVersion } returns 123
            every { readableDatabase } returns readableDatabaseMock
            every { writableDatabase } returns writeableDatabaseMock
            coEvery { migrateIfNeeded() } just runs
            every { close() } just runs
        }
        val databaseProvider = DatabaseProviderImpl(
            databaseOpenHelperFactory = { masterKey: MasterKey ->
                assertEquals(masterKeyMock, masterKey)
                databaseOpenHelperMock
            },
        )

        // Initial state
        assertEquals(DatabaseState.INIT, databaseProvider.databaseState.value)
        assertFails { databaseProvider.readableDatabase }
        assertFails { databaseProvider.writableDatabase }
        assertFails { databaseProvider.oldVersion }

        // Open database
        databaseProvider.open(masterKeyMock)
        assertEquals(DatabaseState.PREPARING, databaseProvider.databaseState.value)
        assertEquals(readableDatabaseMock, databaseProvider.readableDatabase)
        assertEquals(writeableDatabaseMock, databaseProvider.writableDatabase)
        assertEquals(123, databaseProvider.oldVersion)
        coVerify(exactly = 1) { databaseOpenHelperMock.migrateIfNeeded() }

        // Database state is updated
        databaseStateMock.value = DatabaseState.READY
        assertEquals(DatabaseState.READY, databaseProvider.databaseState.value)

        // Close database
        databaseProvider.close()
        verify(exactly = 1) { databaseOpenHelperMock.close() }
        assertEquals(DatabaseState.INIT, databaseProvider.databaseState.value)
        assertFails { databaseProvider.readableDatabase }
        assertFails { databaseProvider.writableDatabase }

        // Open database again
        databaseProvider.open(masterKeyMock)
        assertEquals(DatabaseState.READY, databaseProvider.databaseState.value)
        assertEquals(readableDatabaseMock, databaseProvider.readableDatabase)
        assertEquals(writeableDatabaseMock, databaseProvider.writableDatabase)
    }
}
