package ch.threema.app.ui

import android.os.Handler
import android.view.View
import android.widget.FrameLayout
import ch.threema.app.utils.AnimationUtil
import ch.threema.app.utils.RuntimeUtil
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.button.MaterialButton

/**
 * This class manages the quick scroll buttons in a conversation and their timeouts; it also makes sure only one type is shown at once
 */
class ScrollButtonManager(
    private val upButton: MaterialButton,
    private val downButton: FrameLayout,
    private val downBadgeDrawable: BadgeDrawable,
) {
    private val buttonHandler = Handler()
    private val buttonTask = Runnable {
        RuntimeUtil.runOnUiThread {
            hideAllButtons()
        }
    }

    /**
     * Show a button of requested type and a badge if a count is provided. Hide other buttons.
     */
    fun showButton(type: Int, count: Int) {
        buttonHandler.removeCallbacks(buttonTask)
        when (type) {
            TYPE_UP -> {
                downButton.visibility = View.GONE
                downBadgeDrawable.isVisible = false
                if (upButton.visibility != View.VISIBLE) {
                    AnimationUtil.setFadingVisibility(upButton, View.VISIBLE)
                }
            }

            TYPE_DOWN -> {
                upButton.visibility = View.GONE
                if (downButton.visibility != View.VISIBLE || downBadgeDrawable.number != count) {
                    AnimationUtil.setFadingVisibility(downButton, View.VISIBLE)
                    downButton.post {
                        if (count > 0) {
                            downBadgeDrawable.number = count
                            downBadgeDrawable.isVisible = true
                        } else {
                            downBadgeDrawable.isVisible = false
                        }
                    }
                }
            }

            else -> {}
        }
        if (count <= 0) {
            buttonHandler.postDelayed(buttonTask, SCROLLBUTTON_VIEW_TIMEOUT)
        }
    }

    fun hideButton(type: Int) {
        buttonHandler.removeCallbacks(buttonTask)
        when (type) {
            TYPE_UP -> if (upButton.visibility != View.GONE) {
                AnimationUtil.setFadingVisibility(upButton, View.GONE)
            }

            TYPE_DOWN -> if (downButton.visibility != View.GONE) {
                AnimationUtil.setFadingVisibility(downButton, View.GONE)
            }

            else -> {}
        }
    }

    fun hideAllButtons() {
        hideButton(TYPE_DOWN)
        hideButton(TYPE_UP)
    }

    companion object {
        const val SCROLLBUTTON_VIEW_TIMEOUT = 3000L
        const val TYPE_UP = 1
        const val TYPE_DOWN = 2
    }
}
