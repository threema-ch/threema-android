/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.asynctasks

import android.content.Context
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.fragment.app.FragmentManager
import ch.threema.app.GlobalListeners
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.asynctasks.AndroidContactLinkPolicy.KEEP
import ch.threema.app.asynctasks.AndroidContactLinkPolicy.REMOVE_LINK
import ch.threema.app.asynctasks.ContactSyncPolicy.EXCLUDE
import ch.threema.app.asynctasks.ContactSyncPolicy.INCLUDE
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog
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
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.DHSessionStoreException
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.ContactModel
import java.lang.ref.WeakReference

private const val DIALOG_TAG_DELETE_CONTACT = "dc"

private val logger = getThreemaLogger("MarkContactAsDeletedBackgroundTask")

/**
 * The collection of required services to delete a contact.
 */
data class DeleteContactServices(
    val userService: UserService,
    val contactService: ContactService,
    val conversationService: ConversationService,
    val ringtoneService: RingtoneService,
    val conversationCategoryService: ConversationCategoryService,
    val profilePictureRecipientsService: ProfilePictureRecipientsService,
    val wallpaperService: WallpaperService,
    val fileService: FileService,
    val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
    val dhSessionStore: DHSessionStoreInterface,
    val notificationService: NotificationService,
    val databaseService: DatabaseService,
)

/**
 * The policy whether to exclude the contact from future contact syncs.
 */
enum class ContactSyncPolicy {
    /**
     * The contact won't be excluded from contact sync and may therefore re-appear the next time
     * contact synchronization is executed.
     */
    INCLUDE,

    /**
     * The contact will be excluded from contact sync and won't re-appear by synchronizing the
     * contacts.
     */
    EXCLUDE,
}

/**
 * The policy whether to remove the link to Threema in android contacts (raw contact). Note that
 * modifying the android contacts may be rate limited. Therefore, we should be sparing with android
 * contact modifications.
 */
enum class AndroidContactLinkPolicy {
    /**
     * We keep the contact linked to Threema. Note that when deleting individual contacts, we should
     * not keep them linked. Use this option primarily when the Threema 'account' is deleted anyway.
     */
    KEEP,

    /**
     * The link to Threema will be removed.
     */
    REMOVE_LINK,
}

/**
 * This background task should be used to delete a contact from local. Note that this just sets the
 * acquaintance level to group, removes the conversation, and still shown notifications.
 */
open class MarkContactAsDeletedBackgroundTask(
    protected val contacts: Set<String>,
    private val contactModelRepository: ContactModelRepository,
    protected val deleteContactServices: DeleteContactServices,
    private val syncPolicy: ContactSyncPolicy,
    private val androidLinkPolicy: AndroidContactLinkPolicy,
) : BackgroundTask<Set<String>>, CancelableHorizontalProgressDialog.ProgressDialogClickListener {
    private var cancelled = false

    @CallSuper
    override fun runBefore() {
        // Note that we need to lock the android contact change lock on the UI thread in order to be
        // able to unlock it again. The reason is that the runAfter method is run on the UI thread.
        // TODO(ANDR-2327): This is a hack that may be removed when we have implemented contact
        //  import.
        RuntimeUtil.runOnUiThread {
            GlobalListeners.onAndroidContactChangeLock.lock()
        }
    }

    override fun runInBackground(): Set<String> {
        val deletedIdentities = mutableSetOf<String>()
        for ((index, identity) in contacts.withIndex()) {
            try {
                if (cancelled) {
                    return deletedIdentities
                }
                try {
                    updateProgress(index)
                } catch (e: Exception) {
                    logger.error("Could not update progress", e)
                }

                val success = markContactAsDeleted(identity)

                when (androidLinkPolicy) {
                    REMOVE_LINK -> AndroidContactUtil.getInstance()
                        .deleteThreemaRawContact(identity)

                    KEEP -> Unit
                }

                if (success) {
                    deletedIdentities.add(identity)
                }
            } catch (e: Exception) {
                logger.error("Could not delete contact {}", identity, e)
            }
        }

        return deletedIdentities
    }

    @CallSuper
    override fun runAfter(result: Set<String>) {
        GlobalListeners.onAndroidContactChangeLock.unlock()

        when (syncPolicy) {
            EXCLUDE -> {
                for (deletedIdentity in result) {
                    deleteContactServices.excludedSyncIdentitiesService.excludeFromSync(deletedIdentity, TriggerSource.LOCAL)
                }
            }

            INCLUDE -> Unit
        }

        onFinished()
    }

    /**
     * This method is run after the contacts have been deleted. Note that it is run independent of
     * how many of them could be deleted.
     */
    protected open fun onFinished() = Unit

    override fun onCancel(tag: String?, `object`: Any?) {
        cancelled = true
    }

    private fun markContactAsDeleted(identity: Identity): Boolean {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: return false

        // Note that the conversation needs to be deleted before the downgrade due to the old model
        // that may still be cached and will be stored as the last update flag will be reset.
        deleteContactServices.conversationService.delete(identity)

        // Remove the old contact model from the cache to reduce the risk to it being stored to the
        // database.
        deleteContactServices.contactService.invalidateCache(identity)

        // Cancel notifications
        deleteContactServices.notificationService.cancel(identity)

        contactModel.setAcquaintanceLevelFromLocal(ContactModel.AcquaintanceLevel.GROUP)

        return true
    }

    /**
     * This method is run before the deletion of a contact. Note that this is also called if the
     * contact could not be deleted.
     */
    protected open fun updateProgress(progress: Int) {
        // Nothing to do here
    }
}

