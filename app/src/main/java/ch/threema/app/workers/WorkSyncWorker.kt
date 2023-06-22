/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.workers

import android.content.Context
import android.content.SharedPreferences.Editor
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.notifications.NotificationBuilderWrapper
import ch.threema.app.routines.UpdateAppLogoRoutine
import ch.threema.app.routines.UpdateWorkInfoRoutine
import ch.threema.app.services.*
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.UserCredentials
import ch.threema.app.stores.IdentityStore
import ch.threema.app.utils.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.TestUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkData
import ch.threema.storage.models.ContactModel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class WorkSyncWorker(private val context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private val logger = LoggingUtil.getThreemaLogger("WorkSyncWorker")

    private val contactService: ContactService?
    private val preferenceService: PreferenceService?
    private val fileService: FileService?
    private val licenseService: LicenseService<*>?
    private val apiConnector: APIConnector?
    private val notificationService: NotificationService?
    private val userService: UserService?
    private val identityStore: IdentityStore?

    companion object {
        private const val EXTRA_REFRESH_RESTRICTIONS_ONLY = "RESTRICTIONS_ONLY"
        private const val EXTRA_FORCE_UPDATE = "FORCE_UPDATE"

        fun buildOneTimeWorkRequest(refreshRestrictionsOnly: Boolean, forceUpdate: Boolean, tag: String?): OneTimeWorkRequest {
            val data = Data.Builder()
                    .putBoolean(EXTRA_REFRESH_RESTRICTIONS_ONLY, refreshRestrictionsOnly)
                    .putBoolean(EXTRA_FORCE_UPDATE, forceUpdate)
                    .build()

            val builder = OneTimeWorkRequestBuilder<WorkSyncWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .apply { setInputData(data) }

            tag?.let {
                builder.addTag(tag)
            }

            return builder.build()
        }

        fun buildPeriodicWorkRequest(schedulePeriodMs: Long): PeriodicWorkRequest {
            val data = Data.Builder()
                .putBoolean(EXTRA_REFRESH_RESTRICTIONS_ONLY, false)
                .putBoolean(EXTRA_FORCE_UPDATE, false)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<WorkSyncWorker>(schedulePeriodMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(schedulePeriodMs.toString())
                .apply { setInputData(data) }
                .build()
        }

        /**
         * Start a one time work sync request.
         *
         * @param onSuccess is run when the work sync request was successful
         * @param onFail    is run when the work sync request was unsuccessful
         */
        fun performOneTimeWorkSync(
            activity: AppCompatActivity,
            onSuccess: Runnable,
            onFail: Runnable
        ) {
            val workerTag = "OneTimeWorkSyncWorker"
            val workRequest = buildOneTimeWorkRequest(
                refreshRestrictionsOnly = false,
                forceUpdate = true,
                tag = workerTag
            )
            val workManager = WorkManager.getInstance(ThreemaApplication.getAppContext())
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observe(activity) { workInfo: WorkInfo ->
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        onSuccess.run()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        onFail.run()
                    }
                }
            workManager.enqueueUniqueWork(
                ThreemaApplication.WORKER_WORK_SYNC,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    init {
        val serviceManager = ThreemaApplication.getServiceManager()
        contactService = serviceManager?.contactService
        preferenceService = serviceManager?.preferenceService
        licenseService = serviceManager?.licenseService
        fileService = serviceManager?.fileService
        notificationService = serviceManager?.notificationService
        userService = serviceManager?.userService
        apiConnector = serviceManager?.apiConnector
        identityStore = serviceManager?.identityStore
    }

    override fun doWork(): Result {
        val updateRestrictionsOnly: Boolean = inputData.getBoolean(EXTRA_REFRESH_RESTRICTIONS_ONLY, false)
        val forceUpdate: Boolean = inputData.getBoolean(EXTRA_FORCE_UPDATE, false)

        logger.info("Refreshing work data. Restrictions only = {}, force = {}", updateRestrictionsOnly, forceUpdate)

        if (licenseService == null || notificationService == null || contactService == null || apiConnector == null || preferenceService == null) {
            logger.info("Services not available")
            return Result.failure()
        }

        if (!ConfigUtils.isWorkBuild()) {
            logger.info("Not allowed to run in a non work environment")
            return Result.failure()
        }

        val credentials = licenseService.loadCredentials() as? UserCredentials ?: return Result.failure()

        if (!updateRestrictionsOnly) {
            val workData: WorkData?
            try {
                val allContacts: List<ContactModel> = contactService.all
                val identities = arrayOfNulls<String>(allContacts.size)
                for (n in allContacts.indices) {
                    identities[n] = allContacts[n].identity
                }
                workData = apiConnector
                        .fetchWorkData(
                                credentials.username,
                                credentials.password,
                                identities)
            } catch (e: Exception) {
                logger.error("Failed to fetch2 work data from API", e)
                notificationService.cancelWorkSyncProgress()
                return Result.failure()
            }

            if (workData.responseCode > 0) {
                logger.error("Failed to fetch2 work data. Server response code = {}", workData.responseCode)
                if (workData.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    RuntimeUtil.runOnUiThread {
                        Toast.makeText(context, R.string.fetch2_failure, Toast.LENGTH_LONG).show()
                    }
                }
                notificationService.cancelWorkSyncProgress()
                return Result.failure()
            }

            val existingWorkContacts: List<ContactModel> = contactService.allWork
            for (workContact in workData.workContacts) {
                contactService.addWorkContact(workContact, existingWorkContacts)
            }

            //downgrade work contacts
            for (x in existingWorkContacts.indices) {
                //remove isWork flag
                val c = existingWorkContacts[x]
                c.setIsWork(false)
                if (c.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                    c.verificationLevel = VerificationLevel.UNVERIFIED
                }
                this.contactService.save(c)
            }

            // update applogos
            // start a new thread to lazy download the app icons
            logger.trace("Updating app logos in new thread")
            Thread(UpdateAppLogoRoutine(
                    this.fileService,
                    this.preferenceService,
                    workData.logoLight,
                    workData.logoDark,
                    forceUpdate
            ), "UpdateAppIcon").start()
            preferenceService.customSupportUrl = workData.supportUrl
            if (workData.mdm.parameters != null) {
                // Save the Mini-MDM Parameters to a local file
                AppRestrictionService.getInstance()
                        .storeWorkMDMSettings(workData.mdm)
            }

            // update work info
            UpdateWorkInfoRoutine(
                    context,
                    apiConnector,
                    identityStore,
                    null,
                    licenseService
            ).run()
            preferenceService.workDirectoryEnabled = workData.directory.enabled
            preferenceService.workDirectoryCategories = workData.directory.categories
            preferenceService.workOrganization = workData.organization
            logger.trace("CheckInterval = " + workData.checkInterval)
            if (workData.checkInterval > 0) {
                //schedule next interval
                preferenceService.workSyncCheckInterval = workData.checkInterval
            }
        }

        resetRestrictions()

        notificationService.cancelWorkSyncProgress()

        logger.info("Refreshing work data successfully finished")

        return Result.success()
    }

    private fun resetRestrictions() {
        /* note that PreferenceService may not be available at this time */
        logger.debug("Reset Restrictions")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPreferences != null) {
            val editor = sharedPreferences.edit()
            if (editor != null) {
                applyBooleanRestriction(editor, R.string.restriction__block_unknown, R.string.preferences__block_unknown) { it }
                applyBooleanRestriction(editor, R.string.restriction__disable_screenshots, R.string.preferences__hide_screenshots) { it }
                applyBooleanRestriction(editor, R.string.restriction__disable_save_to_gallery, R.string.preferences__save_media) { !it }
                applyBooleanRestriction(editor, R.string.restriction__disable_message_preview, R.string.preferences__notification_preview) { !it }
                applyBooleanRestrictionMapToInt(editor, R.string.restriction__disable_send_profile_picture, R.string.preferences__profile_pic_release) {
                    if (it) {
                        PreferenceService.PROFILEPIC_RELEASE_NOBODY
                    } else {
                        PreferenceService.PROFILEPIC_RELEASE_EVERYONE
                    }
                }
                applyBooleanRestriction(editor, R.string.restriction__disable_calls, R.string.preferences__voip_enable) { !it }
                applyBooleanRestriction(editor, R.string.restriction__disable_video_calls, R.string.preferences__voip_video_enable) { !it }
                applyBooleanRestriction(editor, R.string.restriction__disable_group_calls, R.string.preferences__group_calls_enable) { !it }
                applyBooleanRestriction(editor, R.string.restriction__hide_inactive_ids, R.string.preferences__show_inactive_contacts) { !it }
                editor.apply()
                applyNicknameRestriction()
            }
        }
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        val notification = NotificationBuilderWrapper(context, NotificationService.NOTIFICATION_CHANNEL_WORK_SYNC, null)
                .setSound(null)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentTitle(context.getString(R.string.wizard1_sync_work))
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

        return Futures.immediateFuture(ForegroundInfo(ThreemaApplication.WORK_SYNC_NOTIFICATION_ID, notification))
    }

    private fun applyBooleanRestriction(editor: Editor, @StringRes restrictionKeyRes: Int, @StringRes settingKeyRes: Int, mapper: (Boolean) -> Boolean) {
        AppRestrictionUtil.getBooleanRestriction(context.getString(restrictionKeyRes))?.let {
            editor.putBoolean(context.getString(settingKeyRes), mapper(it))
        }
    }

    private fun applyBooleanRestrictionMapToInt(editor: Editor, @StringRes restrictionKeyRes: Int, @StringRes settingKeyRes: Int, mapper: (Boolean) -> Int) {
        AppRestrictionUtil.getBooleanRestriction(context.getString(restrictionKeyRes))?.let {
            editor.putInt(context.getString(settingKeyRes), mapper(it))
        }
    }

    private fun applyNicknameRestriction() {
        AppRestrictionUtil.getStringRestriction(context.getString(R.string.restriction__nickname))?.let {
            if (userService != null && !TestUtil.compare(userService.publicNickname, it)) {
                userService.publicNickname = it
            }
        }
    }
}
