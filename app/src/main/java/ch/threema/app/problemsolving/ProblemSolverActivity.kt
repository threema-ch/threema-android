package ch.threema.app.problemsolving

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.android.buildIntent
import ch.threema.app.R
import ch.threema.app.activities.DisableBatteryOptimizationsActivity
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.time.Instant
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ProblemSolverActivity")

class ProblemSolverActivity : ThreemaToolbarActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val getProblemsUseCase: GetProblemsUseCase by inject()
    private val preferenceService: PreferenceService by inject()
    private val debugLogHelper: DebugLogHelper by inject()

    override fun getLayoutResource() = R.layout.activity_problemsolver

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        return true
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()

        findViewById<NestedScrollView>(R.id.scroll_container).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
            ownPadding = SpacingValues.all(R.dimen.grid_unit_x2),
        )
    }

    override fun onStart() {
        super.onStart()
        updateViews()
    }

    private fun updateViews() {
        lifecycleScope.launch {
            val problems = getProblemsUseCase.call()
            if (problems.isEmpty()) {
                finish()
                return@launch
            }
            updateViews(problems)
        }
    }

    private fun updateViews(problems: List<Problem>) {
        val problemsParentLayout = findViewById<LinearLayout>(R.id.problems_parent) ?: return
        val introText = findViewById<TextView>(R.id.intro_text)
        val infoText = findViewById<View>(R.id.info_text)
        val infoIcon = findViewById<View>(R.id.info_icon)
        if (problems.any { it.solutionType == SolutionType.ToSettings }) {
            introText.isVisible = true
            introText.text = getString(R.string.problemsolver_intro, getString(R.string.app_name))
            infoText.isVisible = true
            infoIcon.isVisible = true
        } else {
            introText.isVisible = false
            infoText.isVisible = false
            infoIcon.isVisible = false
        }

        problemsParentLayout.removeAllViews()
        problems.forEach { problem ->
            addProblemView(problem, problemsParentLayout)
        }
    }

    private fun addProblemView(problem: Problem, parent: ViewGroup) {
        val itemLayout = LayoutInflater.from(this)
            .inflate(R.layout.item_problemsolver, parent, false)

        itemLayout.findViewById<TextView>(R.id.item_title).text = getString(problem.titleRes)
        itemLayout.findViewById<TextView>(R.id.item_explain).text = problem.explanation.get(this)

        with(itemLayout.findViewById<MaterialButton>(R.id.solve_button)) {
            val solutionType = problem.solutionType
            if (solutionType == null) {
                isVisible = false
                return@with
            }
            setOnClickListener { onSolveButtonClicked(problem) }
            when (problem.solutionType) {
                SolutionType.ToSettings -> {
                    setText(R.string.problemsolver_to_settings)
                    setIconResource(R.drawable.ic_settings_outline_24dp)
                }
                is SolutionType.InstantAction -> {
                    setText(problem.solutionType.label)
                    setIconResource(R.drawable.ic_check)
                }
            }
        }

        with(itemLayout.findViewById<View>(R.id.dismiss_button)) {
            if (problem.dismissKey != null) {
                setOnClickListener { onDismissClicked(problem.dismissKey) }
            } else {
                isVisible = false
            }
        }

        parent.addView(itemLayout)
    }

    private fun onSolveButtonClicked(problem: Problem) {
        logger.info("Solve button clicked for {}", problem)
        when (problem) {
            Problem.BACKGROUND_USAGE_RESTRICTED -> {
                val intent = buildSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                startActivity(intent)
            }
            Problem.BACKGROUND_DATA_RESTRICTED -> {
                val intent = buildSettingsIntent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
                startActivity(intent)
            }
            Problem.DEBUG_LOG_STILL_ENABLED -> {
                debugLogHelper.setEnabled(false)
            }
            Problem.NOTIFICATIONS_DISABLED -> {
                val intent = buildIntent {
                    @SuppressLint("InlinedApi")
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    // for Android 7
                    putExtra("app_package", packageName)
                    putExtra("app_uid", applicationInfo.uid)
                    // for Android O
                    putExtra("android.provider.extra.APP_PACKAGE", packageName)
                }
                startActivity(intent)
            }
            Problem.FULLSCREEN_NOTIFICATIONS_DISABLED -> {
                @SuppressLint("InlinedApi")
                val intent = buildSettingsIntent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                startActivity(intent)
            }
            Problem.THREEMA_PUSH_BATTERY_OPTIMIZATION,
            Problem.WEBCLIENT_BATTERY_OPTIMIZATION,
            Problem.REMOTE_SECRET_BATTERY_OPTIMIZATION,
            -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val intent = buildActivityIntent<DisableBatteryOptimizationsActivity>(this) {
                    putExtra(
                        DisableBatteryOptimizationsActivity.EXTRA_NAME,
                        getString(R.string.threema_push),
                    )
                    putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, R.string.cancel)
                    putExtra(DisableBatteryOptimizationsActivity.EXTRA_DISABLE_RATIONALE, true)
                }
                startActivity(intent)
            } else {
                val intent = buildSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                startActivity(intent)
            }
            Problem.DEBUG_LOG_FORCE_ENABLED -> Unit
        }
        updateViews()
    }

    private fun buildSettingsIntent(action: String): Intent =
        buildIntent {
            this.action = action
            data = "package:$packageName".toUri()
        }

    private fun onDismissClicked(problemKey: String) {
        logger.info("Problem {} dismissed", problemKey)
        preferenceService.setProblemDismissed(problemKey, Instant.now())
        updateViews()
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ProblemSolverActivity>(context)
    }
}
