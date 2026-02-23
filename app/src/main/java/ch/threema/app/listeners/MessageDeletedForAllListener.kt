package ch.threema.app.listeners

import ch.threema.storage.models.AbstractMessageModel

interface MessageDeletedForAllListener {
    fun onDeletedForAll(message: AbstractMessageModel)
}
