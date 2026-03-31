package ch.threema.app.home.usecases

import android.database.sqlite.SQLiteException
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.factories.ServerMessageModelFactory
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("CheckServerMessagesUseCase")

class CheckServerMessagesUseCase(
    private val serverMessageModelFactory: ServerMessageModelFactory,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): Boolean = withContext(dispatcherProvider.worker) {
        val serverMessageCount = try {
            serverMessageModelFactory.count()
        } catch (e: SQLiteException) {
            logger.error("Could not get server message model count", e)
            0
        }
        serverMessageCount > 0
    }
}
