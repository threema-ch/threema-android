package ch.threema.app.apptaskexecutor

import ch.threema.app.di.awaitAppFullyReady
import ch.threema.storage.factories.AppTaskPersistenceFactory
import org.koin.core.component.KoinComponent

class AppTaskPersistenceProvider(
    private val appTaskPersistenceFactory: AppTaskPersistenceFactory,
) : KoinComponent {
    suspend fun getAppTaskPersistence(): AppTaskPersistence {
        awaitAppFullyReady()
        return DatabaseAppTaskPersistence(appTaskPersistenceFactory)
    }
}
