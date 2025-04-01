/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.glide.AvatarOptions
import ch.threema.app.services.ContactService
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.ParticipantSurfaceViewRenderer
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.base.utils.LoggingUtil
import com.bumptech.glide.RequestManager
import kotlinx.coroutines.*
import org.webrtc.EglBase

private val logger = LoggingUtil.getThreemaLogger("GroupCallParticipantsAdapter")

@UiThread
class GroupCallParticipantsAdapter(
    private val contactService: ContactService,
    private val gutterPx: Int,
    private val requestManager: RequestManager,
) : RecyclerView.Adapter<GroupCallParticipantsAdapter.GroupCallParticipantViewHolder>() {
    private val participants: MutableList<Participant> = mutableListOf()

    private var localParticipantViewHolder: GroupCallParticipantViewHolder? = null
    private val activeViewHolders: MutableSet<GroupCallParticipantViewHolder> = mutableSetOf()
    private val viewHolders: MutableSet<GroupCallParticipantViewHolder> = mutableSetOf()

    lateinit var eglBase: EglBase

    var isPortrait = true
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, participants.size)
            }
        }
    private val orientation: Orientation
        get() = if (isPortrait) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }

    private val frozenStateUpdates = CoroutineScope(Dispatchers.Main).launch {
        delay(UPDATE_FROZEN_INTERVAL_MS * 4)

        while (true) {
            // Don't update frozen state for local participants or participants without active camera
            // as it may be confusing if changes happen without user interaction
            activeViewHolders.filter {
                it.participant !is LocalParticipant && it.participant?.cameraActive ?: false
            }.forEach {
                it.videoView.updateFrozenState()
            }
            delay(UPDATE_FROZEN_INTERVAL_MS)
        }
    }

    @UiThread
    class GroupCallParticipantViewHolder(
        eglBase: EglBase,
        itemView: View,
        val parent: ViewGroup
    ) : RecyclerView.ViewHolder(itemView) {

        var isAttachedToWindow = false

        val name: TextView = itemView.findViewById(R.id.participant_name)
        val avatar: ImageView = itemView.findViewById(R.id.participant_avatar)
        val info: ConstraintLayout = itemView.findViewById(R.id.participant_info)
        var participant: Participant? = null
        val videoView: ParticipantSurfaceViewRenderer = itemView.findViewById(R.id.video_view)

        private val microphoneMuted: ImageView =
            itemView.findViewById(R.id.participant_microphone_muted)
        private var subscribeCameraJob: Job? = null

        private val eglBaseContext = eglBase.eglBaseContext

        init {
            videoView.setNumFramesNeeded(ENABLE_FRAMES_THRESHOLD, DISABLE_FRAMES_THRESHOLD)
            videoView.setAvatarView(avatar)
        }

        private var detachSinkFn: DetachSinkFn? = null

        @UiThread
        internal fun updateCameraSubscription() {
            participant?.let {
                if (isAttachedToWindow && it.cameraActive) {
                    subscribeCamera()
                } else {
                    unsubscribeCamera()
                }
            }
        }

        @UiThread
        private fun subscribeCamera() {
            cancelCameraSubscription()
            logger.trace("Subscribe camera for participant={}", participant?.id)
            participant?.let { participant ->
                val subscribe = {
                    itemView.let {
                        it.post {
                            if (isAttachedToWindow) {
                                subscribeCamera(participant, it)
                            }
                        }
                    }
                }
                subscribeCameraJob = CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
                    delay(CAMERA_SUBSCRIPTION_DELAY_MILLIS)
                    subscribe()
                }
            }
        }

        @UiThread
        private fun subscribeCamera(participant: Participant, view: View) {
            try {
                videoView.init(eglBaseContext)
                logger.debug("Subscribe camera with resolution {}x{}", view.width, view.height)
                detachSinkFn = participant.subscribeCamera(videoView, view.width, view.height)
                updateMirroring()
                videoView.enableVideo()
            } catch (e: RuntimeException) {
                logger.error("Error subscribing camera", e)
            }
        }

        @UiThread
        private fun unsubscribeCamera() {
            cancelCameraSubscription()
            itemView.post {
                participant?.unsubscribeCamera()
                videoView.disableVideo()
                detachSinkFn?.invoke()
                detachSinkFn = null
            }
        }

        @UiThread
        fun updateMirroring() {
            participant?.let {
                itemView.post {
                    videoView.setMirror(it.mirrorRenderer)
                }
            }
        }

        @UiThread
        fun updateCaptureState() {
            logger.trace("UpdateCaptureState for {}", participant)
            participant?.let {
                itemView.post {
                    microphoneMuted.visibility = if (it.microphoneActive) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }

                updateCameraSubscription()
            }
        }

        @UiThread
        internal fun cancelCameraSubscription() {
            subscribeCameraJob = subscribeCameraJob?.let {
                if (!it.isCompleted) {
                    val message = "Cancel camera subscription"
                    logger.trace(message)
                    it.cancel(message)
                }
                null
            }
        }
    }

    /**
     * Teardown the adapter when it will not be used anymore.
     *
     * This will release all video views and cancel pending camera subscriptions.
     */
    @UiThread
    fun teardown() {
        viewHolders.forEach {
            it.cancelCameraSubscription()
            it.videoView.release()
        }
        frozenStateUpdates.cancel("releaseVideoViews")
    }

    @UiThread
    fun setParticipants(updatedParticipants: Set<Participant>) {
        val removedParticipants = this.participants.filter { it !in updatedParticipants }
        val newlyAddedParticipants = updatedParticipants.filter { it !in this.participants }

        val previousCount = this.participants.size
        val newCount = updatedParticipants.size

        val needsNewLayout = hasItemHeightChanged(previousCount, newCount)

        if (needsNewLayout) {
            this.participants.apply {
                clear()
                addAll(updatedParticipants)
            }
            notifyDataSetChanged()
        } else {

            // Remove participants
            removedParticipants.forEach { participant ->
                val index = this.participants.indexOf(participant)
                this.participants.removeAt(index)
                notifyItemRemoved(index)
            }

            // Add participants
            if (newlyAddedParticipants.isNotEmpty()) {
                logger.debug("Add {} new participants", newlyAddedParticipants.size)
                val firstNewPosition = this.participants.size
                this.participants.addAll(newlyAddedParticipants)
                notifyItemRangeInserted(firstNewPosition, newlyAddedParticipants.size)
            }
        }
    }

    @UiThread
    fun updateMirroringForLocalParticipant() {
        localParticipantViewHolder?.updateMirroring()
    }

    @UiThread
    fun updateCaptureStates() {
        activeViewHolders.forEach(GroupCallParticipantViewHolder::updateCaptureState)
    }

    @UiThread
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GroupCallParticipantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            /* resource = */ R.layout.item_group_call_participant_list,
            /* root = */ parent,
            /* attachToRoot = */ false
        )
        return GroupCallParticipantViewHolder(eglBase, view, parent).also(viewHolders::add)
    }

    @UiThread
    override fun onViewAttachedToWindow(holder: GroupCallParticipantViewHolder) {
        holder.isAttachedToWindow = true
        holder.updateCaptureState()
    }

    @UiThread
    override fun onViewDetachedFromWindow(holder: GroupCallParticipantViewHolder) {
        holder.isAttachedToWindow = false
        holder.updateCaptureState()
    }

    @UiThread
    override fun onBindViewHolder(holder: GroupCallParticipantViewHolder, position: Int) {

        val participant = participants[position]

        val itemHeightPx = getViewHeight(holder.parent)
        holder.avatar.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemHeightPx
        )
        holder.itemView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemHeightPx
        )

        holder.participant = participant

        if (participant is LocalParticipant) {
            localParticipantViewHolder = holder
        }

        activeViewHolders.add(holder)

        holder.name.text = participant.name

        holder.avatar.post {
            if (participant is NormalParticipant) {
                contactService.loadAvatarIntoImage(
                    participant.contactModel,
                    holder.avatar,
                    AVATAR_OPTIONS,
                    requestManager
                )
            } else {
                logger.warn("Unknown group call participant type bound: {}", participant.type)
                holder.avatar.setImageResource(R.drawable.ic_person_outline)
            }
        }
    }

    @UiThread
    override fun onViewRecycled(holder: GroupCallParticipantViewHolder) {
        if (holder.participant is LocalParticipant) {
            localParticipantViewHolder = null
        }
        activeViewHolders.remove(holder)
    }

    @UiThread
    override fun getItemCount() = participants.size

    @UiThread
    private fun getViewHeight(parent: ViewGroup): Int {
        val rows = getRowCount(participants.size, isPortrait)
        val totalGutterPx = (rows + 1) * gutterPx
        // `+ 1` to compensate "lost" pixels due to integer arithmetic
        return (parent.measuredHeight - totalGutterPx) / rows + 1
    }

    @UiThread
    private fun hasItemHeightChanged(previousCount: Int, newCount: Int): Boolean {
        return STABLE_HEIGHT_RANGES[orientation]?.any { previousCount in it && newCount !in it }
            ?: true
    }

    @UiThread
    private companion object {
        val AVATAR_OPTIONS: AvatarOptions = AvatarOptions.Builder()
            .setReturnPolicy(AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK)
            .setHighRes(true)
            .toOptions()

        private const val CAMERA_SUBSCRIPTION_DELAY_MILLIS = 800L

        private const val ENABLE_FRAMES_THRESHOLD = 15
        private const val DISABLE_FRAMES_THRESHOLD = 5
        private const val UPDATE_FROZEN_INTERVAL_MS: Long = 5000

        /**
         * A stable height range is - depending on orientation - the range of participants in a call
         * that won't affect the view holders height. If the number of participants changes from within one
         * range to another, the height of the view holder will change.
         */
        val STABLE_HEIGHT_RANGES: Map<Orientation, List<IntRange>> = mapOf(
            Orientation.LANDSCAPE to listOf(0..2, 3..Int.MAX_VALUE),
            Orientation.PORTRAIT to listOf(0..1, 2..4, 5..Int.MAX_VALUE)
        )

        fun getRowCount(participants: Int, isPortrait: Boolean) = when {
            participants in 0..1 -> 1
            participants == 2 && !isPortrait -> 1
            participants in 2..4 || !isPortrait -> 2
            else -> 3
        }
    }
}

private enum class Orientation {
    LANDSCAPE, PORTRAIT
}
