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

package ch.threema.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.DefaultLifecycleObserver
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import com.google.android.material.chip.Chip

object OngoingCallNoticeModes {
	const val MODE_VOIP = 0
	const val MODE_GROUP_CALL_RUNNING = 1
	const val MODE_GROUP_CALL_JOINED = 2
}

class OngoingCallNoticeView : LinearLayout, DefaultLifecycleObserver {
	private lateinit var actionButton: Chip
	private lateinit var callContainer: RelativeLayout
	private lateinit var chronometer: Chronometer
	private lateinit var callText: TextView
	private lateinit var participantsText: TextView

	constructor(context: Context) : super(context) {
		init()
	}

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
		init()
	}

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		init()
	}

	@UiThread
	fun hide() {
		chronometer.stop()
		visibility = View.GONE
	}

	@UiThread
	fun show(startTime: Long?, mode: Int, participantCount: Int) {
		setOperationMode(mode, participantCount)
		startTime?.let {
			chronometer.base = it
			chronometer.start()
		}
		visibility = View.VISIBLE
	}

	fun setContainerAction(action: Runnable?) {
		setViewAction(callContainer, action)
	}

	fun setButtonAction(action: Runnable?) {
		setViewAction(actionButton, action)
	}

	fun setOperationMode(mode: Int, participantCount: Int) {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			actionButton.setTextAppearance(R.style.TextAppearance_Chip_ChatNotice)
		}

		when (mode) {
			OngoingCallNoticeModes.MODE_VOIP -> {
				callContainer.isClickable = true
				callContainer.isFocusable = true
				actionButton.text = context.getString(R.string.voip_hangup)
				actionButton.chipBackgroundColor = getDangerousBackgroundColor()
				actionButton.setTextColor(getDangerousTextColor())
				actionButton.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_call_end_outline)
				actionButton.chipIconTint = getDangerousTextColor()
				callText.setText(R.string.call_ongoing)
				participantsText.visibility = GONE
			}
			OngoingCallNoticeModes.MODE_GROUP_CALL_JOINED -> {
				callContainer.isClickable = false
				callContainer.isFocusable = false
				actionButton.text = context.getString(R.string.voip_gc_open_call)
				actionButton.chipBackgroundColor = getBackgroundColorGroupCall()
				actionButton.setTextColor(getTextColorGroupCall())
				actionButton.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_group_call)
				actionButton.chipIconTint = getTextColorGroupCall()
				callText.setText(R.string.voip_gc_in_call)
				setParticipantsText(participantCount)
			}
			OngoingCallNoticeModes.MODE_GROUP_CALL_RUNNING -> {
				callContainer.isClickable = false
				callContainer.isFocusable = false
				actionButton.text = context.getString(R.string.voip_gc_join_call)
				actionButton.chipBackgroundColor = getBackgroundColorGroupCall()
				actionButton.setTextColor(getTextColorGroupCall())
				actionButton.chipIcon = AppCompatResources.getDrawable(context, R.drawable.ic_outline_login_24)
				actionButton.chipIconTint = getTextColorGroupCall()
				callText.setText(R.string.voip_gc_ongoing_call)
				setParticipantsText(participantCount)
			}
			else -> {
				// should never happen
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
	}

	private fun init() {
		if (context !is AppCompatActivity) {
			return
		}
		(context as AppCompatActivity).lifecycle.addObserver(this)
		val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		inflater.inflate(R.layout.view_ongoing_call_notice, this)
	}

	private fun getBackgroundColorGroupCall(): ColorStateList {
		val backgroundColor = ColorStateList.valueOf(resources.getColor(R.color.group_call_accent))
		return backgroundColor.withAlpha(0x1a)
	}

	private fun getDefaultBackgroundColor(): ColorStateList {
		val backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(context, R.attr.colorAccent))
		return backgroundColor.withAlpha(0x1a)
	}

	private fun getTextColorGroupCall(): ColorStateList {
		return ColorStateList.valueOf(resources.getColor(R.color.group_call_accent))
	}

	private fun getTextColor(): ColorStateList {
		return ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(context, R.attr.colorAccent))
	}

	private fun getDangerousTextColor(): ColorStateList {
		return ColorStateList.valueOf(resources.getColor(R.color.material_red))
	}

	private fun getDangerousBackgroundColor(): ColorStateList {
		val backgroundColor = ColorStateList.valueOf(resources.getColor(R.color.material_red))
		return backgroundColor.withAlpha(0x1a)
	}

	private fun setViewAction(view: View?, action: Runnable?) {
		if (action == null) {
			view?.setOnClickListener(null)
		} else {
			view?.setOnClickListener { action.run() }
		}
	}

	private fun setParticipantsText(participantCount: Int) {
		if (participantCount > 0) {
			participantsText.text = ConfigUtils.getSafeQuantityString(
				context,
				R.plurals.n_participants_in_call,
				participantCount,
				participantCount
			)
			participantsText.visibility = VISIBLE
		} else {
			participantsText.visibility = GONE
		}
	}
}
