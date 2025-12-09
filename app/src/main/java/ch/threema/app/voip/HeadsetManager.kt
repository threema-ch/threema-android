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

package ch.threema.app.voip

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.app.ActivityCompat
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = getThreemaLogger("HeadsetManager")

/**
 * Manager for headsets. This can be used to detect whether there are wired or bluetooth headsets
 * available. Additionally, headset changes can be observed. [playAudioOverBluetoothSco] and
 * [stopAudioOverBluetoothSco] can be used to indicate that the user wants to change the audio
 * device.
 */
class HeadsetManager(private val context: Context, private val audioManager: AudioManager) {
    enum class BluetoothHeadsetState {
        /** A bluetooth headset is connected and ready to use */
        CONNECTED,

        /** A bluetooth headset is connected and sco connection is being established */
        AUDIO_CONNECTING,

        /** A bluetooth headset is connected and sco connection is established */
        AUDIO_CONNECTED,

        /** A bluetooth headset is connected but sco connection has been stopped */
        AUDIO_DISCONNECTED,

        /** No bluetooth headset is connected */
        DISCONNECTED,

        /** An error has occurred with the bluetooth headset */
        ERROR,

        /** This state must be ignored and should not result in any changes */
        UNINITIALIZED,
    }

    private val wiredHeadsetFlow = MutableStateFlow(false)
    private val bluetoothHeadsetFlow = MutableStateFlow(BluetoothHeadsetState.UNINITIALIZED)

    private var wiredHeadsetConnected: Boolean = hasWiredHeadset(audioManager)
        set(value) {
            wiredHeadsetFlow.value = value
            field = value
        }

    private var bluetoothHeadsetState: BluetoothHeadsetState = when {
        hasBluetoothHeadset() -> BluetoothHeadsetState.CONNECTED
        else -> BluetoothHeadsetState.DISCONNECTED
    }
        set(value) {
            bluetoothHeadsetFlow.value = value
            field = value
        }

    private val pstnStateListener = PSTNStateListener(context)

    /**
     * The wired headset broadcast receiver keeps track of the current connection state of wired
     * headsets.
     */
    private val wiredHeadsetBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val plugged = intent?.getIntExtra("state", 0) == 1
            val hasMicrophone = intent?.getIntExtra("microphone", 0) == 1
            val name = intent?.getStringExtra("name")

            logger.debug(
                "Receive ACTION_HEADSET_PLUG: name={}, plugged={}, hasMicrophone={}",
                name,
                plugged,
                hasMicrophone,
            )

