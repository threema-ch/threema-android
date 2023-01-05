/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.AudioDevice
import ch.threema.app.utils.getDefaultAudioDevice
import ch.threema.app.utils.hasEarpiece
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = LoggingUtil.getThreemaLogger("CallAudioManager")

class CallAudioManager(private val context: Context) {
    enum class State {
        UNINITIALIZED, STOPPED, RUNNING
    }

    private val audioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var stateLock = ReentrantLock()
    private var amState: State = State.UNINITIALIZED

    /* Saved properties of the audio manager to restore it after call */
    private var savedAudioMode: Int = 0
    private var savedIsSpeakerPhoneOn: Boolean = false
    private var savedIsMicrophoneMuted: Boolean = false
    private var savedIsBluetoothScoOn: Boolean = false

    private var userSelectedAudioDevice = AudioDevice.NONE
    private val mutableSelectedAudioDevice = MutableStateFlow(AudioDevice.NONE)
    private var selectedAudioDevice = AudioDevice.NONE
        set(value) {
            mutableSelectedAudioDevice.value = value
            field = value
        }
    private val audioDevices: MutableSet<AudioDevice> = mutableSetOf()
    private val mutableAudioDevices: MutableStateFlow<Set<AudioDevice>> = MutableStateFlow(setOf())
    private val headsetManager = HeadsetManager(context, audioManager)

    private var wiredHeadsetJob: Job? = null
    private var bluetoothHeadSetJob: Job? = null

    private val audioFocusRequest: AudioFocusRequestCompat =
            AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(AudioAttributesCompat.Builder()
                            .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                            .build()
                    ).setOnAudioFocusChangeListener { logger.info("Audio focus changed: {}", it) }
                    .build()

    /**
     *  Start and initialize the audio manager.
     *
     *  A stopped audio manager cannot be reused and therefore not be restarted.
     *
     *  If the audio manager is in the states RUNNING or STOPPED the state will not change
     *  and no action will be performed.
     */
    fun start() {
        stateLock.withLock {
            when (amState) {
                State.RUNNING -> logger.warn("CallAudioManager is already running")
                State.STOPPED -> logger.warn("CallAudioManager is already stopped")
                State.UNINITIALIZED -> updateState(State.RUNNING, this::initialize)
            }
        }
    }

    /**
     * Stop this audio manager.
     *
     * Once stopped the manager cannot be reused and subsequent calls to this method
     * will not have any consequences (no state change or actions performed).
     */
    fun stop() {
        stateLock.withLock {
            when (amState) {
                State.STOPPED -> logger.warn("CallAudioManager is already stopped")
                State.UNINITIALIZED -> {
                    logger.warn("CallAudioManager has not been started")
                    updateState(State.STOPPED)
                }
                State.RUNNING -> updateState(State.STOPPED, this::shutdown)
            }
        }
    }

    /**
     * Get a flow of the set of available audio devices.
     */
    fun observeAvailableAudioDevices(): Flow<Set<AudioDevice>> = withState {
        when (it) {
            State.RUNNING -> mutableAudioDevices.asStateFlow()
            else -> emptyFlow()
        }
    }

    /**
     * Get a flow of the selected audio device.
     */
    fun observeSelectedDevice(): Flow<AudioDevice> = withState {
        when (it) {
            State.RUNNING -> mutableSelectedAudioDevice.asStateFlow()
            else -> emptyFlow()
        }
    }

    /**
     * Select the audio device. Note that the selected audio device may become unavailable and then
     * the audio device is switched automatically.
     */
    fun selectAudioDevice(device: AudioDevice) {
        userSelectedAudioDevice = device
        adoptAudioDeviceSelection()
    }

    private fun updateState(newState: State, stateUpdate: Runnable? = null) {
        stateLock.withLock {
            if (amState == newState) {
                logger.warn("CallAudioManager already in state {}", newState)
            } else {
                logger.info("CallAudioManager set state from {} to {}", amState, newState)
                amState = newState
                stateUpdate?.run()
            }
        }
    }

    private fun initialize() {
        saveAudioManagerState()

        requestAudioFocus()

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        muteMicrophone(false)

        // Set initial device states.
        userSelectedAudioDevice = AudioDevice.NONE
        selectedAudioDevice = AudioDevice.NONE
        audioDevices.clear()

        // Set initial audio device selection
        adoptAudioDeviceSelection()

        // Observe wired headset changes
        val scope = CoroutineScope(Dispatchers.Default)
        wiredHeadsetJob = scope.launch {
            headsetManager.observeWiredHeadset().collect {
                adoptAudioDeviceSelection()
            }
        }

        // Observe bluetooth headset changes
        bluetoothHeadSetJob = scope.launch {
            headsetManager.observeBluetoothHeadset().collect {
                handleBluetoothHeadsetStateChange(it)
            }
        }
    }

