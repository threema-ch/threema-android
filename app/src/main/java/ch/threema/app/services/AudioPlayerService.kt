package ch.threema.app.services

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.AudioContentType
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import ch.threema.app.R
import ch.threema.app.voicemessage.SamsungQuirkRenderersFactory
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("AudioPlayerService")

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        logger.info("Creating audio player service")

        // Set our own app icon at the top left of the system player notification
        val mediaNotificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        mediaNotificationProvider.setSmallIcon(R.drawable.ic_notification_small)
        setMediaNotificationProvider(mediaNotificationProvider)

        val player = try {
            ExoPlayer.Builder(applicationContext).apply {
                setRenderersFactory(SamsungQuirkRenderersFactory(applicationContext))
                setAudioAttributes(
                    /* audioAttributes = */
                    createAudioAttributes(contentType = C.AUDIO_CONTENT_TYPE_MUSIC),
                    /* handleAudioFocus = */
                    HANDLE_AUDIO_FOCUS,
                )
                setWakeMode(C.WAKE_MODE_LOCAL)
                setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            /* minBufferMs = */
                            Integer.MAX_VALUE,
                            /* maxBufferMs = */
                            Integer.MAX_VALUE,
                            /* bufferForPlaybackMs = */
                            Integer.MAX_VALUE,
                            /* bufferForPlaybackAfterRebufferMs = */
                            Integer.MAX_VALUE,
                        )
                        .build(),
                )
                setHandleAudioBecomingNoisy(true)
            }.build()
        } catch (e: Exception) {
            logger.error("Failed to create audio player - stopping self", e)
            stopSelf()
            return
        }
        logger.info("Created audio player")
        mediaSession = try {
            MediaSession.Builder(applicationContext, player)
                .setId(AudioPlayerService::class.java.simpleName)
                .setCallback(mediaSessionCallback)
                .build()
        } catch (e: Exception) {
            logger.error("Failed to create media session - stopping self", e)
            try {
                player.release()
            } catch (e: Exception) {
                logger.error("Failed to release player", e)
            }
            stopSelf()
            return
        }
        logger.info("Created media session")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Only allow media controllers from our own app to connect to this media session
        if (!controllerInfo.isPackageNameVerified || controllerInfo.packageName != this.packageName || !controllerInfo.isTrusted) {
            logger.warn("Rejected a bind request from package {}", controllerInfo.packageName)
            return null
        }
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            logger.info("Releasing media session and audio player")
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     *  This callback implementation will stop this media session service if no controllers are left.
     *
     *  For this check in [MediaSession.Callback.onDisconnected] we can not rely on the [MediaSession.getConnectedControllers] list, because this
     *  list might contain an extra media controller from the system. This is why we hold our own set of controller uids (the system media controller
     *  will have the same uid as our own).
     */
    private val mediaSessionCallback = object : MediaSession.Callback {

        private val uniqueControllerIds: MutableSet<Int> = mutableSetOf()

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            logger.info("Controller {} connected with hints: {}", controller.uid, controller.connectionHints)
            uniqueControllerIds.add(controller.uid)
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            logger.info("Controller {} disconnected", controller.uid)
            uniqueControllerIds.remove(controller.uid)
            if (uniqueControllerIds.isEmpty()) {
                logger.info("Stopping self because the last controller disconnected")
                pauseAllPlayersAndStopSelf()
            }
        }
    }

    companion object {

        const val HANDLE_AUDIO_FOCUS = true

        @JvmStatic
        fun createAudioAttributes(@AudioContentType contentType: Int) =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(contentType)
                .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
                .build()
    }
}