/**
 * Use this task when all contacts should be deleted. Note that this does not remove the contact
 * links as they may be rate limited. Therefore, this task should only be used when the account is
 * deleted afterwards anyways. Otherwise some links in the android contacts may remain.
 *
 * Note: This must only be called if the app is being completely erased. If this task is executed
 * while MD is enabled, this will lead to a de-sync.
 */
open class DeleteAllContactsBackgroundTask(
    contactModelRepository: ContactModelRepository,
    deleteContactServices: DeleteContactServices,
) : MarkContactAsDeletedBackgroundTask(
    deleteContactServices.contactService.all.map(ContactModel::identity).toSet(),
    contactModelRepository,
    deleteContactServices,
    INCLUDE,
    KEEP,
) {
    override fun runAfter(result: Set<String>) {
        if (contacts.size != result.size) {
            logger.warn("Deleted {} contacts instead of {}.", result.size, contacts.size)
        }

        // Delete all contacts
        deleteContactServices.databaseService.contactModelFactory.deleteAll()
        contacts.forEach(::cleanContactLeftovers)

        super.runAfter(result)
    }

    /**
     * This cleans leftovers from a removed contact. This includes:
     * - Contact Service Cache
     * - Conversation
     * - Custom ringtone setting
     * - Mute preference
     * - Private chat (conversation category) preference
     * - Profile picture receive preference
     * - Custom wallpaper
     * - Android contact avatar
     * - Contact avatar
     * - Contact photo
     * - Cancel notifications
     * - Share target shortcut
     * - Pinned shortcut
     * - FS sessions
     *
     * This should only be called after the contact was successfully removed from the database.
     */
    private fun cleanContactLeftovers(identity: Identity) {
        deleteContactServices.contactService.invalidateCache(identity)
        deleteContactServices.conversationService.delete(identity)

        val uniqueIdString = ContactUtil.getUniqueIdString(identity)

        deleteContactServices.ringtoneService.removeCustomRingtone(uniqueIdString)
        deleteContactServices.conversationCategoryService.persistDefaultChat(uniqueIdString)
        deleteContactServices.profilePictureRecipientsService.remove(identity)
        deleteContactServices.wallpaperService.removeWallpaper(uniqueIdString)
        deleteContactServices.fileService.removeAndroidDefinedProfilePicture(identity)
        deleteContactServices.fileService.removeUserDefinedProfilePicture(identity)
        deleteContactServices.fileService.removeContactDefinedProfilePicture(identity)
        deleteContactServices.notificationService.cancel(identity)
        ShortcutUtil.deleteShareTargetShortcut(uniqueIdString)
        ShortcutUtil.deletePinnedShortcut(uniqueIdString)

        val myIdentity = deleteContactServices.userService.identity
        try {
            deleteContactServices.dhSessionStore.deleteAllDHSessions(myIdentity, identity)
        } catch (e: DHSessionStoreException) {
            logger.error("Could not delete all DH sessions with {}", identity, e)
        }
    }
}

open class DialogMarkContactAsDeletedBackgroundTask(
    private val fragmentManager: FragmentManager,
    private val contextRef: WeakReference<Context>,
    contacts: Set<String>,
    contactModelRepository: ContactModelRepository,
    deleteContactServices: DeleteContactServices,
    syncPolicy: ContactSyncPolicy,
    linkPolicy: AndroidContactLinkPolicy,
) : MarkContactAsDeletedBackgroundTask(
    contacts,
    contactModelRepository,
    deleteContactServices,
    syncPolicy,
    linkPolicy,
) {
    override fun runBefore() {
        val dialog: CancelableHorizontalProgressDialog =
            CancelableHorizontalProgressDialog.newInstance(
                R.string.deleting_contact,
                contacts.size,
            )
        dialog.show(fragmentManager, DIALOG_TAG_DELETE_CONTACT)

        super.runBefore()
    }

    override fun runAfter(result: Set<String>) {
        super.runAfter(result)

        DialogUtil.dismissDialog(
            fragmentManager,
            DIALOG_TAG_DELETE_CONTACT,
            true,
        )

        val context = contextRef.get() ?: return

        val failed = contacts.size - result.size
        if (failed > 0) {
            Toast.makeText(
                context,
                ConfigUtils.getSafeQuantityString(
                    ThreemaApplication.getAppContext(),
                    R.plurals.some_contacts_not_deleted,
                    failed,
                    failed,
                ),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            if (result.size > 1) {
                Toast.makeText(context, R.string.contacts_deleted, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, R.string.contact_deleted, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun updateProgress(progress: Int) {
        RuntimeUtil.runOnUiThread {
            DialogUtil.updateProgress(fragmentManager, DIALOG_TAG_DELETE_CONTACT, progress)
        }
    }
}
