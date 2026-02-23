package ch.threema.app.asynctasks

import androidx.annotation.WorkerThread
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("AddContactBackgroundTask")

/**
 * This task simply adds the given contact model data if the contact does not exist. If there is a
 * contact with the same identity already, adding the new data is aborted.
 */
class AddContactBackgroundTask(
    private val contactModelData: ContactModelData,
    private val contactModelRepository: ContactModelRepository,
) : BackgroundTask<ContactModel?> {
    /**
     * Add the contact model data if the contact does not exist.
     *
     * @return the newly inserted contact model or null if it could not be inserted
     */
    @WorkerThread
    fun runSynchronously(): ContactModel? {
        runBefore()

        runInBackground().let {
            runAfter(it)
            return it
        }
    }

    /**
     * Do not call this method directly. Use [runSynchronously] or run this task using a
     * [BackgroundExecutor].
     */
    override fun runInBackground(): ContactModel? {
        if (contactModelRepository.getByIdentity(contactModelData.identity) != null) {
            logger.warn("Contact already exists")
            return null
        }

        return try {
            runBlocking {
                contactModelRepository.createFromLocal(contactModelData)
            }
        } catch (e: ContactCreateException) {
            logger.error("Contact could not be created", e)
            null
        }
    }
}
