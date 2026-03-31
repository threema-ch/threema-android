package ch.threema.app.problemsolving

import android.content.Context
import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.PowermanagerUtil
import ch.threema.app.webclient.services.SessionService
import ch.threema.base.SessionScoped
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.localcrypto.MasterKeyManager
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.withContext

@SessionScoped
class GetProblemsUseCase(
    private val appContext: Context,
    private val sessionService: SessionService,
    private val preferenceService: PreferenceService,
    private val debugLogHelper: DebugLogHelper,
    private val timeProvider: TimeProvider,
    private val masterKeyManager: MasterKeyManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): List<Problem> = withContext(dispatcherProvider.worker) {
        buildList {
            if (ConfigUtils.isBackgroundRestricted(appContext)) {
                add(Problem.BACKGROUND_USAGE_RESTRICTED)
            }
            if (ConfigUtils.isBackgroundDataRestricted(appContext)) {
                add(Problem.BACKGROUND_DATA_RESTRICTED)
            }
            if (ConfigUtils.isNotificationsDisabled(appContext)) {
                add(Problem.NOTIFICATIONS_DISABLED)
            }
            if (ConfigUtils.isFullScreenNotificationsDisabled(appContext)) {
                add(Problem.FULLSCREEN_NOTIFICATIONS_DISABLED)
            }
            if (!PowermanagerUtil.isIgnoringBatteryOptimizations(appContext)) {
                if (preferenceService.useThreemaPush()) {
                    add(Problem.THREEMA_PUSH_BATTERY_OPTIMIZATION)
                }
                if (sessionService.hasRunningSessions()) {
                    add(Problem.WEBCLIENT_BATTERY_OPTIMIZATION)
                }
                if (masterKeyManager.awaitIsProtectedWithRemoteSecret()) {
                    add(Problem.REMOTE_SECRET_BATTERY_OPTIMIZATION)
                }
            }
            preferenceService.getDebugLogEnabledTimestamp()?.let { enabledSince ->
                if (timeProvider.get() - enabledSince > 30.days) {
                    add(Problem.DEBUG_LOG_STILL_ENABLED)
                }
            }
            if (debugLogHelper.isDebugLogFileLoggingForceEnabled()) {
                add(Problem.DEBUG_LOG_FORCE_ENABLED)
            }
        }
            .filter { problem ->
                problem.dismissKey == null || preferenceService.getProblemDismissed(problem.dismissKey) == null
            }
            .distinctBy { problem ->
                // Some problems have the same cause, so it's enough to only show one of them
                when (problem) {
                    Problem.WEBCLIENT_BATTERY_OPTIMIZATION -> Problem.THREEMA_PUSH_BATTERY_OPTIMIZATION
                    else -> problem
                }
            }
    }
}
