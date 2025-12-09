/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.problemsolving

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.R

/**
 * @param dismissKey If defined, the user can dismiss this problem to keep it from being reported. The key is used to persist this decision.
 * @param isSolvable Whether this problem has a way to resolve it. Set to false for problems that the user should be made aware of but are not actionable.
 */
@Immutable
enum class Problem(
    @StringRes
    val titleRes: Int,
    val explanation: ResolvableString,
    val dismissKey: String? = null,
    val isSolvable: Boolean = true,
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
}
