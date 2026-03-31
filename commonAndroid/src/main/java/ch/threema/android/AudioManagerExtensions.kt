package ch.threema.android

import android.media.AudioDeviceInfo
import android.media.AudioManager

fun AudioManager.isConnectedToHeadsetOrSpeaker(): Boolean =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        ?.any { audioDeviceInfo ->
            when (audioDeviceInfo.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> true
                else -> false
            }
        }
        ?: false
