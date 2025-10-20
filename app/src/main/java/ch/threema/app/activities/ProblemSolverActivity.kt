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
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.PowermanagerUtil
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.webclient.services.SessionService
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("ProblemSolverActivity")

class ProblemSolverActivity : ThreemaToolbarActivity() {
    private val sessionService: SessionService by inject()

    init {
        logScreenVisibility(logger)
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _: ActivityResult? -> recreate() }

    private class Problem(
        // title text used in problem solver box
        val title: Int,
        // explanation text used in problem solver box
        val explanation: String,
        // the action to call for fixing the problem
        val intentAction: String,
        // the check to determine if the problem exists
        val check: Boolean,
    )

    private val problems by lazy {
        arrayOf(
            Problem(
                title = R.string.problemsolver_title_background,
                explanation = getString(R.string.problemsolver_explain_background),
                intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                check = ConfigUtils.isBackgroundRestricted(this),
            ),
            Problem(
                title = R.string.problemsolver_title_background_data,
                explanation = getString(R.string.problemsolver_explain_background_data),
                intentAction = Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                check = ConfigUtils.isBackgroundDataRestricted(this),
            ),
            @SuppressLint("InlinedApi")
            Problem(
                title = R.string.problemsolver_title_notifications,
                explanation = getString(R.string.problemsolver_explain_notifications),
                intentAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                check = ConfigUtils.isNotificationsDisabled(this),
            ),
            @SuppressLint("InlinedApi")
            Problem(
                title = R.string.problemsolver_title_fullscreen_notifications,
                explanation = getString(R.string.problemsolver_explain_fullscreen_notifications),
                intentAction = Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                check = ConfigUtils.isFullScreenNotificationsDisabled(this),
            ),
            Problem(
                title = R.string.problemsolver_title_app_battery_usgae_optimized,
                explanation = getString(R.string.problemsolver_explain_app_battery_usgae_optimized),
                intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                check = ThreemaApplication.getServiceManager()?.preferenceService?.useThreemaPush() ?: false &&
                    !PowermanagerUtil.isIgnoringBatteryOptimizations(this),
            ),
            Problem(
                title = R.string.problemsolver_title_app_battery_usgae_optimized,
                explanation = getString(
                    R.string.battery_optimizations_explain,
                    getString(R.string.webclient),
                    getString(R.string.app_name),
                ),
                intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                check = sessionService.hasRunningSessions() && !PowermanagerUtil.isIgnoringBatteryOptimizations(this),
            ),
        )
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_problemsolver
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        findViewById<TextView>(R.id.intro_text).text =
            getString(R.string.problemsolver_intro, getString(R.string.app_name))

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        showProblems()

        return true
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()

        findViewById<NestedScrollView>(R.id.scroll_container).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
            ownPadding = SpacingValues.all(R.dimen.grid_unit_x2),
        )
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
            itemLayout.findViewById<TextView>(R.id.item_title).text = getString(problem.title)
            itemLayout.findViewById<TextView>(R.id.item_explain).text = problem.explanation
            val settingsButton = itemLayout.findViewById<MaterialButton>(R.id.item_button)
            settingsButton.setOnClickListener { onProblemClick(problem) }
            problemsParentLayout.addView(itemLayout)
        }

        if (problems.isEmpty()) {
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
                getString(R.string.threema_push),
            )
            intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, R.string.cancel)
            intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_DISABLE_RATIONALE, true)
        } else {
            intent = Intent(action)
            if (problem.title == R.string.problemsolver_title_notifications) {
                // for Android 7
                intent.putExtra("app_package", packageName)
                intent.putExtra("app_uid", applicationInfo.uid)
                // for Android O
                intent.putExtra("android.provider.extra.APP_PACKAGE", packageName)
            } else {
                intent.data = "package:$packageName".toUri()
            }
        }

        settingsLauncher.launch(intent)
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ProblemSolverActivity>(context)
    }
}
