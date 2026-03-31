package ch.threema.app.services

import ch.threema.app.managers.ListenerManager
import ch.threema.storage.factories.ServerMessageModelFactory
import ch.threema.storage.models.ServerMessageModel

class ServerMessageServiceImpl(
    private val serverMessageModelFactory: ServerMessageModelFactory,
) : ServerMessageService {
    override fun saveIncomingServerMessage(msg: ServerMessageModel) {
        // store message
        serverMessageModelFactory.storeServerMessageModel(msg)

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
