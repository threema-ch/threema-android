package ch.threema.app.listeners

import ch.threema.storage.models.AbstractMessageModel

interface EditMessageListener {
    fun onEdit(message: AbstractMessageModel)
}
