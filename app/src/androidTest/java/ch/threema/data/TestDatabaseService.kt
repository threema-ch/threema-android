package ch.threema.data

import androidx.test.core.app.ApplicationProvider
import ch.threema.storage.DatabaseService

/**
 * An in-memory database used in android tests.
 */
class TestDatabaseService : DatabaseService(
    context = ApplicationProvider.getApplicationContext(),
    databaseName = null,
    password = "test-database-key".toByteArray(),
)
