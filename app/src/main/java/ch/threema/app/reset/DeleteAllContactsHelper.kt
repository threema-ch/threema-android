package ch.threema.app.reset

import ch.threema.app.asynctasks.DeleteAllContactsBackgroundTask
import ch.threema.app.asynctasks.DeleteContactServices
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.FileService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.UserService
import ch.threema.app.services.WallpaperService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.utils.executor.CoroutineBackgroundExecutor
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.storage.factories.ContactModelFactory

/**
 * @see ch.threema.app.asynctasks.DeleteAllContactsBackgroundTask
 */
class DeleteAllContactsHelper(
    private val conversationService: ConversationService,
    private val contactModelRepository: ContactModelRepository,
    private val userService: UserService,
    private val contactService: ContactService,
    private val ringtoneService: RingtoneService,
    private val conversationCategoryService: ConversationCategoryService,
    private val profilePictureRecipientsService: ProfilePictureRecipientsService,
    private val wallpaperService: WallpaperService,
    private val fileService: FileService,
    private val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
    private val dhSessionStore: DHSessionStoreInterface,
    private val notificationService: NotificationService,
    private val contactModelFactory: ContactModelFactory,
    private val coroutineBackgroundExecutor: CoroutineBackgroundExecutor,
) {
    suspend fun call() {
        coroutineBackgroundExecutor.execute(getDeleteAllContactsTask())
    }

    private fun getDeleteAllContactsTask() =
        DeleteAllContactsBackgroundTask(
            contactModelRepository,
            DeleteContactServices(
                userService,
                contactService,
                conversationService,
                ringtoneService,
                conversationCategoryService,
                profilePictureRecipientsService,
                wallpaperService,
                fileService,
                excludedSyncIdentitiesService,
                dhSessionStore,
                notificationService,
                contactModelFactory,
            ),
        )
}