            wiredHeadsetConnected = plugged
        }
    }

    /**
     * The broadcast receiver handles the management of the bluetooth headsets.
     */
    private val bluetoothBroadcastReceiver = object : BroadcastReceiver() {
        private val lastPSTNCallThreshold = 500L
        private val bluetoothScoTimeoutMs = 4000L
        private val maxScoConnectionAttempts = 2
        private var scoConnectionTimeoutExecutor: ScheduledFuture<*>? = null

        private var bluetoothAudioConnectedAt: Long? = when {
            isBluetoothHeadsetConnected() -> System.nanoTime()
            else -> null
        }
        private var scoConnectionAttempts = 0

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) {
                logger.info("Bluetooth broadcast intent is null")
                return
            }

            when (val action = intent.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> onConnectionStateChange(intent)
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> onAudioStateChange(intent)
                else -> logger.warn("Unknown bluetooth broadcast action: {}", action)
            }

            logger.info("onReceive done: BT state={}", bluetoothHeadsetState)
        }

        /**
         * Handle connection state changes.
         */
        private fun onConnectionStateChange(intent: Intent) {
            val state = intent.getIntExtra(
                BluetoothHeadset.EXTRA_STATE,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
            )

            logger.info(
                "BluetoothHeadsetBroadcastReceiver.onReceive: " +
                    "a=ACTION_AUDIO_STATE_CHANGED, s={}, sb={}, BT state: {}",
                state.toString(),
                isInitialStickyBroadcast,
                bluetoothHeadsetState.name,
            )

            when (state) {
                BluetoothHeadset.STATE_CONNECTED -> {
                    logger.info("Receiving STATE_CONNECTED")
                    scoConnectionAttempts = 0
                    bluetoothAudioConnectedAt = System.nanoTime()
                    bluetoothHeadsetState = BluetoothHeadsetState.CONNECTED
                }

                BluetoothHeadset.STATE_DISCONNECTED -> {
                    logger.info("Receiving STATE_DISCONNECTED")
                    if (bluetoothHeadsetState == BluetoothHeadsetState.AUDIO_CONNECTED) {
                        audioManager.stopBluetoothSco()
                    }
                    bluetoothHeadsetState = BluetoothHeadsetState.DISCONNECTED
                }

                BluetoothHeadset.STATE_CONNECTING -> logger.info("Receiving STATE_CONNECTING")
                BluetoothHeadset.STATE_DISCONNECTING -> logger.info("Receiving STATE_DISCONNECTING")
                else -> logger.info("Receiving unknown state: {}", state)
            }
        }

        /**
         * Handle audio state changes.
         */
        private fun onAudioStateChange(intent: Intent) {
            val state = intent.getIntExtra(
                BluetoothHeadset.EXTRA_STATE,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
            )

            logger.info(
                "BluetoothHeadsetBroadcastReceiver.onReceive: " +
                    "a=ACTION_AUDIO_STATE_CHANGED, s={}, sb={}, BT state: {}",
                state.toString(),
                isInitialStickyBroadcast,
                bluetoothHeadsetState.name,
            )

            if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                cancelScoTimer()
                logger.info("Bluetooth audio successfully connected")
                bluetoothAudioConnectedAt = System.nanoTime()
                bluetoothHeadsetState = BluetoothHeadsetState.AUDIO_CONNECTED
            } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                logger.info("+++ Bluetooth audio SCO is now connecting...")
            } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                logger.info("+++ Bluetooth audio SCO is now disconnected")

                if (pstnStateListener.isRinging()) {
                    // In this case it is likely that bluetooth SCO is just disconnected because
                    // there is an incoming PSTN call. We do not need to do anything.
                    logger.info("PSTN call is ringing")
                } else if (pstnStateListener.isIdle() && hasRecentDeclinedCall()) {
                    // In this case we have a PSTN call that just stopped ringing. Therefore we want
                    // to continue with our bluetooth group call and must start sco again.
                    startSco(true)
                } else if (isInitialStickyBroadcast) {
                    logger.info("Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.")
                } else if (bluetoothHeadsetState == BluetoothHeadsetState.AUDIO_CONNECTED) {
                    // The bluetooth device is now disconnected after previously having been
                    // connected. We now have two options: Either switch audio to the built-in
                    // speaker / earpiece, or end the call.
                    //
                    // Since many bluetooth headsets just have one button, and because the user
                    // should be able to hang up the call with that, we'll end the call.
                    //
                    // Note that some bluetooth devices connect the audio for a very short while
                    // and then disconnect it again. This is probably if Android would allow audio
                    // connections, but it's turned off inside the device. (This happens e.g. on
                    // the Asus ZenWatch devices when bluetooth audio is turned off.) In that
                    // case we simply stop bluetooth.
                    //
                    // There are also headsets that detect whether they are being worn or not.
                    // Based on the data from support tickets, this process takes roughly a second.
                    // Therefore we set the threshold at 1500 ms to avoid ending the call if the
                    // headset disconnects after 1500 ms.
                    var msElapsed: Long? = null
                    val msElapsedThreshold: Long = 1500
                    bluetoothAudioConnectedAt?.let {
                        msElapsed = (System.nanoTime() - it) / 1_000_000
                    }
                    logger.info("Time elapsed since bluetooth audio connected: {} ms", msElapsed)
                    msElapsed.let {
                        bluetoothHeadsetState = if (it == null || it < msElapsedThreshold) {
                            logger.info("Bluetooth headset audio disconnected. Switching to phone audio.")
                            BluetoothHeadsetState.AUDIO_DISCONNECTED
                        } else {
                            logger.info(
                                "Bluetooth headset audio disconnected after {} ms. Ending call.",
                                msElapsed,
                            )
                            BluetoothHeadsetState.DISCONNECTED
                        }
                    }
                }
            }
        }

        /**
         * Start playing audio via bluetooth headset.
         */
        fun startSco(resetAttempts: Boolean) {
            logger.trace("startSco")

            if (resetAttempts) {
                scoConnectionAttempts = 0
            }

            if (!isBluetoothHeadsetConnected()) {
                logger.debug(
                    "Did not start bluetooth sco because there is no headset connected {}",
                    bluetoothHeadsetState,
                )
                return
            }

            bluetoothHeadsetState = BluetoothHeadsetState.AUDIO_CONNECTING

            audioManager.startBluetoothSco()
            startScoTimer()
        }

        /**
         * Stop playing audio via bluetooth headset.
         */
        fun stopSco() {
            logger.trace("stopSco")
            if (!isBluetoothHeadsetConnected() || !audioManager.isBluetoothScoOn) {
                logger.debug("Cannot stop bluetooth sco: no bluetooth device connected or sco already off")
                return
            }
            bluetoothHeadsetState = BluetoothHeadsetState.AUDIO_DISCONNECTED

            scoConnectionTimeoutExecutor?.cancel(false)
            audioManager.stopBluetoothSco()
        }

        fun destroy() {
            pstnStateListener.destroy()
        }

        /**
         * Start the sco timer. After [bluetoothScoTimeoutMs] milliseconds it is executed (if not
         * canceled before).
         */
        private fun startScoTimer() {
            cancelScoTimer()
            scoConnectionTimeoutExecutor = Executors.newSingleThreadScheduledExecutor()
                .schedule({ onScoTimeout() }, bluetoothScoTimeoutMs, TimeUnit.MILLISECONDS)
        }

        /**
         * Cancels the sco timer.
         */
        private fun cancelScoTimer() {
            scoConnectionTimeoutExecutor?.cancel(false)
            scoConnectionTimeoutExecutor = null
        }

        /**
         * Executed when sco connection times out. It tries to start sco again. If the maximum number
         * of sco connection attempts is reached, it sets the state to [BluetoothHeadsetState.ERROR]
         */
        private fun onScoTimeout() {
            logger.trace("onScoTimeout {}", scoConnectionAttempts)
            scoConnectionAttempts++
            if (audioManager.isBluetoothScoOn) {
                logger.info("Bluetooth sco is unexpectedly on")
                bluetoothAudioConnectedAt = System.nanoTime()
                bluetoothHeadsetState = BluetoothHeadsetState.AUDIO_CONNECTED
                return
            }
            if (scoConnectionAttempts < maxScoConnectionAttempts) {
                // Calling stopBluetoothSco sometimes helps to fix the sco connection problems
                audioManager.stopBluetoothSco()
                startSco(false)
            } else {
                logger.warn("Could not start bluetooth sco")
                bluetoothHeadsetState = BluetoothHeadsetState.ERROR
            }
        }

        private fun hasRecentDeclinedCall(): Boolean {
            pstnStateListener.lastDeclinedCall().let {
                if (it == -1L) {
                    logger.info("No last declined PSTN call")
                    return false
                } else {
                    val timeSinceDeclinedCall = System.currentTimeMillis() - it
                    logger.info("Last declined call {} ms ago", timeSinceDeclinedCall)
                    return timeSinceDeclinedCall < lastPSTNCallThreshold
                }
            }
        }
    }

    init {
        context.registerReceiver(
            wiredHeadsetBroadcastReceiver,
            IntentFilter(AudioManager.ACTION_HEADSET_PLUG),
        )

        // Register receivers for BluetoothHeadset change notifications
        val bluetoothHeadsetFilter = IntentFilter()
        // Register receiver for change in connection state of the bluetooth headset
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        // Register receiver for change in audio connection state of the bluetooth headset
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        context.registerReceiver(bluetoothBroadcastReceiver, bluetoothHeadsetFilter)
    }

    /**
     * Observe state changes regarding wired headsets. True is emitted if a wired headset is
     * connected, false if it has been disconnected.
     */
    fun observeWiredHeadset(): StateFlow<Boolean> = wiredHeadsetFlow.asStateFlow()

    /**
     * Observe state changes regarding bluetooth headsets.
     */
    fun observeBluetoothHeadset(): StateFlow<BluetoothHeadsetState> =
        bluetoothHeadsetFlow.asStateFlow()

    /**
     * Get the current connection state of the wired headset.
     */
    fun isWiredHeadsetConnected(): Boolean = wiredHeadsetConnected

    /**
     * Get the current connection state of the bluetooth headset.
     */
    fun isBluetoothHeadsetConnected(): Boolean =
        bluetoothHeadsetState != BluetoothHeadsetState.UNINITIALIZED &&
            bluetoothHeadsetState != BluetoothHeadsetState.DISCONNECTED

    /**
     * Try playing audio via bluetooth. By observing the bluetooth headset state (with
     * [observeBluetoothHeadset]), updates whether starting bluetooth sco has succeeded can be
     * retrieved.
     */
    fun playAudioOverBluetoothSco() {
        bluetoothBroadcastReceiver.startSco(true)
    }

    /**
     * Stop playing audio via bluetooth. This updates the [bluetoothHeadsetState].
     */
    fun stopAudioOverBluetoothSco() {
        bluetoothBroadcastReceiver.stopSco()
    }

    /**
     * Destroy the manager. After calling this, the manager won't emit connection updates anymore.
     */
    fun destroy() {
        context.unregisterReceiver(wiredHeadsetBroadcastReceiver)
        context.unregisterReceiver(bluetoothBroadcastReceiver)
        bluetoothBroadcastReceiver.destroy()
    }

    /**
     * Checks whether a wired headset is connected. This is independent of [wiredHeadsetConnected]
     * and queries the audio manager directly.
     */
    private fun hasWiredHeadset(audioManager: AudioManager): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
    }

    /**
     * Checks whether there is a connected bluetooth headset. This is independent of the [bluetoothHeadsetState]
     * and queries the bluetooth adapter directly.
     */
    private fun hasBluetoothHeadset(): Boolean {
        logger.trace("Checking initial bluetooth headset")
        val bluetoothAdapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
                ?: return false
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            logger.warn("No bluetooth permission to check initial bluetooth connection")
        }
        return try {
            bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        } catch (exception: SecurityException) {
            logger.error("Permission not granted for bluetooth devices")
            false
        }
    }
}
