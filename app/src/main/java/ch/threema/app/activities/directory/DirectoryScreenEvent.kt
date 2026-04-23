package ch.threema.app.activities.directory

import ch.threema.domain.protocol.api.work.WorkDirectoryContact

sealed interface DirectoryScreenEvent {

    data class WorkContactAdded(
        @JvmField
        val workDirectoryContact: WorkDirectoryContact,
        @JvmField
        val changedAdapterPosition: Int,
        @JvmField
        val openOnSuccess: Boolean,
    ) : DirectoryScreenEvent

    data object Error : DirectoryScreenEvent
}
