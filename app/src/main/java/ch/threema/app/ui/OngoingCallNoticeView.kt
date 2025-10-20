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

package ch.threema.app.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.DefaultLifecycleObserver
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.GroupCallUtil
import ch.threema.app.voip.activities.CallActivity
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.services.VoipCallService
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.button.MaterialButton

private val logger = LoggingUtil.getThreemaLogger("OngoingCallNoticeView")

enum class OngoingCallNoticeMode {
    MODE_VOIP,
    MODE_GROUP_CALL_RUNNING,
    MODE_GROUP_CALL_JOINED,
}

class OngoingCallNoticeView : LinearLayout, DefaultLifecycleObserver {
    private var operationMode: OngoingCallNoticeMode? = null
    private var groupId: LocalGroupId? = null
    private lateinit var actionButton: MaterialButton
    private lateinit var callContainer: RelativeLayout
    private lateinit var chronometer: Chronometer
    private lateinit var callText: TextView
    private lateinit var participantsText: TextView
    private lateinit var ongoingCallDivider: View

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        init()
    }

    @AnyThread
    fun showVoip() {
        post {
            setupVoipActions()
            show(VoipCallService.getStartTime(), OngoingCallNoticeMode.MODE_VOIP)
        }
    }

    /**
     * Hide the notice only if the current [OngoingCallNoticeMode] is [OngoingCallNoticeMode.MODE_VOIP].
     */
    @AnyThread
    fun hideVoip() {
        logger.info(
            "Hide voip in operation mode `{}`",
            operationMode,
        ) // TODO(ANDR-2441): remove eventually
        if (operationMode == OngoingCallNoticeMode.MODE_VOIP) {
            hide()
        }
    }

    @AnyThread
    fun showGroupCall(call: GroupCallDescription, mode: OngoingCallNoticeMode) {
        post {
            val participantsCount: Int = call.callState?.participants?.size ?: 0
            setupGroupCallActions(call)
            val startAt = GroupCallUtil.getRunningSince(
                call = call,
                context = context,
            )
            show(
                startTime = startAt,
                mode = mode,
                participantCount = participantsCount,
            )
        }
    }

    /**
     * Hides the notice no matter what the current [OngoingCallNoticeMode] is.
     */
    @AnyThread
    fun hide() {
        post {
            chronometer.stop()
            visibility = GONE
        }
    }

    @UiThread
    private fun show(startTime: Long, mode: OngoingCallNoticeMode, participantCount: Int = 0) {
        setOperationMode(mode, participantCount)
        chronometer.visibility = VISIBLE
        chronometer.base = startTime
        chronometer.start()
        visibility = VISIBLE
    }

    private fun setOperationMode(mode: OngoingCallNoticeMode, participantCount: Int) {
        operationMode = mode

        when (mode) {
            OngoingCallNoticeMode.MODE_VOIP -> {
                callContainer.isClickable = true
                callContainer.isFocusable = true
                actionButton.text = context.getString(R.string.voip_hangup)
                actionButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_call_end_outline)
                callText.setText(R.string.call_ongoing)
                participantsText.visibility = GONE
                ongoingCallDivider.visibility = GONE
            }

            OngoingCallNoticeMode.MODE_GROUP_CALL_JOINED -> {
                callContainer.isClickable = false
                callContainer.isFocusable = false
                actionButton.text = context.getString(R.string.voip_gc_open_call)
                actionButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_phone_locked_outline)
                callText.setText(R.string.voip_gc_in_call)
                setParticipantsText(participantCount)
            }

            OngoingCallNoticeMode.MODE_GROUP_CALL_RUNNING -> {
                callContainer.isClickable = false
                callContainer.isFocusable = false
                actionButton.text = context.getString(R.string.voip_gc_join_call)
                actionButton.icon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_outline_login_24)
                callText.setText(R.string.voip_gc_ongoing_call)
                setParticipantsText(participantCount)
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        actionButton = findViewById(R.id.call_hangup)
        callContainer = findViewById(R.id.call_container)
        callText = findViewById(R.id.call_text)
        chronometer = findViewById(R.id.call_duration)
        participantsText = findViewById(R.id.participants_count)
        ongoingCallDivider = findViewById(R.id.ongoing_call_divider)
    }

    private fun init() {
        if (context !is AppCompatActivity) {
            return
        }
        (context as AppCompatActivity).lifecycle.addObserver(this)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.notice_ongoing_call, this)
    }

    private fun voipContainerAction() {
        logger.info("Run voip container action") // TODO(ANDR-2441): remove eventually
        if (VoipCallService.isRunning()) {
            val openIntent = Intent(context, CallActivity::class.java)
            openIntent.putExtra(VoipCallService.EXTRA_ACTIVITY_MODE, CallActivity.MODE_ACTIVE_CALL)
            openIntent.putExtra(
                VoipCallService.EXTRA_CONTACT_IDENTITY,
                VoipCallService.getOtherPartysIdentity(),
            )
            openIntent.putExtra(VoipCallService.EXTRA_START_TIME, VoipCallService.getStartTime())
            context.startActivity(openIntent)
        }
    }

    private fun voipButtonAction() {
        logger.info("Run voip button action") // TODO(ANDR-2441): remove eventually
        val hangupIntent = Intent(context, VoipCallService::class.java)
        hangupIntent.action = VoipCallService.ACTION_HANGUP
        context.startService(hangupIntent)
    }

    private fun groupCallButtonAction(call: GroupCallDescription) {
        context.startActivity(GroupCallActivity.createJoinCallIntent(context, call.getGroupIdInt()))
    }

    private fun setViewAction(view: View?, action: Runnable?) {
        if (action == null) {
            view?.setOnClickListener(null)
        } else {
            view?.setOnClickListener { action.run() }
        }
    }

    private fun setContainerAction(action: Runnable?) {
        setViewAction(callContainer, action)
    }

    private fun setButtonAction(action: Runnable?) {
        setViewAction(actionButton, action)
    }

    private fun setupVoipActions() {
        groupId = null
        setContainerAction(this::voipContainerAction)
        setButtonAction(this::voipButtonAction)
    }

    private fun setupGroupCallActions(call: GroupCallDescription) {
        if (groupId != call.groupId) {
            groupId = call.groupId
            setContainerAction(null)
            val action = call.let { { groupCallButtonAction(it) } }
            setButtonAction(action)
        }
    }

    private fun setParticipantsText(participantCount: Int) {
        if (participantCount > 0) {
            participantsText.text = ConfigUtils.getSafeQuantityString(
                context,
                R.plurals.n_participants_in_call,
                participantCount,
                participantCount,
            )
            participantsText.visibility = VISIBLE
            ongoingCallDivider.visibility = VISIBLE
        } else {
            participantsText.visibility = GONE
            ongoingCallDivider.visibility = GONE
        }
    }
}
