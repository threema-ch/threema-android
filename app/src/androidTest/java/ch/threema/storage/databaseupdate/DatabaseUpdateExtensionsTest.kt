package ch.threema.storage.databaseupdate

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateExtensionsTest {
    private var inMemoryDatabase: SQLiteDatabase = SQLiteDatabase.create(null)

    @BeforeTest
    fun setUp() {
        inMemoryDatabase.execSQL("CREATE TABLE IF NOT EXISTS testtable (hello TEXT, world INTEGER)")
    }

    @Test
    fun testTableExistsForNonExistingTable() {
        assertFalse(inMemoryDatabase.tableExists("non_existing_table"))
    }

    @Test
    fun testTableExistsForExistingTable() {
        assertTrue(inMemoryDatabase.tableExists("testtable"))
    }

    @Test
    fun testFieldExistNonExistingTable() {
        assertFalse(inMemoryDatabase.fieldExists("non_existing_table", "non_existing_field"))
    }

    @Test
    fun testFieldExistExistingTable() {
        assertFalse(inMemoryDatabase.fieldExists("testtable", "non_existing_field"))
        assertTrue(inMemoryDatabase.fieldExists("testtable", "hello"))
        assertTrue(inMemoryDatabase.fieldExists("testtable", "world"))
    }
}
