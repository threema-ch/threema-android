package ch.threema.app.apptaskexecutor

import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.di.injectNonBinding
import ch.threema.storage.DatabaseService
import org.koin.core.component.KoinComponent

class AppTaskPersistenceProvider : KoinComponent {

    private val databaseService: DatabaseService by injectNonBinding()

    suspend fun getAppTaskPersistence(): AppTaskPersistence {
        awaitAppFullyReady()
        return DatabaseAppTaskPersistence(databaseService.appTaskPersistenceFactory)
    }
}
