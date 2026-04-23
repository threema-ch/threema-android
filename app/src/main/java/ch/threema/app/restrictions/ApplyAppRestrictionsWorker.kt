package ch.threema.app.restrictions

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.setBackoffCriteria
import ch.threema.android.setConstraints
import ch.threema.app.R
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.GroupCallPolicySetting
import ch.threema.app.preference.service.O2oCallPolicySetting
import ch.threema.app.preference.service.O2oCallVideoPolicySetting
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.ScreenshotPolicySetting
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.preference.service.UnknownContactPolicySetting
import ch.threema.app.restrictions.ApplyAppRestrictionsWorker.RestrictionToPreferenceValueMapper.Invert
import ch.threema.app.restrictions.ApplyAppRestrictionsWorker.RestrictionToPreferenceValueMapper.Keep
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.workers.AutoDeleteWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.minus
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.RemoteSecretCheckType
import ch.threema.protobuf.d2d.sync.Settings
import java.time.Instant
import kotlin.collections.any
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ApplyAppRestrictionsWorker")

/**
 * This worker applies the app restrictions. Applying the app restrictions includes the following actions:
 * - Mapping the currently set app restrictions to their corresponding preferences. Those preferences will be reflected if multi device is
 * active. TODO(PRD-152): Note that this behavior is likely to change once we reflect mdm parameters directly.
 * - Deactivating multi device in case th_disable_multidevice or th_disable_web is set.
 * - Restarting the app if Remote Secrets needs to be activated or deactivated
 *
 * Note that this work must never be enqueued from SYNC.
 */
class ApplyAppRestrictionsWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val masterKeyManager: MasterKeyManager by inject()
    private val userService: UserService by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val taskCreator: TaskCreator by inject()
    private val lifetimeService: LifetimeService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val appRestrictionProvider: AppRestrictionProvider by inject()
    private val autoDeleteWorkerScheduler: AutoDeleteWorker.Scheduler by inject()

    override suspend fun doWork(): Result {
        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: run {
                logger.error("Could not apply app restrictions because the service manager is not available")
                return Result.retry()
            }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPreferences == null) {
            logger.error("Could not apply app restrictions because the shared preferences are not available")
            return Result.retry()
        }

        try {
            mapRestrictionsToPreferences(
                sharedPreferences = sharedPreferences,
                context = context,
                userService = userService,
                multiDeviceManager = multiDeviceManager,
                taskCreator = taskCreator,
                lifetimeService = lifetimeService,
                synchronizedSettingsService = synchronizedSettingsService,
                triggerSource = TriggerSource.LOCAL,
            )
        } catch (e: TransactionScope.TransactionException) {
            logger.error("Could not execute transaction. This may now possibly lead to a desync.", e)
            // We do not retry the work as a transaction exception will probably be thrown on retries as well.
            return Result.failure()
        } catch (e: Exception) {
            logger.error("Error while mapping restrictions to preferences", e)
            return Result.retry()
        }

        disableMultiDeviceIfRestricted(
            multiDeviceManager = multiDeviceManager,
            taskCreator = taskCreator,
        )

        if (masterKeyManager.shouldUpdateRemoteSecretProtectionState(RemoteSecretCheckType.APP_RUNTIME)) {
            ConfigUtils.scheduleAppRestart(context)
        }

        return Result.success()
    }

    /**
     * App restrictions are currently just mapped to their corresponding preferences.
     *
     * TODO(PRD-152): Note that this behavior is likely to change once we reflect mdm parameters directly.
     *
     * @throws TransactionScope.TransactionException if the transaction fails
     */
    private suspend fun mapRestrictionsToPreferences(
        sharedPreferences: SharedPreferences,
        context: Context,
        userService: UserService,
        multiDeviceManager: MultiDeviceManager,
        taskCreator: TaskCreator,
        lifetimeService: LifetimeService,
        synchronizedSettingsService: SynchronizedSettingsService,
        @Suppress("SameParameterValue") triggerSource: TriggerSource,
    ) {
        logger.info("Start mapping restrictions to settings")

        // First we just check which changes need to be made
        val checkResults = RestrictionMapCheckResults(
            listOf(
                checkBlockUnknown(context, sharedPreferences),
                checkDisableScreenshots(context, sharedPreferences),
                checkDisableSaveToGallery(context, sharedPreferences),
                checkDisableMessagePreview(context, sharedPreferences),
                checkDisableCalls(context, sharedPreferences),
                checkDisableVideoCalls(context, sharedPreferences),
                checkDisableGroupCalls(context, sharedPreferences),
                checkHideInactiveIds(context, sharedPreferences),
            ),
        )

        // Then we define the action to persist the changes
        val persistSettings: () -> Unit = {
            if (checkResults.sharedPreferencesAppliers.isNotEmpty()) {
                logger.info("Persisting preferences")
                sharedPreferences.edit {
                    checkResults.sharedPreferencesAppliers.forEach { applier ->
                        applier.invoke(this)
                    }
                }
            } else {
                logger.info("No need to persist preferences")
            }
        }

        if (multiDeviceManager.isMultiDeviceActive && triggerSource != TriggerSource.SYNC && checkResults.hasReflectionData) {
            // In case multi device is active and there are some changes that must be reflected, we acquire a connection, reflect the changes, and
            // release the connection.
            logger.info("Scheduling task to reflect settings")
            lifetimeService.acquireUnpauseableConnection(LIFETIME_SOURCE_TAG)
            try {
                taskCreator.scheduleReflectMultipleSettingsSyncUpdateTask(checkResults.settingsSyncCreators).await()
            } finally {
                lifetimeService.releaseConnection(LIFETIME_SOURCE_TAG)
            }
        }

        // Then persist the settings
        persistSettings()

        // We need to refresh them as we may have modified the preferences directly.
        synchronizedSettingsService.reloadSynchronizedBooleanSettings()

        applyProfilePictureChangeRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            multiDeviceManager = multiDeviceManager,
            taskCreator = taskCreator,
        )

        applyNicknameRestriction(
            userService = userService,
            triggerSource = triggerSource,
        )

        // Update the periodic auto delete worker
        autoDeleteWorkerScheduler.scheduleAutoDelete()

        logger.info("Mapping restrictions to preferences finished")
    }

    private fun disableMultiDeviceIfRestricted(multiDeviceManager: MultiDeviceManager, taskCreator: TaskCreator) {
        if (ConfigUtils.isMultiDeviceEnabled()) {
            // Nothing to do if multi device is not disabled
            return
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleDeactivateMultiDeviceTask()
        }
    }

    private fun checkBlockUnknown(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__block_unknown,
            preferenceKeyRes = UnknownContactPolicySetting.preferenceKeyStringRes,
            restrictionToPreferenceValueMapper = Keep,
            settingsSyncCreator = { settingsBuilder, blockUnknownRestriction ->
                settingsBuilder.setUnknownContactPolicy(
                    if (blockUnknownRestriction) {
                        Settings.UnknownContactPolicy.BLOCK_UNKNOWN
                    } else {
                        Settings.UnknownContactPolicy.ALLOW_UNKNOWN
                    },
                )
            },
        )
    }

    private fun checkDisableScreenshots(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_screenshots,
            preferenceKeyRes = ScreenshotPolicySetting.preferenceKeyStringRes,
            restrictionToPreferenceValueMapper = Keep,
            settingsSyncCreator = { settingsBuilder, disableScreenshotsRestriction ->
                settingsBuilder.setScreenshotPolicy(
                    if (disableScreenshotsRestriction) {
                        Settings.ScreenshotPolicy.DENY_SCREENSHOT
                    } else {
                        Settings.ScreenshotPolicy.ALLOW_SCREENSHOT
                    },
                )
            },
        )
    }

    private fun checkDisableSaveToGallery(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_save_to_gallery,
            preferenceKeyRes = R.string.preferences__save_media,
            restrictionToPreferenceValueMapper = Invert,
            // Note that this restriction cannot not be reflected
            settingsSyncCreator = null,
        )
    }

    private fun checkDisableMessagePreview(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_message_preview,
            preferenceKeyRes = R.string.preferences__notification_preview,
            restrictionToPreferenceValueMapper = Invert,
            // Note that this restriction cannot not be reflected
            settingsSyncCreator = null,
        )
    }

    private fun checkDisableCalls(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_calls,
            preferenceKeyRes = O2oCallPolicySetting.preferenceKeyStringRes,
            restrictionToPreferenceValueMapper = Invert,
            settingsSyncCreator = { settingsBuilder, disableCallsRestriction ->
                settingsBuilder.setO2OCallPolicy(
                    if (disableCallsRestriction) {
                        Settings.O2oCallPolicy.DENY_O2O_CALL
                    } else {
                        Settings.O2oCallPolicy.ALLOW_O2O_CALL
                    },
                )
            },
        )
    }

    private fun checkDisableVideoCalls(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_video_calls,
            preferenceKeyRes = O2oCallVideoPolicySetting.preferenceKeyStringRes,
            restrictionToPreferenceValueMapper = Invert,
            settingsSyncCreator = { settingsBuilder, disableVideoCallsRestriction ->
                settingsBuilder.setO2OCallVideoPolicy(
                    if (disableVideoCallsRestriction) {
                        Settings.O2oCallVideoPolicy.DENY_VIDEO
                    } else {
                        Settings.O2oCallVideoPolicy.ALLOW_VIDEO
                    },
                )
            },
        )
    }

    private fun checkDisableGroupCalls(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__disable_group_calls,
            preferenceKeyRes = GroupCallPolicySetting.preferenceKeyStringRes,
            restrictionToPreferenceValueMapper = Invert,
            settingsSyncCreator = { settingsBuilder, disableGroupCallsRestriction ->
                settingsBuilder.setGroupCallPolicy(
                    if (disableGroupCallsRestriction) {
                        Settings.GroupCallPolicy.DENY_GROUP_CALL
                    } else {
                        Settings.GroupCallPolicy.ALLOW_GROUP_CALL
                    },
                )
            },
        )
    }

    private fun checkHideInactiveIds(context: Context, sharedPreferences: SharedPreferences): RestrictionMapCheckResult {
        return checkBooleanRestriction(
            context = context,
            sharedPreferences = sharedPreferences,
            restrictionKeyRes = R.string.restriction__hide_inactive_ids,
            preferenceKeyRes = R.string.preferences__show_inactive_contacts,
            restrictionToPreferenceValueMapper = Invert,
            // Note that this restriction cannot be reflected
            settingsSyncCreator = null,
        )
    }

    /**
     * Checks whether the restriction with key [restrictionKeyRes] is set. If it is set, it checks whether the corresponding preference with key
     * [preferenceKeyRes] needs to be changed to the value determined with [restrictionToPreferenceValueMapper].
     */
    private fun checkBooleanRestriction(
        context: Context,
        sharedPreferences: SharedPreferences,
        @StringRes restrictionKeyRes: Int,
        @StringRes preferenceKeyRes: Int,
        restrictionToPreferenceValueMapper: RestrictionToPreferenceValueMapper,
        settingsSyncCreator: ((Settings.Builder, Boolean) -> Unit)?,
    ): RestrictionMapCheckResult {
        val restrictionValue = appRestrictionProvider.getBooleanRestriction(context.getString(restrictionKeyRes))
            ?: return RestrictionMapCheckResult(
                sharedPreferencesApplier = null,
                settingsSyncCreators = null,
            )

        val preferenceKey = context.getString(preferenceKeyRes)
        val sharedPreferencesChange =
            sharedPreferences.getBooleanSharedPreferenceChange(preferenceKey, restrictionToPreferenceValueMapper.toPreferenceValue(restrictionValue))

        if (sharedPreferencesChange == null || settingsSyncCreator == null) {
            return RestrictionMapCheckResult(
                sharedPreferencesApplier = sharedPreferencesChange,
                settingsSyncCreators = null,
            )
        }

        val settingsSyncChange: (Settings.Builder) -> Unit = { settingsBuilder ->
            settingsSyncCreator(settingsBuilder, restrictionValue)
        }
        return RestrictionMapCheckResult(sharedPreferencesChange, settingsSyncChange)
    }

    private fun SharedPreferences.getBooleanSharedPreferenceChange(settingsKey: String, value: Boolean): ((Editor) -> Unit)? =
        if (!contains(settingsKey) || getBoolean(settingsKey, false) != value) {
            { editor -> editor.putBoolean(settingsKey, value) }
        } else {
            null
        }

    private fun applyProfilePictureChangeRestriction(
        context: Context,
        sharedPreferences: SharedPreferences,
        multiDeviceManager: MultiDeviceManager,
        taskCreator: TaskCreator,
    ) {
        val profilePictureShareRestriction = appRestrictions.isDisabledProfilePicReleaseSettingsOrNull()
            ?: // We do not need to do anything if it isn't set
            return

        val preferenceKeyName = context.getString(R.string.preferences__profile_pic_release)
        val preferenceValue = sharedPreferences.getInt(preferenceKeyName, -1)

        if (profilePictureShareRestriction && preferenceValue == PreferenceService.PROFILEPIC_RELEASE_NOBODY) {
            // Profile picture sharing is restricted but disabled anyways. Therefore nothing to do.
            return
        }

        if (!profilePictureShareRestriction && preferenceValue == PreferenceService.PROFILEPIC_RELEASE_EVERYONE) {
            // Profile picture sharing is explicitly enabled but preference already matches this. Therefore nothing to do.
            return
        }

        // Persist the preference with the updated value
        sharedPreferences.edit {
            val newValue = if (profilePictureShareRestriction) {
                PreferenceService.PROFILEPIC_RELEASE_NOBODY
            } else {
                PreferenceService.PROFILEPIC_RELEASE_EVERYONE
            }
            putInt(preferenceKeyName, newValue)
        }

        // Sync new policy setting to device group (if md is active)
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectUserProfileShareWithPolicySyncTask(
                if (profilePictureShareRestriction) {
                    ProfilePictureSharePolicy.Policy.NOBODY
                } else {
                    ProfilePictureSharePolicy.Policy.EVERYONE
                },
            )
        }
    }

    private fun applyNicknameRestriction(
        userService: UserService,
        triggerSource: TriggerSource,
    ) {
        appRestrictions.getNickname()?.let { nickname ->
            if (userService.publicNickname != nickname) {
                userService.setPublicNickname(nickname, triggerSource)
            }
        }
    }

    /**
     * The actions required based on all current restrictions.
     */
    private class RestrictionMapCheckResults(restrictionMapCheckResults: List<RestrictionMapCheckResult>) {
        val sharedPreferencesAppliers = restrictionMapCheckResults.mapNotNull(RestrictionMapCheckResult::sharedPreferencesApplier)
        val settingsSyncCreators = restrictionMapCheckResults.mapNotNull(RestrictionMapCheckResult::settingsSyncCreators)

        val hasReflectionData: Boolean
            get() = settingsSyncCreators.isNotEmpty()
    }

    /**
     * The actions required based on a specific restriction.
     */
    private data class RestrictionMapCheckResult(
        /**
         * The action that needs to be applied to persist the preference based on a restriction.
         */
        val sharedPreferencesApplier: ((Editor) -> Unit)?,
        /**
         * The action that modifies a settings builder to contain the updated value of the preference.
         */
        val settingsSyncCreators: ((Settings.Builder) -> Unit)?,
    )

    private sealed interface RestrictionToPreferenceValueMapper {
        fun toPreferenceValue(restrictionValue: Boolean): Boolean

        data object Keep : RestrictionToPreferenceValueMapper {
            override fun toPreferenceValue(restrictionValue: Boolean) = restrictionValue
        }

        data object Invert : RestrictionToPreferenceValueMapper {
            override fun toPreferenceValue(restrictionValue: Boolean) = !restrictionValue
        }
    }

    companion object {
        /**
         * The lifetime source tag that is used for the lifetime service.
         */
        private const val LIFETIME_SOURCE_TAG = "ApplyAppRestrictionsWorker"

        /**
         * The unique name of the work.
         */
        private const val UNIQUE_WORK_NAME = "ApplyAppRestrictions"

        /**
         * Enqueues the app restriction worker as expedited one time work request.
         */
        fun applyAppRestrictions(context: Context) {
            val workManager = WorkManager.getInstance(context)

            if (workManager.isWorkAlreadyEnqueued(UNIQUE_WORK_NAME, withinNext = 30.seconds)) {
                // No need to enqueue another work request as there already is one instance that will soon start.
                return
            }

            workManager.enqueueUniqueWork(
                uniqueWorkName = UNIQUE_WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                request = buildWorkRequest(),
            )
        }

        private fun WorkManager.isWorkAlreadyEnqueued(uniqueWorkName: String, withinNext: Duration): Boolean {
            val now = Instant.now()
            return getWorkInfosForUniqueWork(uniqueWorkName).get().any { workInfo ->
                workInfo.state == WorkInfo.State.ENQUEUED &&
                    Instant.ofEpochMilli(workInfo.nextScheduleTimeMillis) - now < withinNext
            }
        }

        private fun buildWorkRequest(): OneTimeWorkRequest = buildOneTimeWorkRequest<ApplyAppRestrictionsWorker> {
            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            setConstraints {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }

            // If it fails, retry again in a few minutes. The most likely case that it fails is because of a locked master key or unstable network
            // connection. Note that this worker is also enqueued once the master key is being unlocked.
            setBackoffCriteria(
                backoffPolicy = BackoffPolicy.LINEAR,
                backoffDelay = 5.minutes,
            )
        }
    }
}
