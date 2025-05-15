/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.PowermanagerUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.time.Instant

private val logger = LoggingUtil.getThreemaLogger("ProblemSolverActivity")

class ProblemSolverActivity : ThreemaToolbarActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _: ActivityResult? -> recreate() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean -> recreate() }

    private class Problem(
        // title text used in problem solver box
        val title: Int,
        // explanation text used in problem solver box
        val explanation: Int,
        // the action to call for fixing the problem
        val intentAction: String?,
        // the check to determine if the problem exists
        val check: Boolean,
    )

    @SuppressLint("InlinedApi")
    private val problems = arrayOf(
        Problem(
            title = R.string.problemsolver_title_background,
            explanation = R.string.problemsolver_explain_background,
            intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            check = ConfigUtils.isBackgroundRestricted(ThreemaApplication.getAppContext()),
        ),
        Problem(
            title = R.string.problemsolver_title_background_data,
            explanation = R.string.problemsolver_explain_background_data,
            intentAction = Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
            check = ConfigUtils.isBackgroundDataRestricted(ThreemaApplication.getAppContext(), false),
        ),
        Problem(
            title = R.string.problemsolver_title_notifications,
            explanation = R.string.problemsolver_explain_notifications,
            intentAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            check = ConfigUtils.isNotificationsDisabled(ThreemaApplication.getAppContext()),
        ),
        Problem(
            title = R.string.problemsolver_title_fullscreen_notifications,
            explanation = R.string.problemsolver_explain_fullscreen_notifications,
            intentAction = Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            check = ConfigUtils.isFullScreenNotificationsDisabled(ThreemaApplication.getAppContext()),
        ),
        Problem(
            title = R.string.problemsolver_title_app_battery_usgae_optimized,
            explanation = R.string.problemsolver_explain_app_battery_usgae_optimized,
            intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            check = ThreemaApplication.getServiceManager()?.preferenceService?.useThreemaPush() ?: false &&
                !PowermanagerUtil.isIgnoringBatteryOptimizations(ThreemaApplication.getAppContext()),
        ),
        Problem(
            title = R.string.problemsolver_title_android_version_deprecated,
            explanation = R.string.problemsolver_explain_android_version_deprecated,
            intentAction = null,
            check = Build.VERSION.SDK_INT < Build.VERSION_CODES.N &&
                ThreemaApplication.getServiceManager()?.preferenceService?.lastDeprecatedAndroidVersionWarningDismissed == null,
        ),
    )

    override fun getLayoutResource(): Int {
        return R.layout.activity_problemsolver
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        val success = super.initActivity(savedInstanceState)

        findViewById<TextView>(R.id.intro_text).text =
            getString(R.string.problemsolver_intro, getString(R.string.app_name))

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        showProblems()

        return success
    }

    private fun showProblems() {
        val problemsParentLayout = findViewById<LinearLayout>(R.id.problems_parent)
        val problems = problems.filter { it.check }
        for (problem in problems) {
            val itemLayout: View = LayoutInflater.from(this).inflate(
                /* resource = */
                R.layout.item_problemsolver,
                /* root = */
                problemsParentLayout,
                /* attachToRoot = */
                false,
            )
            itemLayout.findViewById<TextView>(R.id.item_title).text =
                getString(problem.title)
            itemLayout.findViewById<TextView>(R.id.item_explain).text =
                getString(problem.explanation)
            val settingsButton = itemLayout.findViewById<MaterialButton>(R.id.item_button)
            settingsButton.setOnClickListener { onProblemClick(problem) }
            if (problem.intentAction == null) {
                settingsButton.setText(R.string.ok)
                settingsButton.icon = null
            }
            problemsParentLayout.addView(itemLayout)
        }

        if (problems.singleOrNull()?.title == R.string.problemsolver_title_android_version_deprecated) {
            findViewById<View>(R.id.intro_text).isVisible = false
            findViewById<View>(R.id.info_icon).isVisible = false
            findViewById<View>(R.id.info_text).isVisible = false
        }

        if (problems.isEmpty()) {
            finish()
        }
    }

    private fun onProblemClick(problem: Problem) {
        if (problem.title == R.string.problemsolver_title_android_version_deprecated) {
            ThreemaApplication.requireServiceManager().preferenceService.setLastDeprecatedAndroidVersionWarningDismissed(Instant.now())
            recreate()
            return
        }

        val action = problem.intentAction!!
        val intent: Intent

        if (problem.title == R.string.problemsolver_title_app_battery_usgae_optimized &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            intent = Intent(this, DisableBatteryOptimizationsActivity::class.java)
            intent.putExtra(
                DisableBatteryOptimizationsActivity.EXTRA_NAME,
                getString(R.string.threema_push),
            )
            intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, R.string.cancel)
            intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_DISABLE_RATIONALE, true)
        } else {
            intent = Intent(action)
            if (problem.title == R.string.problemsolver_title_notifications) {
                // for Android 5-7
                intent.putExtra("app_package", packageName)
                intent.putExtra("app_uid", applicationInfo.uid)
                // for Android O
                intent.putExtra("android.provider.extra.APP_PACKAGE", packageName)
            } else {
                intent.data = Uri.parse("package:$packageName")
            }
        }

        settingsLauncher.launch(intent)
    }
}
