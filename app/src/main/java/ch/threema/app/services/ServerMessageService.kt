package ch.threema.app.services

import ch.threema.app.managers.ListenerManager
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.ServerMessageModel

interface ServerMessageService {
    fun saveIncomingServerMessage(msg: ServerMessageModel)
}

class ServerMessageServiceImpl(
    private val databaseService: DatabaseService,
) : ServerMessageService {
    override fun saveIncomingServerMessage(msg: ServerMessageModel) {
        // store message
        databaseService.serverMessageModelFactory.storeServerMessageModel(msg)

        // notify listeners
        ListenerManager.serverMessageListeners.handle {
            if (msg.type == ServerMessageModel.TYPE_ALERT) {
                it.onAlert(msg)
            } else {
                it.onError(msg)
            }
        }
    }
}
