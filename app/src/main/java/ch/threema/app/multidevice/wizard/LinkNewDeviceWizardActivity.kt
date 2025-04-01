/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import ch.threema.app.R
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.shape.MaterialShapeDrawable

private val logger = LoggingUtil.getThreemaLogger("LinkNewDeviceWizardActivity")

class LinkNewDeviceWizardActivity : ThreemaActivity() {
    companion object {
        const val ACTIVITY_RESULT_EXTRA_FAILURE_REASON: String = "activityResultFailureReason"
    }

    private var currentFragment: Fragment? = null
    private val viewModel: LinkNewDeviceWizardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.currentFragment.observe(this) { fragment ->
            currentFragment = fragment
        }

        viewModel.nextFragment.observe(this) { newFragmentClass ->
            switchFragment(currentFragment, newFragmentClass)
        }

        setContentView(R.layout.activity_link_new_device_bottom_sheet)

        window.statusBarColor = resources.getColor(R.color.attach_status_bar_color_collapsed)

        val bottomSheetLayout = findViewById<ConstraintLayout?>(R.id.bottom_sheet)
        if (bottomSheetLayout != null) {
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
            val targetPeekHeight =
                resources.displayMetrics.heightPixels - ConfigUtils.getActionBarSize(this)
            ObjectAnimator.ofInt(bottomSheetBehavior, "peekHeight", targetPeekHeight)
                .apply {
                    duration = 200
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}
                        override fun onAnimationEnd(animation: Animator) {
                            if (savedInstanceState == null) {
                                // start with qr scanner
                                switchFragment(null, LinkNewDeviceScanQrFragment::class.java)
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}
                    }
                    )
                    start()
                }
            bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> finishWithResult()

                        BottomSheetBehavior.STATE_EXPANDED -> {
                            findViewById<View>(R.id.drag_handle).visibility = View.INVISIBLE
                            val background: Drawable = bottomSheetLayout.background
                            if (background is MaterialShapeDrawable) {
                                window.statusBarColor =
                                    background.resolvedTintColor
                            } else {
                                window.statusBarColor =
                                    resources.getColor(R.color.attach_status_bar_color_expanded)
                            }
                        }

                        BottomSheetBehavior.STATE_SETTLING -> findViewById<View>(R.id.drag_handle).visibility =
                            View.VISIBLE

                        BottomSheetBehavior.STATE_DRAGGING -> window.statusBarColor =
                            resources.getColor(R.color.attach_status_bar_color_collapsed)

                        else -> {}
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // we don't care about sliding events
                }
            })
        }
    }

    /**
     * Remove previous fragment and show next fragment with animation
     * If there is no more fragment to show, finish the activity signalling success
     */
    @UiThread
    private fun switchFragment(
        previousFragment: Fragment?,
        nextFragmentClass: Class<out Fragment>?
    ) {
        if (previousFragment == null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fast_fade_in,
                    R.anim.fast_fade_out,
                    R.anim.fast_fade_in,
                    R.anim.fast_fade_out
                )
                .add(R.id.fragment_container, LinkNewDeviceScanQrFragment::class.java, null, null)
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
                    R.anim.slide_out_right_short
                )
                .remove(previousFragment)
                .add(R.id.fragment_container, nextFragmentClass, null, null)
                .commitNow()
        }
    }

    private fun finishWithResult() {
        if (viewModel.success) {
            logger.debug("Finishing after successful linking")
            setResult(Activity.RESULT_OK)
        } else {
            logger.debug(
                "Finishing after cancel or linking error. Reason = {}",
                viewModel.failureReason ?: "user canceled"
            )
            val resultIntent =
                Intent().putExtra(ACTIVITY_RESULT_EXTRA_FAILURE_REASON, viewModel.failureReason)
            setResult(Activity.RESULT_CANCELED, resultIntent)
        }
        finish()
    }

    override fun finish() {
        viewModel.cancelAllJobs()

        super.finish()
        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out)
    }
}
