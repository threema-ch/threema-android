package ch.threema.app.fragments.composemessage

import ch.threema.storage.models.AbstractMessageModel

sealed interface ComposeMessageEvent {

    data class NextRecordsLoaded(
        @JvmField val messageModels: List<AbstractMessageModel>,
        @JvmField val hasMoreRecords: Boolean,
    ) : ComposeMessageEvent
}
