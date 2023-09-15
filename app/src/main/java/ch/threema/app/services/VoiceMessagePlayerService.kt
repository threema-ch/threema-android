/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.*
import androidx.media3.session.MediaSession.Callback
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.listeners.SensorListener
import ch.threema.app.listeners.SensorListener.keyIsNear
import ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.SoundUtil
import ch.threema.app.voicemessage.SamsungQuirkAudioSink
import ch.threema.base.utils.LoggingUtil
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VoiceMessagePlayerService : MediaSessionService(), SensorListener, OnAudioFocusChangeListener {
    private val logger = LoggingUtil.getThreemaLogger(TAG)
    private val audioBecomingNoisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val audioBecomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.pause()
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequestCompat: AudioFocusRequestCompat

    private var sensorService: SensorService? = null
    private var preferenceService: PreferenceService? = null
    private var hasAudioFocus = false

    companion object {
        private const val TAG = "VoiceMessagePlayerService"
        private const val NOTIFICATION_ID = 59843
        private const val CHANNEL_ID = "voice_message_player_service"
    }

    override fun onCreate() {
        logger.info("onCreate")
        super.onCreate()

        ThreemaApplication.getServiceManager()?.let {
            this.sensorService = it.sensorService
            this.preferenceService = it.preferenceService
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioFocusRequestCompat =
            AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC).build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()

        val mediaNotificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.notification_channel_voice_message_player)
            .setChannelId(NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER)
            .setNotificationIdProvider { ThreemaApplication.VOICE_MSG_PLAYER_NOTIFICATION_ID }
            .build()
        mediaNotificationProvider.setSmallIcon(R.drawable.ic_notification_small)
        setMediaNotificationProvider(mediaNotificationProvider)

        initializeSessionAndPlayer()
        setListener(MediaSessionServiceListener())
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (player.playbackState == Player.STATE_ENDED && !startInForegroundRequired) {
            logger.info("Playback ended.")
        }
        super.onUpdateNotification(session, true)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logger.info("onTaskRemoved")
        if (!player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        logger.info("onDestroy")
        preferenceService?.let {
            if (it.isUseProximitySensor) {
                sensorService?.unregisterSensors(TAG)
            }
        }
        releaseAudioFocus()
        player.release()
        mediaSession.release()
        clearListener()
        super.onDestroy()
    }

    private fun initializeSessionAndPlayer() {
        player = ExoPlayer.Builder(this)
                .setRenderersFactory(SamsungQuirkRenderersFactory(this))
                .setAudioAttributes(getRegularAudioAttributes(), false)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setLoadControl(
                        DefaultLoadControl.Builder()
                                .setBufferDurationsMs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                                .build()
                )
                .build()

        preferenceService?.let {
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    logger.debug("onIsPlayingChanged {}", isPlaying)
                    if (isPlaying) {
                        logger.info("Start playing")
                        if (it.isUseProximitySensor && !SoundUtil.isHeadsetOn(audioManager)) {
                            sensorService?.registerSensors(TAG, this@VoiceMessagePlayerService)
                        }
                        requestAudioFocus()
                    } else {
                        logger.info("Stop playing")
                        if (it.isUseProximitySensor) {
                            sensorService?.unregisterSensors(TAG)
                        }
                        releaseAudioFocus()
                    }
                }
            })
        }

        val mediaSessionCallback = (object : Callback {
            override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                val resolvedMediaItems = mutableListOf<MediaItem>()
                for (mediaItem in mediaItems) {
                    val resolvedMediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(mediaItem.mediaId))
                            .setMediaId(mediaItem.mediaId)
                            .setMediaMetadata(mediaItem.mediaMetadata)
                            .build()
                    resolvedMediaItems.add(resolvedMediaItem)
                }
                return Futures.immediateFuture(resolvedMediaItems)
            }
        })

        mediaSession =
                MediaSession.Builder(this, player)
                        .setCallback(mediaSessionCallback)
                        .setSessionActivity(getSessionActivityPendingIntent())
                        .build()
    }

    private fun getSessionActivityPendingIntent(): PendingIntent {
        val intent = packageManager
                .getLaunchIntentForPackage(packageName)
                ?.setPackage(null)
                ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        val immutableFlag = if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0
        return getActivity(this, 0, intent, immutableFlag or FLAG_UPDATE_CURRENT)
    }

    private inner class MediaSessionServiceListener : Listener {

        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
        override fun onForegroundServiceStartNotAllowedException() {
            @SuppressLint("MissingPermission")
            if (ConfigUtils.isPermissionGranted(this@VoiceMessagePlayerService, Manifest.permission.POST_NOTIFICATIONS)) {
                val notificationManagerCompat = NotificationManagerCompat.from(this@VoiceMessagePlayerService)
                ensureNotificationChannel(notificationManagerCompat)
                val builder =
                        NotificationCompat.Builder(this@VoiceMessagePlayerService, CHANNEL_ID)
                                .setContentIntent(getSessionActivityPendingIntent())
                                .setSmallIcon(R.drawable.ic_notification_small)
                                .setColor(ResourcesCompat.getColor(resources, R.color.md_theme_light_primary, theme))
                                .setContentTitle(getString(R.string.vm_fg_service_not_allowed))
                                .setStyle(
                                        NotificationCompat.BigTextStyle().bigText(getString(R.string.vm_fg_service_not_allowed_explain))
                                )
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)

                notificationManagerCompat.notify(NOTIFICATION_ID, builder.build())
            } else {
                Toast.makeText(this@VoiceMessagePlayerService, R.string.notifications_disabled_title, LENGTH_LONG).show()
            }
        }
    }

    private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT < 26 || notificationManagerCompat.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.vm_fg_service_not_allowed),
                        NotificationManager.IMPORTANCE_DEFAULT
                )
        try {
            notificationManagerCompat.createNotificationChannel(channel)
        } catch (e: Exception) {
            logger.error("Unable to create notification channel.", e)
        }
    }

    override fun onSensorChanged(key: String?, value: Boolean) {
        if (keyIsNear == key) {
            if (value) {
                player.setAudioAttributes(getEarpieceAudioAttributes(), false)
            } else {
                player.setAudioAttributes(getRegularAudioAttributes(), false)
            }
        }
    }

    private fun requestAudioFocus() {
        if (!hasAudioFocus) {
            if (
                AudioManagerCompat.requestAudioFocus(
                    audioManager,
                    audioFocusRequestCompat
                )
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                hasAudioFocus = true
                registerReceiver(audioBecomingNoisyReceiver, audioBecomingNoisyFilter)
            }
        }
    }

    private fun releaseAudioFocus() {
        if (hasAudioFocus) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequestCompat)
            hasAudioFocus = false
            try {
                unregisterReceiver(audioBecomingNoisyReceiver)
            } catch (e: IllegalArgumentException) {
                // not registered... ignore exceptions
            }
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.volume = 0.2f
            }
        }
    }

    private fun getRegularAudioAttributes() : AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()
    }

    private fun getEarpieceAudioAttributes() : AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_VOICE_COMMUNICATION)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()
    }

    class SamsungQuirkRenderersFactory(val context: Context) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean, enableOffload: Boolean): AudioSink {
            return SamsungQuirkAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, enableOffload)
        }
    }
}
