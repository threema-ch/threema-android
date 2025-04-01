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
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.core.graphics.drawable.DrawableCompat
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import kotlin.math.sqrt

internal class ThumbDatePopupBackground(context: Context) : Drawable() {
    private val paint: Paint = Paint()
    private val paddingStart: Int
    private val paddingEnd: Int
    private val paddingTopBottom: Int
    private val path = Path()
    private val tempMatrix = Matrix()

    init {
        paint.isAntiAlias = true
        paint.color = ConfigUtils.getColorFromAttribute(context, R.attr.colorSecondaryContainer)
        paint.style = Paint.Style.FILL
        paddingStart =
            context.resources.getDimensionPixelSize(R.dimen.thumb_date_popup_padding_start);
        paddingEnd = context.resources.getDimensionPixelSize(R.dimen.thumb_date_popup_padding_end);
        paddingTopBottom =
            context.resources.getDimensionPixelSize(R.dimen.thumb_date_popup_padding_top_bottom);
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean {
        updatePath()
        return true
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun isAutoMirrored(): Boolean {
        return true
    }

    private fun needMirroring(): Boolean {
        return DrawableCompat.getLayoutDirection(this) == View.LAYOUT_DIRECTION_RTL
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun onBoundsChange(bounds: Rect) {
        updatePath()
    }

    private fun updatePath() {
        path.reset()
        val bounds = bounds
        var width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val r = height / 2
        val sqrt2 = sqrt(2.0).toFloat()
        // Ensure we are convex.
        width = (r + sqrt2 * r).coerceAtLeast(width)
        val o1X = width - sqrt2 * r

        pathArcTo(path, r, r, r, 90f, 180f)
        pathArcTo(path, o1X, r, r, -90f, 45f)
        pathArcTo(path, o1X, r, r, -45f, 90f)
        pathArcTo(path, o1X, r, r, 45f, 45f)
        path.close()
        if (needMirroring()) {
            tempMatrix.setScale(-1f, 1f, width / 2, 0f)
        } else {
            tempMatrix.reset()
        }
        tempMatrix.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        path.transform(tempMatrix)
    }

    private fun pathArcTo(
        path: Path, centerX: Float, centerY: Float, radius: Float,
        startAngle: Float, sweepAngle: Float
    ) {
        path.arcTo(
            centerX - radius, centerY - radius, centerX + radius, centerY + radius,
            startAngle, sweepAngle, false
        )
    }

    override fun getPadding(padding: Rect): Boolean {
        if (needMirroring()) {
            padding[paddingEnd, paddingTopBottom, paddingStart] = paddingTopBottom
        } else {
            padding[paddingStart, paddingTopBottom, paddingEnd] = paddingTopBottom
        }
        return true
    }

    override fun getOutline(outline: Outline) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !path.isConvex) {
            // The outline path must be convex before Q, but we may run into floating point error
            // caused by calculation involving sqrt(2) or OEM implementation difference, so in this
            // case we just omit the shadow instead of crashing.
            super.getOutline(outline)
            return
        }
        outline.setConvexPath(path)
    }
}