    private fun requestAudioFocus() {
        AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest).let {
            if (it == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger.info("Audio focus for call granted")
            } else {
                logger.info("Audio focus request for call failed")
            }
        }
    }

    private fun handleBluetoothHeadsetStateChange(state: HeadsetManager.BluetoothHeadsetState) {
        logger.trace("Observed new bluetooth headset state {}", state)
        when (state) {
            // A headset has been newly connected. Audio is not yet played over it.
            HeadsetManager.BluetoothHeadsetState.CONNECTED -> {
                headsetManager.playAudioOverBluetoothSco()
            }
            // A headset is now playing the audio
            HeadsetManager.BluetoothHeadsetState.AUDIO_CONNECTED -> {
                if (selectedAudioDevice != AudioDevice.BLUETOOTH) {
                    // If a bluetooth device plays the audio, then update the button
                    userSelectedAudioDevice = AudioDevice.BLUETOOTH
                }
                adoptAudioDeviceSelection()
            }
            // The headset is still connected, but does not play the audio anymore
            HeadsetManager.BluetoothHeadsetState.AUDIO_DISCONNECTED -> {
                if (selectedAudioDevice == AudioDevice.BLUETOOTH && headsetManager.isBluetoothHeadsetConnected()) {
                    // Not connected (anymore) with bluetooth audio. Switch to alternative
                    // audio device but leave bluetooth in list (because it is still connected)
                    userSelectedAudioDevice = getDefaultAudioDevice(queryAudioDevices().minus(AudioDevice.BLUETOOTH))
                }
                adoptAudioDeviceSelection()
            }
            // The headset has been disconnected
            HeadsetManager.BluetoothHeadsetState.DISCONNECTED -> {
                if (selectedAudioDevice == AudioDevice.BLUETOOTH) {
                    ThreemaApplication.getServiceManager()?.groupCallManager?.abortCurrentCall()
                } else {
                    adoptAudioDeviceSelection()
                }
            }
            else -> {
                // The other state changes can be ignored here
            }
        }
    }

    private fun shutdown() {
        logger.trace("shutdown")
        restoreAudioManagerState()

        // Abandon audio focus
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)

        headsetManager.destroy()
        wiredHeadsetJob?.cancel()
        bluetoothHeadSetJob?.cancel()
    }

    private fun saveAudioManagerState() {
        savedAudioMode = audioManager.mode
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
        savedIsMicrophoneMuted = audioManager.isMicrophoneMute
        savedIsBluetoothScoOn = audioManager.isBluetoothScoOn
    }

    private fun restoreAudioManagerState() {
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedIsSpeakerPhoneOn
        muteMicrophone(savedIsMicrophoneMuted)
        if (savedIsBluetoothScoOn != audioManager.isBluetoothScoOn) {
            if (savedIsBluetoothScoOn) {
                audioManager.startBluetoothSco()
            } else {
                audioManager.stopBluetoothSco()
            }
        }
    }

    private fun queryAudioDevices(): Set<AudioDevice> {
        val devices: MutableList<AudioDevice> = mutableListOf()

        if (headsetManager.isWiredHeadsetConnected()) {
            // Use the wired headset if connected
            devices.add(AudioDevice.WIRED_HEADSET)
        } else {
            // Otherwise check if earpiece is available (phones) and add speaker
            if (hasEarpiece(audioManager, context)) {
                devices.add(AudioDevice.EARPIECE)
            }
            devices.add(AudioDevice.SPEAKER_PHONE)
        }
        if (headsetManager.isBluetoothHeadsetConnected()) {
            // Add bluetooth headset if available
            devices.add(AudioDevice.BLUETOOTH)
        }

        logger.debug("Query audio devices: {}", devices)

        return devices.toSet()
    }

    private fun muteMicrophone(muted: Boolean) {
        if (audioManager.isMicrophoneMute != muted) {
            logger.debug("Mute microphone: {}", muted)
            audioManager.isMicrophoneMute = muted
        }
    }

    /**
     * Select the the audio device that is selected by the user. If not available, switch to the
     * next available one.
     */
    private fun adoptAudioDeviceSelection() {
        val devices = queryAudioDevices()

        val devicesChanged = audioDevices != devices
        if (devicesChanged) {
            setAudioDevices(devices)
        }

        val newAudioDevice = when {
            userSelectedAudioDevice in audioDevices -> userSelectedAudioDevice
            selectedAudioDevice in audioDevices -> selectedAudioDevice
            else -> getDefaultAudioDevice(audioDevices)
        }

        if (newAudioDevice != selectedAudioDevice || devicesChanged) {
            setAudioDevice(newAudioDevice)
            selectedAudioDevice = newAudioDevice
        }
    }

    /**
     * Enable the audio device.
     */
    private fun setAudioDevice(audioDevice: AudioDevice) {
        logger.info("Set audio device {}", audioDevice)

        if (audioDevice in audioDevices) {
            selectedAudioDevice = audioDevice
            audioManager.isSpeakerphoneOn = audioDevice == AudioDevice.SPEAKER_PHONE
            if (audioDevice == AudioDevice.BLUETOOTH) {
                headsetManager.playAudioOverBluetoothSco()
            } else if (audioManager.isBluetoothScoOn) {
                headsetManager.stopAudioOverBluetoothSco()
            }
        } else {
            logger.error("Cannot set audio device {} is not available", audioDevice)
        }
    }

    /**
     * Set the available audio devices
     */
    private fun setAudioDevices(devices: Set<AudioDevice>) {
        audioDevices.clear()
        audioDevices.addAll(devices)
        mutableAudioDevices.value = devices.toSet()
    }

    private fun <T> withState(block: (State) -> T): T {
        return stateLock.withLock {
            block(amState)
        }
    }
}
