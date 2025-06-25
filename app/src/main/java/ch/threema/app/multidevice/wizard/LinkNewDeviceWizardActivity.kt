/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.multidevice.wizard

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceFragment
import ch.threema.app.multidevice.wizard.steps.LinkNewDevicePFSInfoFragment
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceResultFragment
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceScanQrFragment
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("LinkNewDeviceWizardActivity")

class LinkNewDeviceWizardActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    private var currentFragment: Fragment? = null
    private val viewModel: LinkNewDeviceWizardViewModel by viewModels()

    private var reallyCancelDialog: WeakReference<AlertDialog>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link_new_device_bottom_sheet)

        onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (reallyCancelDialog?.get() == null) {
                        onUserRequestedCancel()
                    } else {
                        reallyCancelDialog?.get()?.dismiss()
                    }
                }
            },
        )

        viewModel.currentFragment.observe(this) { fragment ->
            currentFragment = fragment
        }

        viewModel.nextFragment.observe(this) { newFragmentClass: Class<out Fragment>? ->
            switchFragment(currentFragment, newFragmentClass)

            // If we are switching to the success screen and the dialog which asks
            // the user if he really wants to cancel is currently still visible (not answered),
            // we close it and re-open the bottom sheet
            if (newFragmentClass == LinkNewDeviceResultFragment::class.java && reallyCancelDialog?.get() != null) {
                reallyCancelDialog?.get()?.dismiss()
                reShowBottomSheet()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                viewModel.onUserRequestedCancel.collect {
                    this@LinkNewDeviceWizardActivity.onUserRequestedCancel()
                }
            }
        }

        val bottomSheetLayout = findViewById<ConstraintLayout?>(R.id.bottom_sheet)
        if (bottomSheetLayout != null) {
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)

            determinePeekHeight { targetPeekHeightPx ->

                ObjectAnimator.ofInt(bottomSheetBehavior, "peekHeight", targetPeekHeightPx)
                    .apply {
                        duration = 200
                        addListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) {}
                                override fun onAnimationEnd(animation: Animator) {
                                    if (savedInstanceState == null) {
                                        switchFragment(null, null)
                                    }
                                }

                                override fun onAnimationCancel(animation: Animator) {}
                                override fun onAnimationRepeat(animation: Animator) {}
                            },
                        )
                        start()
                    }
            }

            val dragHandle = findViewById<BottomSheetDragHandleView>(R.id.drag_handle)
            bottomSheetBehavior.addBottomSheetCallback(
                object : BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> onUserRequestedCancel()

                            BottomSheetBehavior.STATE_EXPANDED -> {
                                dragHandle.visibility = View.INVISIBLE
                            }

                            BottomSheetBehavior.STATE_SETTLING -> {
                                dragHandle.visibility = View.VISIBLE
                            }

                            else -> {}
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // we don't care about sliding events
                    }
                },
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val bottomSheetLayout = findViewById<ConstraintLayout?>(R.id.bottom_sheet)
        if (bottomSheetLayout != null) {
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
            determinePeekHeight { targetPeekHeightPx ->
                bottomSheetBehavior.setPeekHeight(targetPeekHeightPx)
            }
        }
        super.onConfigurationChanged(newConfig)
    }

    private fun determineInitialFragment(): Class<out LinkNewDeviceFragment> =
        if (ThreemaApplication.getServiceManager()?.multiDeviceManager?.isMultiDeviceActive == true) {
            LinkNewDeviceScanQrFragment::class.java
        } else {
            LinkNewDevicePFSInfoFragment::class.java
        }

    /**
     * Remove previous fragment and show next fragment with animation
     * If there is no more fragment to show, finish the activity signalling success
     */
    @UiThread
    private fun switchFragment(
        previousFragment: Fragment?,
        nextFragmentClass: Class<out Fragment>?,
    ) {
        if (previousFragment == null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fast_fade_in,
                    R.anim.fast_fade_out,
                    R.anim.fast_fade_in,
                    R.anim.fast_fade_out,
                )
                .add(R.id.fragment_container, determineInitialFragment(), null, null)
                .commit()
        } else if (nextFragmentClass == null) {
            // no more fragment to show - quit activity
            finishWithResult()
        } else {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right_short,
                    R.anim.slide_out_left_short,
                    R.anim.slide_in_left_short,
                    R.anim.slide_out_right_short,
                )
                .remove(previousFragment)
                .add(R.id.fragment_container, nextFragmentClass, null, null)
                .commitNow()
        }
    }

    private fun reShowBottomSheet() {
        findViewById<ConstraintLayout?>(R.id.bottom_sheet)?.let { bottomSheetLayout ->
            BottomSheetBehavior.from(bottomSheetLayout).setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }

    private fun finishWithResult() {
        when (viewModel.linkingResult.value) {
            is LinkingResult.Success -> {
                logger.info("Finishing after successful linking")
                setResult(Activity.RESULT_OK)
            }

            is LinkingResult.Failure -> {
                logger.warn("Finishing after linking error")
                setResult(Activity.RESULT_CANCELED)
            }

            null -> {
                logger.info("Finishing after user cancelled")
                setResult(Activity.RESULT_CANCELED)
            }
        }
        finish()
    }

    private fun onUserRequestedCancel() {
        if (
            currentFragment != null &&
            currentFragment is LinkNewDevicePFSInfoFragment ||
            currentFragment is LinkNewDeviceScanQrFragment ||
            currentFragment is LinkNewDeviceResultFragment
        ) {
            finishWithResult()
            return
        }

        val alertDialog = MaterialAlertDialogBuilder(this).apply {
            setTitle(getString(R.string.device_linking_cancel_dialog_title))
            setMessage(getString(R.string.device_linking_cancel_dialog_message))
            setCancelable(false)
            setNegativeButton(getString(R.string.device_linking_cancel_dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
                finishWithResult()
            }
            setPositiveButton(getString(R.string.device_linking_cancel_dialog_continue)) { dialog, _ ->
                dialog.dismiss()
                reShowBottomSheet()
            }
        }.show()
        reallyCancelDialog = WeakReference(alertDialog)
    }

    override fun finish() {
        viewModel.cancelAllJobs()

        super.finish()
        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out)
    }

    private fun determinePeekHeight(onAvailable: (Int) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.parent_layout)) { view: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(systemBars() or displayCutout())
            val toolbarHeightPx: Int = ConfigUtils.getActionBarSize(this)
            val extraSpacingPx: Int = resources.getDimensionPixelSize(R.dimen.grid_unit_x2)
            val windowHeight: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.height()
            } else {
                // It seems that this height value already excludes the vertical device insets
                resources.displayMetrics.heightPixels
            }
            var peekHeight = windowHeight - toolbarHeightPx - extraSpacingPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                peekHeight -= insets.top + insets.bottom
            }
            view.setOnApplyWindowInsetsListener(null)
            onAvailable(peekHeight.coerceAtLeast(0))
            windowInsets
        }
    }
}
