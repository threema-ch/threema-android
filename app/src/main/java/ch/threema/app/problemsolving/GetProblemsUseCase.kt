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

import android.content.Context
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.PowermanagerUtil
import ch.threema.app.webclient.services.SessionService
import ch.threema.base.SessionScoped

@SessionScoped
class GetProblemsUseCase(
    private val appContext: Context,
    private val sessionService: SessionService,
    private val preferenceService: PreferenceService,
) {
    fun run(): List<Problem> =
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
