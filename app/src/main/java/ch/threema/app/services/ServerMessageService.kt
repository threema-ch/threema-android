package ch.threema.app.services

import ch.threema.storage.models.ServerMessageModel

interface ServerMessageService {
    fun saveIncomingServerMessage(msg: ServerMessageModel)
}
