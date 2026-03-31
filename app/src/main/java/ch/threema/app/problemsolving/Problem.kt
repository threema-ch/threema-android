package ch.threema.app.problemsolving

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.logging.DebugLogHelper.Companion.FORCE_ENABLE_FILE_NAME

/**
 * @param dismissKey If defined, the user can dismiss this problem to keep it from being reported. The key is used to persist this decision.
 * @param solutionType The type of action the user can take to solve the problem, or null if it is not a solveable problem but a problem the user
 * needs to be made aware of.
 */
@Immutable
enum class Problem(
    @StringRes
    val titleRes: Int,
    val explanation: ResolvableString,
    val dismissKey: String? = null,
    val solutionType: SolutionType? = SolutionType.ToSettings,
) {
    BACKGROUND_USAGE_RESTRICTED(
        titleRes = R.string.problemsolver_title_background,
        explanation = ResourceIdString(R.string.problemsolver_explain_background),
        dismissKey = "background_usage",
    ),
    BACKGROUND_DATA_RESTRICTED(
        titleRes = R.string.problemsolver_title_background_data,
        explanation = ResourceIdString(R.string.problemsolver_explain_background_data),
        dismissKey = "background_data",
    ),
    DEBUG_LOG_FORCE_ENABLED(
        titleRes = R.string.problemsolver_title_debug_log_enabled,
        explanation = ResolvableString { context ->
            context.getString(R.string.problemsolver_explain_debug_log_force_enabled, FORCE_ENABLE_FILE_NAME)
        },
        dismissKey = "forced_debug_log",
        solutionType = null,
    ),
    DEBUG_LOG_STILL_ENABLED(
        titleRes = R.string.problemsolver_title_debug_log_enabled,
        explanation = ResourceIdString(R.string.problemsolver_explain_debug_log_enabled),
        dismissKey = "debug_log",
        solutionType = SolutionType.InstantAction(R.string.disable),
    ),
    NOTIFICATIONS_DISABLED(
        titleRes = R.string.problemsolver_title_notifications,
        explanation = ResourceIdString(R.string.problemsolver_explain_notifications),
    ),
    FULLSCREEN_NOTIFICATIONS_DISABLED(
        titleRes = R.string.problemsolver_title_fullscreen_notifications,
        explanation = ResourceIdString(R.string.problemsolver_explain_fullscreen_notifications),
        dismissKey = "fullscreen_notifications",
    ),
    THREEMA_PUSH_BATTERY_OPTIMIZATION(
        titleRes = R.string.problemsolver_title_app_battery_usgae_optimized,
        explanation = ResourceIdString(R.string.problemsolver_explain_app_battery_usgae_optimized),
        dismissKey = "threema_push_battery_optimization",
    ),
    WEBCLIENT_BATTERY_OPTIMIZATION(
        titleRes = R.string.problemsolver_title_app_battery_usgae_optimized,
        explanation = { context ->
            context.getString(
                R.string.battery_optimizations_explain,
                context.getString(R.string.webclient),
                context.getString(R.string.app_name),
            )
        },
        dismissKey = "webclient_battery_optimization",
    ),
    REMOTE_SECRET_BATTERY_OPTIMIZATION(
        titleRes = R.string.problemsolver_title_app_battery_usgae_optimized,
        explanation = { context ->
            context.getString(
                R.string.battery_optimizations_explain,
                context.getString(R.string.remote_secret),
                context.getString(R.string.app_name),
            )
        },
        dismissKey = "remote_secret_battery_optimization",
    ),
}
