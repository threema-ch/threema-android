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
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.PowermanagerUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ProblemSolverActivity : ThreemaToolbarActivity() {
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult? -> recreate() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> recreate() }

    private class Problem(
        // title text used in problem solver box
        val title: Int,
        // explanation text used in problem solver box
        val explanation: Int,
        // the action to call for fixing the problem
        val intentAction: String,
        // the check to determine if the problem exists
        val check: Boolean
    )

    @SuppressLint("InlinedApi")
    private val problems = arrayOf(
        Problem(
            R.string.problemsolver_title_background,
            R.string.problemsolver_explain_background,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ConfigUtils.isBackgroundRestricted(ThreemaApplication.getAppContext())
        ),

        Problem(
            R.string.problemsolver_title_background_data,
            R.string.problemsolver_explain_background_data,
            Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
            ConfigUtils.isBackgroundDataRestricted(ThreemaApplication.getAppContext(), false)
        ),

        Problem(
            R.string.problemsolver_title_notifications,
            R.string.problemsolver_explain_notifications,
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            ConfigUtils.isNotificationsDisabled(ThreemaApplication.getAppContext()),
        ),

        Problem(
            R.string.problemsolver_title_fullscreen_notifications,
            R.string.problemsolver_explain_fullscreen_notifications,
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            ConfigUtils.isFullScreenNotificationsDisabled(ThreemaApplication.getAppContext()),
        ),

        Problem(
            R.string.problemsolver_title_app_battery_usgae_optimized,
            R.string.problemsolver_explain_app_battery_usgae_optimized,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ThreemaApplication.getServiceManager()?.preferenceService?.useThreemaPush() ?: false && !PowermanagerUtil.isIgnoringBatteryOptimizations(
                ThreemaApplication.getAppContext()
            )
        )
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
        var problemCount = 0
        val problemsParentLayout = findViewById<LinearLayout>(R.id.problems_parent)
        for (problem in problems) {
            if (problem.check) {
                val itemLayout: View = LayoutInflater.from(this).inflate(
                    R.layout.item_problemsolver,
                    problemsParentLayout, false
                )
                itemLayout.findViewById<TextView>(R.id.item_title).text =
                    getString(problem.title)
                itemLayout.findViewById<TextView>(R.id.item_explain).text =
                    getString(problem.explanation)
                val settingsButton = itemLayout.findViewById<MaterialButton>(R.id.item_button)
                settingsButton.setOnClickListener { onProblemClick(problem) }
                problemsParentLayout.addView(itemLayout)
                problemCount++
            }
        }

        if (problemCount == 0) {
            finish()
        }
    }

    private fun onProblemClick(problem: Problem) {
        val action = problem.intentAction
        val intent: Intent

        if (problem.title == R.string.problemsolver_title_app_battery_usgae_optimized &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            intent = Intent(this, DisableBatteryOptimizationsActivity::class.java)
            intent.putExtra(
                DisableBatteryOptimizationsActivity.EXTRA_NAME,
                getString(R.string.threema_push)
            );
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
