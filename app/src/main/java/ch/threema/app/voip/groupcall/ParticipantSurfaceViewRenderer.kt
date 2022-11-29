/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.voip.groupcall

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import kotlinx.coroutines.*
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

/**
 * A SurfaceViewRenderer that keeps track of the number of frames displayed.
 */
class ParticipantSurfaceViewRenderer : SurfaceViewRenderer {
    private var enableFramesThreshold = -1
    private var disableFramesThreshold = -1

    private var frameCount = 0
    private var lastFrameCount = 0

    private enum class VideoState {
        /* Video is currently shown */
        SHOWN,
        /* Video view is frozen and therefore the avatar is shown */
        FROZEN,
        /* There is no video stream for this participant */
        INACTIVE
    }
    private var state: VideoState = VideoState.INACTIVE

    private var videoActive = false
    private var skipNextFrameCheck = false

    private var avatarView: View? = null
    private val animationDuration = 1000L

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onFrame(frame: VideoFrame?) {
        super.onFrame(frame)

        frameCount++
    }

    /**
     * Set visibility to VISIBLE after at least one frame has arrived and skip next frame check that
     * could happen very soon when not enough frames have arrived.
     */
    @AnyThread
    fun enableVideo() {
        videoActive = true
        // Update last frame count because there may have arrived some frames of the last video stream
        lastFrameCount = frameCount
        CoroutineScope(Dispatchers.Default).launch {
            while (lastFrameCount == frameCount && videoActive) {
                delay(50)
            }
            if (videoActive) {
                withContext(Dispatchers.Main) {
                    skipNextFrameCheck = true
                    show()
                }
            }
        }
    }

    /**
     * Set visibility to GONE and stop listening for new arriving frames.
     */
    @AnyThread
    fun disableVideo() {
        videoActive = false
        CoroutineScope(Dispatchers.Main).launch {
            hide()
        }
    }

    /**
     * Set the number of frames that will be expected between calls of [updateFrozenState].
     * @param enableFramesThreshold the number of frames needed to enable a frozen view renderer
     * @param disableFramesThreshold the number of frames needed to keep the view renderer visible
     */
    fun setNumFramesNeeded(enableFramesThreshold: Int, disableFramesThreshold: Int) {
        this.enableFramesThreshold = enableFramesThreshold
        this.disableFramesThreshold = disableFramesThreshold
    }

    /**
     * Set the avatar view corresponding to this surface renderer. When this surface renderer gets
     * frozen (invisible), it animates the avatar view for a smoother transition. Note that this is
     * needed because the surface renderer does not support transparency.
     */
    fun setAvatarView(view: View) {
        avatarView = view
    }

    /**
     * Update the visibility based on the amount of frames shown. If less than
     * [disableFramesThreshold] are counted since the last call of this method, the view considers
     * itself as frozen and hides itself. If the view is frozen and [enableFramesThreshold] or more
     * frames have been received since the last execution of this method, this view becomes active
     * (and therefore visible). If this view is inactive (video disabled), this method does not
     * change the view's visibility.
     */
    @UiThread
    fun updateFrozenState() {
        if (skipNextFrameCheck) {
            lastFrameCount = frameCount
            skipNextFrameCheck = false
            return
        }

        if (state != VideoState.INACTIVE) {
            // If the visibility is not GONE, video is active. Therefore we update the visibility,
            // if there hasn't been 'numFramesNeeded' new frames since the last check.
            val framesReceived = frameCount - lastFrameCount

            if (state == VideoState.SHOWN && framesReceived < disableFramesThreshold) {
                freeze()
            } else if (state == VideoState.FROZEN && framesReceived >= enableFramesThreshold) {
                unfreeze()
            }
        }
        lastFrameCount = frameCount
    }

    private fun show() {
        visibility = VISIBLE
        avatarView?.visibility = INVISIBLE
        state = VideoState.SHOWN
    }

    private fun hide() {
        visibility = GONE
        avatarView?.visibility = VISIBLE
        state = VideoState.INACTIVE
    }

    private fun unfreeze() {
        if (state == VideoState.SHOWN) {
            return
        }

        visibility = VISIBLE

        avatarView?.let {
            it.alpha = 1f
            it.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .withEndAction { it.visibility = INVISIBLE; it.alpha = 1f }
                .start()
        }

        state = VideoState.SHOWN
    }

    private fun freeze() {
        if (state == VideoState.FROZEN) {
            return
        }

        avatarView?.let {
            it.alpha = 0f
            it.visibility = VISIBLE
            it.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .withEndAction { visibility = INVISIBLE }
                .start()
        }

        state = VideoState.FROZEN
    }
}
