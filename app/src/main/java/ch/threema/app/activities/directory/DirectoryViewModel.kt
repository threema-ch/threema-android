package ch.threema.app.activities.directory

import ch.threema.app.asynctasks.AddOrUpdateWorkContactBackgroundTask
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.work.WorkDirectoryContact
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("DirectoryViewModel")

class DirectoryViewModel(
    private val dispatcherProvider: DispatcherProvider,
    private val identityProvider: IdentityProvider,
    private val contactModelRepository: ContactModelRepository,
) : BaseViewModel<Unit, DirectoryScreenEvent>() {

    override fun initialize() = runInitialization {}

    fun addContact(
        workDirectoryContact: WorkDirectoryContact,
        changedAdapterPosition: Int,
        openOnSuccess: Boolean,
    ) = runAction {
        logger.info("Add new work contact")
        val contactAdded: Boolean = addContact(workDirectoryContact)
        emitEvent(
            if (contactAdded) {
                DirectoryScreenEvent.WorkContactAdded(
                    workDirectoryContact = workDirectoryContact,
                    changedAdapterPosition = changedAdapterPosition,
                    openOnSuccess = openOnSuccess,
                )
            } else {
                DirectoryScreenEvent.Error
            },
        )
    }

    private suspend fun ViewModelActionScope<Unit, DirectoryScreenEvent>.addContact(workDirectoryContact: WorkDirectoryContact): Boolean {
        val myIdentity = identityProvider.getIdentity()
            ?: run {
                logger.error("Can not add new work contact, as the user's identity is missing")
                emitEvent(DirectoryScreenEvent.Error)
                endAction()
            }
        val createContactTask = AddOrUpdateWorkContactBackgroundTask(
            workContact = workDirectoryContact,
            myIdentity = myIdentity.value,
            contactModelRepository = contactModelRepository,
        )
        val contactModel = withContext(dispatcherProvider.io) {
            createContactTask.runSynchronously()
        }
        return contactModel != null
    }
}
