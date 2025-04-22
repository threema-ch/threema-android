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

package ch.threema.app.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import ch.threema.app.R
import com.google.android.material.badge.BadgeDrawable

class PermissionIconView : FrameLayout {
    /**
     * This indicates the state
     */
    enum class PermissionIconState {
        /**
         * The permission has been granted. In this state, there is no further action required by
         * the user for this permission.
         */
        GRANTED,

        /**
         * The permission is optional and the user decided to deny it. There is no further action
         * required by the user.
         */
        OPTIONAL_AND_DENIED,

        /**
         * The permission is either not granted but required, or the user did not yet decide to
         * grant or deny the optional permission. For optional permissions we need an action from
         * the user to grant or deny the permission. For required permission we need the user to
         * grant the permission.
         */
        REQUIRED_OR_UNDECIDED,
    }

    private val iconView: ImageView
    private val badge: BadgeDrawable

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_permission_icon, this)

        iconView = findViewById(R.id.permission_icon_view)

        badge = initializeBadge()
    }

    /**
     * Set the view highlighted. Only one view should be highlighted at the same time. The
     * highlighted view is shown larger than the other views.
     */
    fun setHighlighted(isHighlighted: Boolean, animate: Boolean) {
        val duration = if (animate) {
            resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        } else {
            0L
        }
        if (isHighlighted) {
            animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(1f)
                .setDuration(duration)
                .start()
        } else {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0.5f)
                .setDuration(duration)
                .start()
        }
    }

    /**
     * Update the badge depending on the state.
     */
    fun updateBadge(state: PermissionIconState) {
        badge.backgroundColor = ContextCompat.getColor(
            context,
            when (state) {
                PermissionIconState.GRANTED -> R.color.material_green
                PermissionIconState.OPTIONAL_AND_DENIED -> R.color.material_orange
                PermissionIconState.REQUIRED_OR_UNDECIDED -> R.color.material_red
            },
        )
    }

    /**
     * Set the permission icon.
     */
    fun setIcon(@DrawableRes icon: Int) {
        this.iconView.setImageResource(icon)
    }

    private fun initializeBadge(): BadgeDrawable {
        val backgroundFrame = findViewById<FrameLayout>(R.id.background_frame)

        val badge = BadgeDrawable.create(context)
        badge.badgeGravity = BadgeDrawable.TOP_END
        badge.backgroundColor = ContextCompat.getColor(context, R.color.material_red)
        badge.setVisible(true, true)

        doOnGlobalLayout {
            // Set position of back to the foreground of the background frame. From API 23 this
            // could also be set to the background image.
            badge.bounds = backgroundFrame.getDrawingRect()
            badge.updateBadgeCoordinates(backgroundFrame)
            backgroundFrame.foreground = badge
        }

        return badge
    }

    private fun doOnGlobalLayout(action: () -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                action()
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }
}

/**
 * Return the visible drawing bounds of the view.
 */
private fun View.getDrawingRect(): Rect {
    val rect = Rect()
    this.getDrawingRect(rect)
    return rect
}
