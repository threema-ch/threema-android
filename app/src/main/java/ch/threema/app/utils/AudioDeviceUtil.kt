/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import ch.threema.app.R

/**
 * AudioDevice is the names of possible audio devices that we currently
 * support.
 */
enum class AudioDevice {
    SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE
}

/**
 * Get the icon resource of this audio device type
 */
fun AudioDevice.getIconResource(): Int {
    return when (this) {
        AudioDevice.SPEAKER_PHONE -> R.drawable.ic_volume_up_outline
        AudioDevice.WIRED_HEADSET -> R.drawable.ic_headset_mic_outline
        AudioDevice.EARPIECE -> R.drawable.ic_phone_in_talk
        AudioDevice.BLUETOOTH -> R.drawable.ic_bluetooth_searching_outline
        AudioDevice.NONE -> R.drawable.ic_mic_off_outline
    }
}

/**
 * Get the string resource of this audio device type
 */
fun AudioDevice.getStringResource(): Int {
    return when (this) {
        AudioDevice.SPEAKER_PHONE -> R.string.voip_speakerphone
        AudioDevice.WIRED_HEADSET -> R.string.voip_wired_headset
        AudioDevice.EARPIECE -> R.string.voip_earpiece
        AudioDevice.BLUETOOTH -> R.string.voip_bluetooth
        AudioDevice.NONE -> R.string.voip_none
    }
}

/**
 * Check whether this device has an earpiece (most phones) or not (most tablets)
 */
fun hasEarpiece(audioManager: AudioManager, context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.availableCommunicationDevices.any {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
    } else {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
}

/**
 * Get the default audio device of the given set
 */
fun getDefaultAudioDevice(audioDevices: Set<AudioDevice>): AudioDevice {
    return listOf(
            AudioDevice.BLUETOOTH,
            AudioDevice.WIRED_HEADSET,
            AudioDevice.EARPIECE
    ).firstOrNull { it in audioDevices } ?: AudioDevice.SPEAKER_PHONE
}
