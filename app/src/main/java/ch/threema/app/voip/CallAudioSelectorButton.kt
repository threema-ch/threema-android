/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.voip

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import ch.threema.app.R
import ch.threema.app.utils.AudioDevice
import ch.threema.app.utils.TestUtil
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("CallAudioSelectorButton")

class CallAudioSelectorButton : AppCompatImageView, View.OnClickListener {
    var audioDevices: Set<AudioDevice> = emptySet()
        set(value) {
            logger.trace("Set audio devices: {}", value)
            if (field != value) {
                field = value
                updateButtonAppearance()
            }
        }

    var selectedAudioDevice: AudioDevice = AudioDevice.EARPIECE
        set(value) {
            logger.trace("Set selected device: {}", value)
            if (field != value) {
                field = value
                updateButtonAppearance()
            }
        }

    var multiSelectionListener: AudioDeviceMultiSelectListener? = null
    var selectionListener: AudioDeviceSelectionListener? = null

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr,
    ) {
        init()
    }

    private fun updateButtonAppearance() {
        logger.trace("Update button appearance")
        val layers = background as LayerDrawable
        layers.findDrawableByLayerId(R.id.moreIndicatorItem).alpha =
            if (audioDevices.size > 2) VISIBLE else HIDDEN
        layers.findDrawableByLayerId(R.id.bluetoothItem).alpha =
            if (selectedAudioDevice == AudioDevice.BLUETOOTH) VISIBLE else HIDDEN
        layers.findDrawableByLayerId(R.id.handsetItem).alpha =
            if (selectedAudioDevice == AudioDevice.EARPIECE) VISIBLE else HIDDEN
        layers.findDrawableByLayerId(R.id.headsetItem).alpha =
            if (selectedAudioDevice == AudioDevice.WIRED_HEADSET) VISIBLE else HIDDEN
        layers.findDrawableByLayerId(R.id.speakerphoneItem).alpha =
            if (selectedAudioDevice == AudioDevice.SPEAKER_PHONE) VISIBLE else HIDDEN
        if (!TestUtil.isInDeviceTest()) {
            isClickable = audioDevices.size > 1
            isEnabled = audioDevices.size > 1
        }
    }

    private fun init() {
        setOnClickListener(this)
    }

    override fun onClick(v: View) {
        if (audioDevices.size > 2) {
            multiSelectionListener?.onShowSelected(audioDevices, selectedAudioDevice)
        } else {
            var newAudioDevice = AudioDevice.EARPIECE
            for (device in audioDevices) {
                if (device != selectedAudioDevice) {
                    newAudioDevice = device
                }
            }
            logger.trace("Selection: {}", newAudioDevice)
            selectionListener?.onSelection(newAudioDevice)
        }
    }

    companion object {
        // Constants for Drawable.setAlpha()
        private const val HIDDEN = 0
        private const val VISIBLE = 255
    }

    fun interface AudioDeviceMultiSelectListener {
        fun onShowSelected(audioDevices: Set<AudioDevice>, selectedDevice: AudioDevice)
    }

    fun interface AudioDeviceSelectionListener {
        fun onSelection(audioDevice: AudioDevice)
    }
}
